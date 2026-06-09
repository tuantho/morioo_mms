package com.morioo.mms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Service de pont média local.
 *
 * Écoute sur 127.0.0.1:8765 et dispatch des touches média (play/pause/next/prev)
 * via AudioManager. Utilisé par le dashboard web (AABrowser) pour contrôler Spotify
 * quand Android Auto a la session exclusive — l'API Spotify externe est bloquée dans
 * ce cas, mais les KeyEvent média passent toujours.
 *
 * Interface HTTP minimale (GET) :
 *   /play_pause  →  KEYCODE_MEDIA_PLAY_PAUSE
 *   /play        →  KEYCODE_MEDIA_PLAY_PAUSE
 *   /pause       →  KEYCODE_MEDIA_PLAY_PAUSE
 *   /next        →  KEYCODE_MEDIA_NEXT
 *   /prev        →  KEYCODE_MEDIA_PREVIOUS
 *   /previous    →  KEYCODE_MEDIA_PREVIOUS
 */
class MediaBridgeService : Service() {

    companion object {
        const val PORT = 8765
        private const val NOTIF_ID   = 101
        private const val CHANNEL_ID = "media_bridge"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() { scope.cancel() }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Serveur TCP ───────────────────────────────────────────────────────────

    private fun startServer() {
        scope.launch {
            while (isActive) {
                try {
                    val ss = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 5)
                    }
                    ss.use {
                        while (isActive) {
                            val client = ss.accept()
                            launch { handleClient(client) }
                        }
                    }
                } catch (e: Exception) {
                    delay(5_000) // retry si le port est brièvement occupé
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = socket.getInputStream().bufferedReader()

            // Lire la ligne de requête : "GET /pause HTTP/1.1"
            val requestLine = reader.readLine() ?: return
            // Vider les headers
            while (true) {
                val h = reader.readLine() ?: break
                if (h.isEmpty()) break
            }

            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            dispatchForPath(path)

            val body = """{"ok":true}"""
            val response = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                body
            socket.getOutputStream().write(response.toByteArray())

        } catch (e: Exception) {
            // connexion fermée prématurément — ignoré
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun dispatchForPath(path: String) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (path) {
            "/next"                  -> KeyEvent.KEYCODE_MEDIA_NEXT
            "/prev", "/previous"     -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "/play", "/pause",
            "/play_pause"            -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else                     -> return
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    // ── Notification foreground (requis Android 8+) ───────────────────────────

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, "Boesch 510",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Boesch 510")
            .setContentText("Contrôle média actif")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }
}
