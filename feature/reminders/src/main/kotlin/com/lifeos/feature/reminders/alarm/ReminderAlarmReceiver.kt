package com.lifeos.feature.reminders.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires the reminder: full-screen over the lockscreen + a loud, vibrating,
 * heads-up notification on the ALARM audio stream.
 *
 * Channel history: v1 (alpha.1) was created without sound/vibration and
 * Android never mutates an existing channel — that's why firings were
 * near-silent. v2 is a fresh id with the full alarm configuration; v1 is
 * deleted on the fly.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var remindersRepository: RemindersRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return
        val title = intent.getStringExtra(EXTRA_TITLE)

        showNotification(context, reminderId, title)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                remindersRepository.onFired(reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, reminderId: Long, titleHint: String?) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // v1 (no sound — channels are immutable once created) must go away.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminder alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Exact reminder alarms — loud and on time"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 600)
                enableLights(true)
                lightColor = Color.CYAN
                setShowBadge(true)
            },
        )

        if (!manager.areNotificationsEnabled()) {
            LifeLogger.e(TAG, "Notifications disabled — reminder $reminderId fired silently")
        }

        val fullScreen = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_TITLE, titleHint)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titleHint ?: "Reminder")
            .setContentText("Tap to open")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_TAG, reminderId.toInt(), notification)
    }

    companion object {
        const val ACTION_FIRE = "com.lifeos.reminders.FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val LEGACY_CHANNEL_ID = "lifeos_reminders"
        const val CHANNEL_ID = "lifeos_reminders_v2"
        const val NOTIFICATION_TAG = "reminder"
        private const val TAG = "ReminderAlarm"
    }
}
