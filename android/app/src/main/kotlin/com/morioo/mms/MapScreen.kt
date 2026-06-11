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

    private var boatLat  = 50.4901
    private var boatLon  = 5.1002
    private var gpsFix   = false
    private var vitesse  = 0.0
    private var profondeur = 0.0
    private var batterie = 0.0
    private var trail:   List<List<Double>> = emptyList()
    private var zoom     = ZOOM_DEFAULT

    private val paintBg      = Paint().apply { color = Color.parseColor("#0d0802") }
    private val paintTrail   = Paint().apply {
        color = Color.argb(200, 255, 69, 0); strokeWidth = 4f
        style = Paint.Style.STROKE; isAntiAlias = true; strokeJoin = Paint.Join.ROUND
    }
    private val paintBoat    = Paint().apply { color = Color.parseColor("#ff4500"); isAntiAlias = true }
    private val paintBorder  = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true
    }
    private val paintInfo    = Paint().apply {
        color = Color.parseColor("#ffeedd"); textSize = 36f; isAntiAlias = true
        typeface = Typeface.MONOSPACE; isFakeBoldText = true
    }
    private val paintInfoBg  = Paint().apply { color = Color.argb(180, 13, 8, 2) }
    private val paintNoTile  = Paint().apply { color = Color.parseColor("#160e05") }
    private val paintNoTileBorder = Paint().apply {
        color = Color.parseColor("#3d2510"); style = Paint.Style.STROKE; strokeWidth = 1f
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)

        lifecycleScope.launch {
            while (true) {
                val (d, t) = withContext(Dispatchers.IO) {
                    Pair(ApiClient.getStatus(), ApiClient.getTrail())
                }
                if (d != null) {
                    boatLat    = d.lat
                    boatLon    = d.lon
                    gpsFix     = d.gpsFix
                    vitesse    = d.vitesseKmh
                    profondeur = d.profondeur
                    batterie   = d.batterie
                }
                trail = t
                withContext(Dispatchers.IO) { prefetchTiles() }
                render()
                invalidate()
                delay(1000)
            }
        }
    }

    // ── SurfaceCallback ──────────────────────────────────────────────────────

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container
        render()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) { surface = null }
    override fun onVisibleAreaChanged(visibleArea: Rect) { render() }
    override fun onStableAreaChanged(stableArea: Rect) {}

    // ── Template ─────────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        val gpsLabel = if (gpsFix) "🛰 FIX" else "🛰 NO FIX"
        val vitLabel = "⚡ %.1f km/h".format(vitesse)

        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder()
                        .setTitle(gpsLabel)
                        .setOnClickListener {}
                        .build())
                    .addAction(Action.Builder()
                        .setTitle(vitLabel)
                        .setOnClickListener {}
                        .build())
                    .addAction(Action.Builder()
                        .setTitle("📋 Jauges")
                        .setOnClickListener { screenManager.push(DashboardScreen(carContext)) }
                        .build())
                    .addAction(Action.Builder()
                        .setTitle("🎛 Contrôles")
                        .setOnClickListener { screenManager.push(ControlsScreen(carContext)) }
                        .build())
                    .build()
            )
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder()
                        .setTitle("+")
                        .setOnClickListener {
                            if (zoom < ZOOM_MAX) { zoom++; render() }
                        }
                        .build())
                    .addAction(Action.Builder()
                        .setTitle("−")
                        .setOnClickListener {
                            if (zoom > ZOOM_MIN) { zoom--; render() }
                        }
                        .build())
                    .build()
            )
            .build()
    }

    // ── Rendu carte ──────────────────────────────────────────────────────────

    private fun render() {
        val sc = surface ?: return
        val sf = sc.surface ?: return
        if (!sf.isValid) return
        val canvas = sf.lockCanvas(null) ?: return
        try { drawMap(canvas, sc.width, sc.height) }
        finally { sf.unlockCanvasAndPost(canvas) }
    }

    private fun drawMap(canvas: Canvas, w: Int, h: Int) {
        val (centerTX, centerTY) = TileCache.latLonToTile(boatLat, boatLon, zoom)
        val tileX = centerTX.toInt()
        val tileY = centerTY.toInt()
        val offX  = (centerTX - tileX) * TileCache.TILE_SIZE
        val offY  = (centerTY - tileY) * TileCache.TILE_SIZE
        val cx = w / 2f
        val cy = h / 2f

        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paintBg)

        val rx = w / TileCache.TILE_SIZE / 2 + 2
        val ry = h / TileCache.TILE_SIZE / 2 + 2
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

        // Marqueur bateau
        canvas.drawCircle(cx, cy, 14f, paintBoat)
        canvas.drawCircle(cx, cy, 14f, paintBorder)

        // Bandeau info (vitesse + profondeur + batterie + GPS)
        val infoH = 70f
        canvas.drawRect(0f, h - infoH, w.toFloat(), h.toFloat(), paintInfoBg)
        val gps = if (gpsFix) "FIX" else "NO FIX"
        val infoText = "⚡ %.1f km/h  |  🌊 %.1f m  |  🔋 %.1fV  |  🛰 %s  |  Z%d"
            .format(vitesse, profondeur, batterie, gps, zoom)
        canvas.drawText(infoText, 20f, h - infoH / 2f + paintInfo.textSize / 3f, paintInfo)
    }

    private fun latLonToScreen(
        lat: Double, lon: Double,
        centerTX: Double, centerTY: Double,
        cx: Float, cy: Float
    ): Pair<Float, Float> {
        val (tx, ty) = TileCache.latLonToTile(lat, lon, zoom)
        return Pair(
            (cx + (tx - centerTX) * TileCache.TILE_SIZE).toFloat(),
            (cy + (ty - centerTY) * TileCache.TILE_SIZE).toFloat()
        )
    }

    private fun prefetchTiles() {
        val sc = surface ?: return
        val (ctX, ctY) = TileCache.latLonToTile(boatLat, boatLon, zoom)
        val tileX = ctX.toInt(); val tileY = ctY.toInt()
        val rx = sc.width  / TileCache.TILE_SIZE / 2 + 2
        val ry = sc.height / TileCache.TILE_SIZE / 2 + 2
        for (dx in -rx..rx) for (dy in -ry..ry)
            TileCache.get(zoom, tileX + dx, tileY + dy)
    }

    companion object {
        private const val ZOOM_DEFAULT = 16
        private const val ZOOM_MIN     = 12
        private const val ZOOM_MAX     = 19
    }
}
