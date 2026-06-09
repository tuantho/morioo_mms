package com.morioo.mms

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MusicScreen(carContext: CarContext) : Screen(carContext) {

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

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Exception) {
        MessageTemplate.Builder("Erreur : ${e.message}")
            .setTitle("Musique")
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun buildTemplate(): Template {
        val d = data

        val title  = d?.musicTitle?.takeIf { it.isNotEmpty() } ?: "Aucune lecture en cours"
        val artist = d?.musicArtist?.takeIf { it.isNotEmpty() } ?: ""

        val rowBuilder = Row.Builder().setTitle(title)
        if (artist.isNotEmpty()) rowBuilder.addText(artist)

        val pane = Pane.Builder()
            .addRow(rowBuilder.build())
            .addAction(Action.Builder()
                .setTitle("⏮  Précédent")
                .setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                .build())
            .addAction(Action.Builder()
                .setTitle("⏭  Suivant")
                .setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
                .build())
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Musique")
            .setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder()
                .addAction(Action.Builder()
                    .setTitle("⏯")
                    .setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                    .build())
                .build())
            .build()
    }

    /** Envoie une touche média à la session active (Spotify via Android Auto). */
    private fun sendMediaKey(keyCode: Int) {
        val am = carContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
