package com.lifeos.feature.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AlarmManager alarms don't survive reboot — re-arm every pending reminder. */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var remindersRepository: RemindersRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        LifeLogger.i("ReminderBoot", "Rescheduling reminders after reboot")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                remindersRepository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
