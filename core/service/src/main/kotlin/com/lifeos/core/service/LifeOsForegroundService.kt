package com.lifeos.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lifeos.core.common.log.LifeLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The persistent coordination hub (§1.4): owns the event bus and, as later
 * phases land, the rules engine, action executors, scheduler façade, HA
 * WebSocket, and planner tick. Started sticky and revived after reboot by
 * [BootReceiver].
 */
@AndroidEntryPoint
class LifeOsForegroundService : LifecycleService() {

    @Inject
    lateinit var eventBus: LifeEventBus

    @Inject
    lateinit var rulesEngine: RulesEngine

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        rulesEngine.start(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        lifecycleScope.launch {
            eventBus.publish(LifeEvent.ServiceStarted(System.currentTimeMillis()))
        }
        LifeLogger.i(TAG, "Coordination service started")

        return START_STICKY
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOS")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(launchAppIntent())
            .build()

    private fun launchAppIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        // Channel importance is immutable once created; the old channel was stuck
        // showing a full notification. Delete it and use a fresh MIN-importance
        // id so the coordination notice hides (no status-bar icon, collapsed).
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background coordination",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Keeps cross-module automation running. Android requires a " +
                "persistent notice for always-on background work; this is the quietest it allows."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val LEGACY_CHANNEL_ID = "lifeos_coordination"
        private const val CHANNEL_ID = "lifeos_coordination_v2"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LifeOsForegroundService::class.java),
                )
            } catch (e: Exception) {
                // Background-start restrictions (e.g. Android 15+ from BOOT_COMPLETED for
                // some FGS types) must never crash the caller; WorkManager-based revival
                // arrives with the scheduler façade in a later phase.
                LifeLogger.w(TAG, "Unable to start coordination service", e)
            }
        }
    }
}
