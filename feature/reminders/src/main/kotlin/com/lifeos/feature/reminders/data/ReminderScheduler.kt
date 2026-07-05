package com.lifeos.feature.reminders.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.reminders.ReminderEntity
import com.lifeos.feature.reminders.alarm.ReminderAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps AlarmManager's alarm-clock API (§Module 2): exact, doze-proof, and
 * surfaced by the system as an upcoming alarm. Reschedule-all runs on boot.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: ReminderEntity) {
        if (!reminder.enabled || reminder.at <= System.currentTimeMillis()) return
        val showIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(reminder.at, showIntent),
            firePendingIntent(reminder.id, reminder.title),
        )
        LifeLogger.d(TAG, "Scheduled reminder ${reminder.id} at ${reminder.at}")
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(firePendingIntent(reminderId, title = null))
    }

    private fun firePendingIntent(reminderId: Long, title: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmReceiver.ACTION_FIRE)
                .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
                .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val TAG = "ReminderScheduler"
    }
}
