package com.morioo.mms

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    private var data: ApiClient.BoatData? = null
    private var connectionError = false

    init {
        lifecycleScope.launch {
            while (true) {
                val d = withContext(Dispatchers.IO) { ApiClient.getStatus() }
                connectionError = (d == null)
                data = d
                invalidate()
                delay(2000)
            }
        }
    }

    override fun onGetTemplate(): Template {

        if (connectionError && data == null) {
            return MessageTemplate.Builder(
                "Impossible de joindre le Pi.\n\nVérifie le réseau bateau (Wi-Fi / hotspot)."
            )
                .setTitle("Boesch 510")
                .setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder()
                    .setTitle("Réessayer")
                    .setOnClickListener { invalidate() }
                    .build())
                .build()
        }

        val d = data
        val items = ItemList.Builder()

        if (d == null) {
            items.addItem(Row.Builder().setTitle("Connexion au Pi…").build())
        } else {

            // ⚡ Vitesse + 🌊 Profondeur
            items.addItem(Row.Builder()
                .setTitle("⚡ %.1f km/h".format(d.vitesseKmh))
                .addText("🌊 %.1f m".format(d.profondeur))
                .build())

            // 🔋 Batterie + 🛰 GPS
            val batIcon = when {
                d.batterie >= 12.6 -> "🔋"
                d.batterie >= 12.0 -> "🪫"
                else               -> "⚠️"
            }
            items.addItem(Row.Builder()
                .setTitle("$batIcon %.1f V".format(d.batterie))
                .addText(if (d.gpsFix) "🛰 GPS FIX" else "🛰 NO FIX")
                .build())

            // 📍 Trip ODO
            items.addItem(Row.Builder()
                .setTitle("📍 Trip")
                .addText("%.2f km  |  %.2f nm".format(d.tripKm, d.tripKm / 1.852))
                .build())

            // 💧 Pompe de cale (tap pour activer)
            val pompeLabel = if (d.pompeDeCale)
                "💧 POMPE ON — ${d.pompeTimer}s restantes"
            else
                "💧 Pompe de cale : OFF  →  Appuyer pour démarrer"
            items.addItem(Row.Builder()
                .setTitle(pompeLabel)
                .setOnClickListener { triggerPompe() }
                .build())

            // 🌊 Feux sous-marins (tap pour toggle)
            val feuxLabel = if (d.lumieresSousMarine)
                "🌊 Feux sous-marins : ON  →  Appuyer pour éteindre"
            else
                "🌊 Feux sous-marins : OFF  →  Appuyer pour allumer"
            items.addItem(Row.Builder()
                .setTitle(feuxLabel)
                .setOnClickListener { triggerFeux() }
                .build())

            // 🎵 Spotify
            val music = when {
                d.musicArtist.isNotEmpty() -> "🎵 ${d.musicTitle} — ${d.musicArtist}"
                d.musicTitle.isNotEmpty()  -> "🎵 ${d.musicTitle}"
                else -> "🎵 Spotify déconnecté"
            }
            items.addItem(Row.Builder()
                .setTitle(music)
                .build())
        }

        // ActionStrip droite : météo + contrôles Spotify
        val strip = ActionStrip.Builder()

        if (d != null && d.weatherIcon.isNotEmpty()) {
            strip.addAction(Action.Builder()
                .setTitle("${d.weatherIcon} ${d.weatherTemp ?: "--"}°")
                .setOnClickListener {}
                .build())
        }

        strip.addAction(Action.Builder()
            .setTitle("⏮")
            .setOnClickListener { spotify("previous") }
            .build())
        strip.addAction(Action.Builder()
            .setTitle("⏯")
            .setOnClickListener { spotify("play") }
            .build())
        strip.addAction(Action.Builder()
            .setTitle("⏭")
            .setOnClickListener { spotify("next") }
            .build())

        return ListTemplate.Builder()
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .setActionStrip(strip.build())
            .build()
    }

    private fun triggerPompe() {
        lifecycleScope.launch(Dispatchers.IO) { ApiClient.post("/api/switch/pompe_de_cale") }
    }

    private fun triggerFeux() {
        lifecycleScope.launch(Dispatchers.IO) { ApiClient.post("/api/switch/lumieres_sous_marines") }
    }

    private fun spotify(action: String) {
        lifecycleScope.launch(Dispatchers.IO) { ApiClient.post("/api/spotify/$action") }
    }
}
