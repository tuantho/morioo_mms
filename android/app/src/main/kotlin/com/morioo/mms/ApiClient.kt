package com.morioo.mms

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    // Hostname du Raspberry Pi sur le réseau bateau
    private const val BASE = "http://rasp-boesch:8000"

    data class BoatData(
        val vitesseKmh:          Double,
        val profondeur:          Double,
        val batterie:            Double,
        val gpsFix:              Boolean,
        val pompeDeCale:         Boolean,
        val pompeTimer:          Int,
        val lumieresSousMarine:  Boolean,
        val musicTitle:          String,
        val musicArtist:         String,
        val anchorActive:        Boolean,
        val anchorAlarm:         Boolean,
        val anchorDistM:         Double,
        val tripKm:              Double,
        val weatherIcon:         String,
        val weatherTemp:         Int?,
    )

    /** Récupère l'état complet du bateau. Null si le Pi est injoignable. */
    fun getStatus(): BoatData? {
        val j = get("/api/status") ?: return null
        val trip   = j.optJSONObject("trip")
        val anchor = j.optJSONObject("anchor")

        // Météo depuis /api/weather (appel séparé, on absorbe les erreurs)
        val w = try { get("/api/weather") } catch (e: Exception) { null }

        return BoatData(
            vitesseKmh         = j.optDouble("vitesse", 0.0) * 1.852,
            profondeur         = j.optDouble("profondeur", 0.0),
            batterie           = j.optDouble("batterie", 0.0),
            gpsFix             = j.optBoolean("gps_fix", false),
            pompeDeCale        = j.optBoolean("pompe_de_cale", false),
            pompeTimer         = j.optInt("pompe_timer", 0),
            lumieresSousMarine = j.optBoolean("lumieres_sous_marines", false),
            musicTitle         = j.optString("music_title", ""),
            musicArtist        = j.optString("music_artist", ""),
            anchorActive       = anchor?.optBoolean("active", false) ?: false,
            anchorAlarm        = anchor?.optBoolean("alarm", false) ?: false,
            anchorDistM        = anchor?.optDouble("distance_m", 0.0) ?: 0.0,
            tripKm             = trip?.optDouble("km", 0.0) ?: 0.0,
            weatherIcon        = w?.optString("icon", "") ?: "",
            weatherTemp        = w?.let { if (it.optBoolean("ok")) it.optInt("temp") else null },
        )
    }

    /** POST simple (toggle relais, anchor set/clear). */
    fun post(path: String): Boolean = try {
        val conn = (URL("$BASE$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = 3000
            doInput = true
        }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    } catch (e: Exception) { false }

    private fun get(path: String): JSONObject? = try {
        val conn = (URL("$BASE$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
        }
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        JSONObject(text)
    } catch (e: Exception) { null }
}
