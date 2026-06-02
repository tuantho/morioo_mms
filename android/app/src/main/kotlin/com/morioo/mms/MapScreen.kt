package com.morioo.mms

import android.graphics.*
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MapScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var surface: SurfaceContainer? = null

    private var boatLat = 50.4901
    private var boatLon = 5.1002
    private var gpsFix  = false
    private var vitesse = 0.0
    private var trail:  List<List<Double>> = emptyList()

    // Peints réutilisables (évite les allocs dans le render loop)
    private val paintBg    = Paint().apply { color = Color.parseColor("#0d0802") }
    private val paintGrid  = Paint().apply { color = Color.parseColor("#1a1205"); strokeWidth = 1f }
    private val paintTrail = Paint().apply {
        color = Color.argb(200, 255, 69, 0); strokeWidth = 4f
        style = Paint.Style.STROKE; isAntiAlias = true; strokeJoin = Paint.Join.ROUND
    }
    private val paintBoat  = Paint().apply { color = Color.parseColor("#ff4500"); isAntiAlias = true }
    private val paintBorder = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true
    }
    private val paintInfo  = Paint().apply {
        color = Color.parseColor("#ffeedd"); textSize = 36f; isAntiAlias = true
        typeface = Typeface.MONOSPACE; isFakeBoldText = true
    }
    private val paintInfoBg = Paint().apply { color = Color.argb(180, 13, 8, 2) }
    private val paintNoTile = Paint().apply { color = Color.parseColor("#160e05") }
    private val paintNoTileBorder = Paint().apply {
        color = Color.parseColor("#3d2510"); style = Paint.Style.STROKE; strokeWidth = 1f
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)

        lifecycleScope.launch {
            while (true) {
                // Fetch données bateau + trace en parallèle
                val (d, t) = withContext(Dispatchers.IO) {
                    Pair(ApiClient.getStatus(), ApiClient.getTrail())
                }
                if (d != null) {
                    boatLat = d.lat
                    boatLon = d.lon
                    gpsFix  = d.gpsFix
                    vitesse = d.vitesseKmh
                }
                trail = t
                // Pré-charge les tuiles autour de la position courante
                withContext(Dispatchers.IO) { prefetchTiles() }
                render()
                delay(2000)
            }
        }
    }

    // -------------------------------------------------------------------------
    // SurfaceCallback
    // -------------------------------------------------------------------------

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container
        render()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) { render() }
    override fun onStableAreaChanged(stableArea: Rect) {}

    // -------------------------------------------------------------------------
    // Template Android Auto
    // -------------------------------------------------------------------------

    override fun onGetTemplate(): Template {
        val gpsLabel = if (gpsFix) "🛰 FIX" else "🛰 NO FIX"
        val vitLabel = "%.1f km/h".format(vitesse)

        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder()
                        .setTitle(gpsLabel)
                        .setOnClickListener { /* info */ }
                        .build())
                    .addAction(Action.Builder()
                        .setTitle(vitLabel)
                        .setOnClickListener { /* info */ }
                        .build())
                    .addAction(Action.Builder()
                        .setTitle("◀ Retour")
                        .setOnClickListener { screenManager.pop() }
                        .build())
                    .build()
            )
            .build()
    }

    // -------------------------------------------------------------------------
    // Rendu de la carte sur le Surface
    // -------------------------------------------------------------------------

    private fun render() {
        val sc = surface ?: return
        val sf = sc.surface ?: return
        if (!sf.isValid) return

        val canvas = sf.lockCanvas(null) ?: return
        try {
            drawMap(canvas, sc.width, sc.height)
        } finally {
            sf.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawMap(canvas: Canvas, w: Int, h: Int) {
        val zoom = ZOOM
        val (centerTX, centerTY) = TileCache.latLonToTile(boatLat, boatLon, zoom)
        val tileX = centerTX.toInt()
        val tileY = centerTY.toInt()

        // Décalage en pixels du bateau dans la tuile centrale
        val offX = (centerTX - tileX) * TileCache.TILE_SIZE
        val offY = (centerTY - tileY) * TileCache.TILE_SIZE

        val cx = w / 2f
        val cy = h / 2f

        // Fond si pas de tuile
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paintBg)

        // Nombre de tuiles à afficher dans chaque direction
        val rx = w / TileCache.TILE_SIZE / 2 + 2
        val ry = h / TileCache.TILE_SIZE / 2 + 2

        // Tuiles
        for (dx in -rx..rx) {
            for (dy in -ry..ry) {
                val sx = (cx - offX + dx * TileCache.TILE_SIZE).toInt()
                val sy = (cy - offY + dy * TileCache.TILE_SIZE).toInt()
                val bmp = TileCache.get(zoom, tileX + dx, tileY + dy)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, sx.toFloat(), sy.toFloat(), null)
                } else {
                    val r = RectF(sx.toFloat(), sy.toFloat(),
                        (sx + TileCache.TILE_SIZE).toFloat(),
                        (sy + TileCache.TILE_SIZE).toFloat())
                    canvas.drawRect(r, paintNoTile)
                    canvas.drawRect(r, paintNoTileBorder)
                }
            }
        }

        // Trace GPS
        if (trail.size > 1) {
            val path = Path()
            trail.forEachIndexed { i, pt ->
                val (px, py) = latLonToScreen(pt[0], pt[1], centerTX, centerTY, cx, cy)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, paintTrail)
        }

        // Marqueur bateau (cercle rouge + bordure blanche)
        canvas.drawCircle(cx, cy, 14f, paintBoat)
        canvas.drawCircle(cx, cy, 14f, paintBorder)

        // Bandeau info en bas (vitesse + GPS)
        val infoH = 70f
        canvas.drawRect(0f, h - infoH, w.toFloat(), h.toFloat(), paintInfoBg)
        val gps = if (gpsFix) "GPS FIX  " else "GPS NO FIX  "
        canvas.drawText(
            "$gps|  %.1f km/h".format(vitesse),
            20f, h - infoH / 2f + paintInfo.textSize / 3f, paintInfo
        )
    }

    /** Convertit lat/lon en pixels sur l'écran, par rapport à la tuile centrale. */
    private fun latLonToScreen(
        lat: Double, lon: Double,
        centerTX: Double, centerTY: Double,
        cx: Float, cy: Float
    ): Pair<Float, Float> {
        val (tx, ty) = TileCache.latLonToTile(lat, lon, ZOOM)
        return Pair(
            (cx + (tx - centerTX) * TileCache.TILE_SIZE).toFloat(),
            (cy + (ty - centerTY) * TileCache.TILE_SIZE).toFloat()
        )
    }

    /** Pré-charge les tuiles visibles en arrière-plan. */
    private fun prefetchTiles() {
        val sc = surface ?: return
        val zoom = ZOOM
        val (ctX, ctY) = TileCache.latLonToTile(boatLat, boatLon, zoom)
        val tileX = ctX.toInt()
        val tileY = ctY.toInt()
        val rx = sc.width  / TileCache.TILE_SIZE / 2 + 2
        val ry = sc.height / TileCache.TILE_SIZE / 2 + 2
        for (dx in -rx..rx) for (dy in -ry..ry)
            TileCache.get(zoom, tileX + dx, tileY + dy)   // bloquant, dans IO
    }

    companion object {
        private const val ZOOM = 16   // détail suffisant pour la Meuse
    }
}
