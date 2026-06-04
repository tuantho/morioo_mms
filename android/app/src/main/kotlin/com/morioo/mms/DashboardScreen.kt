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

        val batIcon = when {
            d.batterie >= 12.6 -> "🔋"
            d.batterie >= 12.0 -> "🪫"
            else -> "⚠️"
        }

        val pompeLabel = if (d.pompeDeCale) "💧 POMPE ON (${d.pompeTimer}s)" else "💧 POMPE CALE"
        val feuxLabel  = if (d.lumieresSousMarine) "🌊 FEUX ON" else "🌊 FEUX OFF"

        val paneBuilder = Pane.Builder()
            .addRow(Row.Builder()
                .setTitle("⚡ %.1f km/h  •  🌊 %.1f m".format(d.vitesseKmh, d.profondeur))
                .addText("$batIcon %.1f V  •  ${if (d.gpsFix) "🛰 GPS OK" else "🛰 No fix"}".format(d.batterie))
                .build())
            .addRow(Row.Builder()
                .setTitle("📍 %.2f km  |  %.2f nm".format(d.tripKm, d.tripKm / 1.852))
                .build())
            .addAction(Action.Builder()
                .setTitle(pompeLabel)
                .setBackgroundColor(if (d.pompeDeCale) CarColor.GREEN else CarColor.DEFAULT)
                .setOnClickListener { triggerPompe() }
                .build())
            .addAction(Action.Builder()
                .setTitle(feuxLabel)
                .setBackgroundColor(if (d.lumieresSousMarine) CarColor.BLUE else CarColor.DEFAULT)
                .setOnClickListener { triggerFeux() }
                .build())

        if (d.weatherIcon.isNotEmpty()) {
            paneBuilder.addRow(Row.Builder()
                .setTitle("🌤 ${d.weatherIcon} ${d.weatherTemp ?: "--"}°C")
                .build())
        }

        return PaneTemplate.Builder(paneBuilder.build())
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
}
