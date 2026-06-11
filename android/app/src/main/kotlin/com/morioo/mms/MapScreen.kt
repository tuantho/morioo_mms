package com.morioo.mms

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

/**
 * Écran principal Android Auto : carte de l'host (PlaceListMapTemplate)
 * ancrée sur la position GPS du bateau — fournie par le Pi, donc aucune
 * permission de localisation téléphone requise (setCurrentLocationEnabled
 * reste à false).
 *
 * ⚠️ Quota de templates : un update n'est un « refresh » gratuit que si les
 * TITRES des rows ne changent pas (~5 templates par tâche, ensuite l'host
 * gèle l'UI). Les valeurs dynamiques vont dans addText(), jamais en titre.
 */
class MapScreen(carContext: CarContext) : PollingScreen(carContext, 1_000) {

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Exception) {
        MessageTemplate.Builder("Erreur : ${e.message}")
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun buildTemplate(): Template {
        // Pas encore de données : état loading (le passage loading → contenu
        // est toujours considéré comme un refresh par l'host).
        val d = data ?: return PlaceListMapTemplate.Builder()
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(false)
            .setLoading(true)
            .build()

        val gpsStr  = if (d.gpsFix) "🛰 GPS FIX" else "🛰 NO FIX"
        val batIcon = when {
            d.batterie >= 12.6 -> "🔋"
            d.batterie >= 12.0 -> "🪫"
            else -> "⚠️"
        }

        val items = ItemList.Builder()
            .addItem(Row.Builder()
                .setTitle("⚡ Jauges")
                .addText("%.1f km/h  •  🌊 %.1f m  •  📍 %.1f km"
                    .format(d.vitesseKmh, d.profondeur, d.tripKm))
                .addText("$batIcon %.1f V  •  $gpsStr".format(d.batterie))
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(DashboardScreen(carContext)) }
                .build())
            .addItem(Row.Builder()
                .setTitle("💧 Pompe de cale")
                .addText(if (d.pompeDeCale) "ON — arrêt auto dans ${d.pompeTimer} s" else "OFF")
                .setBrowsable(true)
                .setOnClickListener { triggerPompe() }
                .build())
            .addItem(Row.Builder()
                .setTitle("🌊 Feux sous-marins")
                .addText(if (d.lumieresSousMarine) "ON" else "OFF")
                .setBrowsable(true)
                .setOnClickListener { triggerFeux() }
                .build())
            .addItem(Row.Builder()
                .setTitle("🎛 Contrôles avancés")
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(ControlsScreen(carContext)) }
                .build())
            .build()

        return PlaceListMapTemplate.Builder()
            .setTitle("Boesch 510")
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(false)
            // CarLocation, PAS LatLng (LatLng = Maps SDK, absent d'ici)
            .setAnchor(Place.Builder(CarLocation.create(d.lat, d.lon))
                .setMarker(PlaceMarker.Builder().build())
                .build())
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
