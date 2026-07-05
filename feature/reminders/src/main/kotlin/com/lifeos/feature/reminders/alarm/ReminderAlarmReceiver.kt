package com.lifeos.feature.reminders.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires the reminder: full-screen over the lockscreen + high-priority notification. */
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
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Exact reminder alarms" },
        )

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
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_TAG, reminderId.toInt(), notification)
    }

    companion object {
        const val ACTION_FIRE = "com.lifeos.reminders.FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val CHANNEL_ID = "lifeos_reminders"
        const val NOTIFICATION_TAG = "reminder"
    }
}
