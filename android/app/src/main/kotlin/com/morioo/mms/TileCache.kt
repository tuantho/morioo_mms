package com.morioo.mms

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Cache de tuiles de carte (CartoDB Dark — même fond que le dashboard Pi).
 * LRU simple : max 80 tuiles en mémoire (~5 MB).
 * Les fetches sont bloquants — toujours appeler depuis un thread IO.
 */
object TileCache {

    const val TILE_SIZE = 256
    private const val MAX_TILES = 80

    // LRU : LinkedHashMap en mode accessOrder
    private val cache: MutableMap<String, Bitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bitmap>(MAX_TILES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) =
                size > MAX_TILES
        }
    )

    /** Retourne la tuile depuis le cache ou la télécharge. Null si réseau KO. */
    fun get(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        cache[key]?.let { return it }
        return try {
            // CartoDB Dark — même fond que le dashboard Raspberry Pi
            val url = URL("https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/$z/$x/$y.png")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout    = 5000
            conn.setRequestProperty("User-Agent", "MoriooMMS/1.0")
            val bmp = BitmapFactory.decodeStream(conn.inputStream)
            conn.disconnect()
            if (bmp != null) cache[key] = bmp
            bmp
        } catch (e: Exception) { null }
    }

    /** Convertit lat/lon en coordonnées de tuile (partie décimale = position dans la tuile). */
    fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val x = (lon + 180.0) / 360.0 * (1 shl zoom)
        val latRad = Math.toRadians(lat)
        val y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)
        return Pair(x, y)
    }
}
