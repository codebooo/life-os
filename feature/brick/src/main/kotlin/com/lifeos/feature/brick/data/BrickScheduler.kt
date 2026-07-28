package com.lifeos.feature.brick.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.brick.BrickProfileEntity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms the next start/end alarm for every TIME-activated profile (§Module
 * Brick). Inexact-but-idle-safe alarms are enough here: a mode beginning a
 * minute late is fine, and it avoids burning the exact-alarm budget.
 */
@Singleton
class BrickScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun rescheduleAll(profiles: List<BrickProfileEntity>) {
        profiles.forEach { profile ->
            cancel(profile.id, starting = true)
            cancel(profile.id, starting = false)
            // Inverse modes are always schedule-driven on both edges: the window
            // is the blocked stretch, and tag taps only work inside it.
            val timeStart = profile.inverse || profile.activator == "TIME"
            val timeEnd = profile.inverse || profile.deactivator == "TIME"
            if (!timeStart && !timeEnd) return@forEach
            profile.startMinuteOfDay?.takeIf { timeStart }?.let {
                schedule(profile.id, it, starting = true)
            }
            profile.endMinuteOfDay?.takeIf { timeEnd }?.let {
                schedule(profile.id, it, starting = false)
            }
        }
    }

    private fun schedule(profileId: Long, minuteOfDay: Int, starting: Boolean) {
        val next = nextOccurrence(minuteOfDay)
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(profileId, starting))
        }.onFailure { LifeLogger.w(TAG, "Could not arm Brick alarm", it) }
    }

    private fun cancel(profileId: Long, starting: Boolean) {
        alarmManager.cancel(pendingIntent(profileId, starting))
    }

    private fun pendingIntent(profileId: Long, starting: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // Distinct request codes per profile and edge.
            (profileId.toInt() * 2) + if (starting) 0 else 1,
            Intent(context, BrickScheduleReceiver::class.java)
                .setAction(if (starting) BrickScheduleReceiver.ACTION_START else BrickScheduleReceiver.ACTION_END)
                .putExtra(BrickScheduleReceiver.EXTRA_PROFILE_ID, profileId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextOccurrence(minuteOfDay: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private companion object {
        const val TAG = "BrickScheduler"
    }
}

/** Fires at a profile's start/end minute and flips the mode. */
@AndroidEntryPoint
class BrickScheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var brickRepository: BrickRepository

    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        if (profileId == -1L) return
        val starting = intent.action == ACTION_START
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                brickRepository.refresh()
                brickRepository.onScheduleTick(profileId, starting)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.lifeos.brick.START"
        const val ACTION_END = "com.lifeos.brick.END"
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}
