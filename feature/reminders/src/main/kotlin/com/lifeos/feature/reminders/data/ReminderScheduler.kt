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
        val fire = firePendingIntent(reminder.id, reminder.title)
        // setAlarmClock is exact + doze-proof and, for USE_EXACT_ALARM apps, needs
        // no user grant. If the OEM still blocks it, fall back to an inexact wake
        // so the reminder is late-but-never-lost rather than throwing.
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.at, showIntent), fire)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, fire)
                LifeLogger.i(TAG, "Exact alarms not permitted — scheduled inexact")
            }
        } catch (se: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, fire)
            LifeLogger.e(TAG, "Exact alarm denied; used inexact", se)
        }
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
