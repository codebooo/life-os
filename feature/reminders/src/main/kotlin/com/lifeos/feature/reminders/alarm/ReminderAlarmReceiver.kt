package com.lifeos.feature.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires the reminder by handing off to [AlarmService] — a foreground service
 * that loops the ALARM-stream ringtone, vibrates, and posts the full-screen
 * notification. Channel history: v1 (alpha.1) was created silent and channels
 * are immutable, so v2 is a fresh id (created in the service).
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

        // The foreground AlarmService owns ringing + the full-screen notification —
        // sound survives silent mode, app death, and suppressed activity launches.
        AlarmService.ring(context, reminderId, title)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                remindersRepository.onFired(reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.lifeos.reminders.FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val LEGACY_CHANNEL_ID = "lifeos_reminders"
        const val CHANNEL_ID = "lifeos_reminders_v2"
        const val NOTIFICATION_TAG = "reminder"
    }
}
