package com.lifeos.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifeos.core.common.log.LifeLogger

/** Revives the coordination service after reboot (§1.4). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            LifeLogger.i("BootReceiver", "Boot completed — restarting coordination service")
            LifeOsForegroundService.start(context)
        }
    }
}
