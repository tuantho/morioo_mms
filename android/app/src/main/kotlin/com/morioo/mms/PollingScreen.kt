package com.morioo.mms

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*

/**
 * Base des écrans Android Auto : poll /api/status tant que l'écran est
 * visible (STARTED) et invalide le template à chaque tick.
 *
 * repeatOnLifecycle : quand un autre écran est poussé par-dessus, le polling
 * s'arrête (sinon chaque écran de la pile continuerait à taper le Pi et à
 * appeler invalidate() en arrière-plan) ; il reprend au retour.
 */
abstract class PollingScreen(
    carContext: CarContext,
    private val intervalMs: Long,
) : Screen(carContext) {

    /** Dernier état connu du bateau (conservé si le Pi devient injoignable). */
    protected var data: ApiClient.BoatData? = null
    protected var connectionError = false

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val d = withContext(Dispatchers.IO) { ApiClient.getStatus() }
                    connectionError = (d == null)
                    if (d != null) data = d
                    invalidate()
                    delay(intervalMs)
                }
            }
        }
    }
}
