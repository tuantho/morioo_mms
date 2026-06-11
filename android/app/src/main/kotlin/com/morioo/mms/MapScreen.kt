package com.morioo.mms

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MapScreen(carContext: CarContext) : Screen(carContext) {

    private var boatLat    = 50.4901
    private var boatLon    = 5.1002
    private var gpsFix     = false
    private var vitesse    = 0.0
    private var profondeur = 0.0
    private var batterie   = 0.0
    private var tripKm     = 0.0
    private var pompeDeCale        = false
    private var lumieresSousMarine = false
    private var pompeTimer = 0

    init {
        lifecycleScope.launch {
            while (true) {
                val d = withContext(Dispatchers.IO) { ApiClient.getStatus() }
                if (d != null) {
                    boatLat    = d.lat
                    boatLon    = d.lon
                    gpsFix     = d.gpsFix
                    vitesse    = d.vitesseKmh
                    profondeur = d.profondeur
                    batterie   = d.batterie
                    tripKm     = d.tripKm
                    pompeDeCale        = d.pompeDeCale
                    lumieresSousMarine = d.lumieresSousMarine
                    pompeTimer = d.pompeTimer
                }
                invalidate()
                delay(1000)
            }
        }
    }

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Exception) {
        MessageTemplate.Builder("Erreur carte : ${e.message}")
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun buildTemplate(): Template {
        val gpsStr  = if (gpsFix) "🛰 GPS FIX" else "🛰 NO FIX"
        val batIcon = when {
            batterie >= 12.6 -> "🔋"
            batterie >= 12.0 -> "🪫"
            else -> "⚠️"
        }

        // Marqueur bateau sur la carte système
        val boatPlace = Place.Builder(LatLng.create(boatLat, boatLon))
            .setMarker(PlaceMarker.Builder().setColor(CarColor.RED).build())
            .build()

        val pompeLabel = if (pompeDeCale) "💧 POMPE : ON (${pompeTimer}s)" else "💧 Pompe de cale : OFF"
        val feuxLabel  = if (lumieresSousMarine) "🌊 Feux : ON" else "🌊 Feux sous-marins : OFF"

        val items = ItemList.Builder()
            // Jauges principales
            .addItem(Row.Builder()
                .setTitle("⚡ %.1f km/h  •  🌊 %.1f m".format(vitesse, profondeur))
                .addText("$batIcon %.1f V  •  $gpsStr  •  %.1f km".format(batterie, tripKm))
                .build())
            // Pompe de cale
            .addItem(Row.Builder()
                .setTitle(pompeLabel)
                .setOnClickListener { triggerPompe() }
                .build())
            // Feux sous-marins
            .addItem(Row.Builder()
                .setTitle(feuxLabel)
                .setOnClickListener { triggerFeux() }
                .build())
            // Navigation vers jauges détaillées
            .addItem(Row.Builder()
                .setTitle("📋 Jauges détaillées")
                .setOnClickListener { screenManager.push(DashboardScreen(carContext)) }
                .build())
            .build()

        return PlaceListMapTemplate.Builder()
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .setAnchor(boatPlace)
            .setCurrentLocationEnabled(false)
            .setItemList(items)
            .build()
    }

    private fun triggerPompe() {
        lifecycleScope.launch(Dispatchers.IO) { ApiClient.post("/api/switch/pompe_de_cale") }
    }

    private fun triggerFeux() {
        lifecycleScope.launch(Dispatchers.IO) { ApiClient.post("/api/switch/lumieres_sous_marines") }
    }
}
