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

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Exception) {
        MessageTemplate.Builder("Erreur : ${e.javaClass.simpleName}\n${e.message}")
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun buildTemplate(): Template {

        if (connectionError && data == null) {
            return MessageTemplate.Builder(
                "Impossible de joindre le Pi.\n\nVérifie le réseau bateau."
            )
                .setTitle("Boesch 510")
                .setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder()
                    .setTitle("Réessayer")
                    .setOnClickListener { invalidate() }
                    .build())
                .build()
        }

        val d = data ?: return MessageTemplate.Builder("Connexion au Pi…")
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .build()

        // ── Lignes d'info ──────────────────────────────────────────────
        val rows = ItemList.Builder()

        // Vitesse + Profondeur
        rows.addItem(Row.Builder()
            .setTitle("⚡ %.1f km/h".format(d.vitesseKmh))
            .addText("🌊 Profondeur : %.1f m".format(d.profondeur))
            .build())

        // Batterie + GPS
        val batIcon = when {
            d.batterie >= 12.6 -> "🔋"
            d.batterie >= 12.0 -> "🪫"
            else               -> "⚠️"
        }
        val gpsText = if (d.gpsFix) "🛰 GPS FIX ✅" else "🛰 GPS : recherche…"
        rows.addItem(Row.Builder()
            .setTitle("$batIcon Batterie : %.1f V".format(d.batterie))
            .addText(gpsText)
            .build())

        // ODO
        rows.addItem(Row.Builder()
            .setTitle("📍 Trip : %.2f km  |  %.2f nm".format(d.tripKm, d.tripKm / 1.852))
            .build())


        // Météo si dispo
        if (d.weatherIcon.isNotEmpty()) {
            rows.addItem(Row.Builder()
                .setTitle("🌤 Météo : ${d.weatherIcon} ${d.weatherTemp ?: "--"}°C")
                .build())
        }

        // ── Boutons de contrôle ────────────────────────────────────────
        val pompeLabel = if (d.pompeDeCale) "💧 POMPE ON (${d.pompeTimer}s)" else "💧 POMPE CALE"
        val feuxLabel  = if (d.lumieresSousMarine) "🌊 FEUX ON" else "🌊 FEUX OFF"

        val pane = Pane.Builder()
            .setItemList(rows.build())
            .addAction(Action.Builder()
                .setTitle(pompeLabel)
                .setBackgroundColor(
                    if (d.pompeDeCale) CarColor.GREEN else CarColor.DEFAULT
                )
                .setOnClickListener { triggerPompe() }
                .build())
            .addAction(Action.Builder()
                .setTitle(feuxLabel)
                .setBackgroundColor(
                    if (d.lumieresSousMarine) CarColor.BLUE else CarColor.DEFAULT
                )
                .setOnClickListener { triggerFeux() }
                .build())
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
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
