package com.morioo.mms

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class ControlsScreen(carContext: CarContext) : Screen(carContext) {

    private var data: ApiClient.BoatData? = null

    init {
        lifecycleScope.launch {
            while (true) {
                data = withContext(Dispatchers.IO) { ApiClient.getStatus() }
                invalidate()
                delay(2000)
            }
        }
    }

    override fun onGetTemplate(): Template {
        val d = data
        val items = ItemList.Builder()

        // --- 💧 Pompe de cale ---
        val pompeTitle = if (d?.pompeDeCale == true && d.pompeTimer > 0)
            "💧 Pompe de cale (${d.pompeTimer}s)"
        else
            "💧 Pompe de cale"

        items.addItem(Row.Builder()
            .setTitle(pompeTitle)
            .addText(if (d?.pompeDeCale == true) "En marche — arrêt auto" else "Arrêtée")
            .setToggle(Toggle.Builder { _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    ApiClient.post("/api/switch/pompe_de_cale")
                }
            }.setChecked(d?.pompeDeCale ?: false).build())
            .build())

        // --- 🌊 Feux sous-marins ---
        items.addItem(Row.Builder()
            .setTitle("🌊 Feux sous-marins")
            .addText(if (d?.lumieresSousMarine == true) "Allumés" else "Éteints")
            .setToggle(Toggle.Builder { _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    ApiClient.post("/api/switch/lumieres_sous_marines")
                }
            }.setChecked(d?.lumieresSousMarine ?: false).build())
            .build())

        // --- ⚓ Alarme de mouillage ---
        val anchorText = when {
            d?.anchorAlarm == true  -> "⚠️ DÉRIVE — ${d.anchorDistM.toInt()} m du point d'ancre !"
            d?.anchorActive == true -> "Armée — dérive actuelle : ${d.anchorDistM.toInt()} m"
            else                    -> "Inactive"
        }
        items.addItem(Row.Builder()
            .setTitle("⚓ Alarme de mouillage")
            .addText(anchorText)
            .setToggle(Toggle.Builder { isChecked ->
                lifecycleScope.launch(Dispatchers.IO) {
                    if (isChecked) ApiClient.post("/api/anchor/set?radius=50")
                    else           ApiClient.post("/api/anchor/clear")
                }
            }.setChecked(d?.anchorActive ?: false).build())
            .build())

        return ListTemplate.Builder()
            .setTitle("Contrôles")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }
}
