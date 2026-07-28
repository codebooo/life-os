package com.lifeos.feature.screentime.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.lifeos.core.database.screentime.AppUsageEntity
import com.lifeos.core.database.screentime.ScreenTimeDao
import com.lifeos.core.database.screentime.ScreenTimeDayEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Harvests Android's usage data into LifeOS's own database (§Module Screen
 * Time). Samsung/Android purge raw usage after ~a month; mirroring each day
 * into Room keeps the history forever.
 *
 * Foreground time is derived from the raw event stream (RESUMED → PAUSED/STOPPED
 * pairs), NOT from `queryAndAggregateUsageStats`: that API returns each app's
 * total for whatever interval bucket the system picked (often weekly/monthly),
 * so asking it for a single day reports the same bloated total for every day —
 * which is exactly the "191h per day" nonsense it produced before.
 */
@Singleton
class ScreenTimeCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ScreenTimeDao,
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Rebuilds the last [days] days from the event stream. Stored days older
     * than yesterday are skipped (they can no longer change) unless [force] is
     * set, which re-derives everything — used once to replace the bad totals
     * written by the old aggregate-based implementation.
     */
    suspend fun sync(days: Int = 45, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        val alreadyStored = if (force) emptySet() else dao.capturedDates().toSet()

        for (offset in 0 until days) {
            val bounds = dayBounds(offset)
            if (offset > 1 && bounds.key in alreadyStored) continue
            if (bounds.end <= bounds.start) continue

            val day = deriveDay(usageManager, bounds) ?: continue
            val apps = day.foregroundMsByPackage
                .filter { it.value > 0 }
                .map { (packageName, ms) ->
                    val label = runCatching {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0),
                        ).toString()
                    }.getOrDefault(packageName)
                    AppUsageEntity(bounds.key, packageName, label, ms)
                }
            val total = apps.sumOf { it.foregroundMs }
            if (total == 0L && day.unlocks == 0) continue

            dao.upsertDay(
                ScreenTimeDayEntity(
                    date = bounds.key,
                    totalForegroundMs = total,
                    unlocks = day.unlocks,
                    notifications = day.notifications,
                    capturedAt = System.currentTimeMillis(),
                ),
            )
            if (apps.isNotEmpty()) dao.upsertApps(apps)
        }
    }

    private data class DayUsage(
        val foregroundMsByPackage: Map<String, Long>,
        val unlocks: Int,
        val notifications: Int,
    )

    /**
     * Walks one day's events and sums each app's time on screen. A session runs
     * from ACTIVITY_RESUMED to the next PAUSED/STOPPED for that package (or to
     * screen-off / end of day, so a night with an app left open can't count
     * hours it wasn't visible for).
     */
    private fun deriveDay(manager: UsageStatsManager, bounds: DayBounds): DayUsage? {
        val events = manager.queryEvents(bounds.start, bounds.end)
        if (events == null) return null

        val totals = mutableMapOf<String, Long>()
        val resumedAt = mutableMapOf<String, Long>()
        var unlocks = 0
        var notifications = 0
        var sawAnyEvent = false
        val event = UsageEvents.Event()

        fun close(packageName: String, until: Long) {
            val start = resumedAt.remove(packageName) ?: return
            val delta = until - start
            if (delta > 0) totals[packageName] = (totals[packageName] ?: 0L) + delta
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            sawAnyEvent = true
            val packageName = event.packageName ?: continue
            val stamp = event.timeStamp.coerceIn(bounds.start, bounds.end)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // A resume without a matching pause replaces the old mark.
                    resumedAt[packageName] = stamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> close(packageName, stamp)
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
                // Screen off ends every open session — nothing is on screen now.
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                -> resumedAt.keys.toList().forEach { close(it, stamp) }
            }
        }
        if (!sawAnyEvent) return null
        // Anything still open at the cutoff counts up to the cutoff only.
        resumedAt.keys.toList().forEach { close(it, bounds.end) }

        return DayUsage(
            foregroundMsByPackage = totals,
            unlocks = unlocks,
            notifications = notifications,
        )
    }

    private data class DayBounds(val start: Long, val end: Long, val key: String)

    private fun dayBounds(offsetDaysAgo: Int): DayBounds {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -offsetDaysAgo)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val key = dateFormat.format(cal.time)
        val end = (start + 86_400_000L).coerceAtMost(System.currentTimeMillis())
        return DayBounds(start, end, key)
    }
}
