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
        // Poll le Pi toutes les 2 secondes tant que l'écran est visible
        lifecycleScope.launch {
            while (true) {
                val d = withContext(Dispatchers.IO) { ApiClient.getStatus() }
                connectionError = (d == null)
                data = d
                invalidate()   // re-déclenche onGetTemplate()
                delay(2000)
            }
        }
    }

    override fun onGetTemplate(): Template {

        // --- Écran d'erreur si Pi injoignable ---
        if (connectionError && data == null) {
            return MessageTemplate.Builder(
                "Impossible de joindre le Pi.\n\nVérifie que tu es connecté au réseau bateau (Wi-Fi)."
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
            // Premier chargement
            items.addItem(Row.Builder().setTitle("Connexion au Pi…").build())
        } else {
            // ⚡ Vitesse
            items.addItem(Row.Builder()
                .setTitle("⚡ Vitesse")
                .addText("%.1f km/h".format(d.vitesseKmh))
                .build())

            // 🌊 Profondeur
            items.addItem(Row.Builder()
                .setTitle("🌊 Profondeur")
                .addText("%.1f m".format(d.profondeur))
                .build())

            // 🔋 Batterie
            val batIcon = when {
                d.batterie >= 12.6 -> "🔋"
                d.batterie >= 12.0 -> "🪫"
                else               -> "⚠️"
            }
            items.addItem(Row.Builder()
                .setTitle("$batIcon Batterie")
                .addText("%.1f V".format(d.batterie))
                .build())

            // 🛰 GPS
            items.addItem(Row.Builder()
                .setTitle("🛰 GPS")
                .addText(if (d.gpsFix) "✅ Fix acquis" else "⏳ Recherche satellites…")
                .build())

            // 📍 Trip
            items.addItem(Row.Builder()
                .setTitle("📍 Trip ODO")
                .addText("%.2f km".format(d.tripKm))
                .build())

            // 🎵 Musique
            val music = when {
                d.musicArtist.isNotEmpty() -> "${d.musicTitle} — ${d.musicArtist}"
                d.musicTitle.isNotEmpty()  -> d.musicTitle
                else -> "—"
            }
            items.addItem(Row.Builder()
                .setTitle("🎵 Musique")
                .addText(music)
                .build())
        }

        // Météo + lien contrôles dans l'ActionStrip (barre latérale)
        val actionStrip = ActionStrip.Builder()

        if (d != null && d.weatherIcon.isNotEmpty() && d.weatherTemp != null) {
            actionStrip.addAction(Action.Builder()
                .setTitle("${d.weatherIcon} ${d.weatherTemp}°")
                .setOnClickListener { /* info seulement */ }
                .build())
        }

        actionStrip.addAction(Action.Builder()
            .setTitle("🗺 Carte")
            .setOnClickListener {
                screenManager.push(MapScreen(carContext))
            }
            .build())

        actionStrip.addAction(Action.Builder()
            .setTitle("🎮 Contrôles")
            .setOnClickListener {
                screenManager.push(ControlsScreen(carContext))
            }
            .build())

        return ListTemplate.Builder()
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .setActionStrip(actionStrip.build())
            .build()
    }
}
