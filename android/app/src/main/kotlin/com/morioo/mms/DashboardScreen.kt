package com.morioo.mms

import androidx.car.app.CarContext
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class DashboardScreen(carContext: CarContext) : PollingScreen(carContext, 2_000) {

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

        val pompeLabel = if (d.pompeDeCale) "💧 POMPE ON" else "💧 POMPE CALE"
        val feuxLabel  = if (d.lumieresSousMarine) "🌊 FEUX ON" else "🌊 FEUX OFF"

        // ⚠️ Quota de templates : titres de rows constants, valeurs dans addText
        // (un titre qui change n'est pas un « refresh » → l'host gèle après ~5).

        // Row 1 : jauges
        val gpsStr     = if (d.gpsFix) "🛰 GPS OK" else "🛰 No fix"
        val weatherStr = if (d.weatherIcon.isNotEmpty()) "  •  ${d.weatherIcon} ${d.weatherTemp ?: "--"}°C" else ""
        val row1 = Row.Builder()
            .setTitle("⚡ Jauges")
            .addText("%.1f km/h  •  🌊 %.1f m  •  📍 %.1f km".format(d.vitesseKmh, d.profondeur, d.tripKm))
            .addText("$batIcon %.1f V  •  $gpsStr$weatherStr".format(d.batterie))
            .build()

        // Row 2 : musique (cliquable → MusicScreen)
        val row2Builder = Row.Builder()
            .setTitle("🎵 Musique")
            .addText(d.musicTitle.takeIf { it.isNotEmpty() } ?: "Aucune lecture")
            .setOnClickListener { screenManager.push(MusicScreen(carContext)) }
        if (d.musicArtist.isNotEmpty()) row2Builder.addText(d.musicArtist)

        val paneBuilder = Pane.Builder()
            .addRow(row1)
            .addRow(row2Builder.build())
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

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Boesch 510 — Jauges")
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun triggerPompe() {
        lifecycleScope.launch(Dispatchers.IO) { ApiClient.post("/api/switch/pompe_de_cale") }
    }

    private fun triggerFeux() {
        lifecycleScope.launch(Dispatchers.IO) { ApiClient.post("/api/switch/lumieres_sous_marines") }
    }
}
