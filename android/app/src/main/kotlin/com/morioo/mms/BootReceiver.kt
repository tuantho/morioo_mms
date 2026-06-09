package com.morioo.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Démarre automatiquement MediaBridgeService au boot du téléphone.
 * Ainsi le bridge 127.0.0.1:8765 est actif dès qu'Android Auto démarre,
 * sans avoir à ouvrir l'app manuellement.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val svc = Intent(context, MediaBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
