package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receptor de inicio para asegurar el correcto restablecimiento tras reinicio del teléfono.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Teléfono reiniciado. NotificationListenerService será reconectado por el sistema Android.")
        }
    }
}
