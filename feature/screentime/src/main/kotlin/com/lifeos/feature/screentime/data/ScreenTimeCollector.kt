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
 * Harvests Android's UsageStats into LifeOS's own database (§Module Screen
 * Time). Samsung/Android purge raw usage after ~a month; by mirroring each day
 * into Room the moment we can read it, LifeOS keeps the history forever.
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
     * Pulls the last [days] days (default 45, wider than Samsung keeps) and
     * upserts every day that has data. Already-stored days are refreshed so a
     * partial "today" fills in, but historical rows are never dropped.
     */
    suspend fun sync(days: Int = 45) = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        for (offset in 0 until days) {
            val (dayStart, dayEnd, dateKey) = dayBounds(offset)
            val stats = usageManager.queryAndAggregateUsageStats(dayStart, dayEnd)
            if (stats.isEmpty()) continue

            val apps = stats.values
                .filter { it.totalTimeInForeground > 0 }
                .map { usage ->
                    val label = runCatching {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(usage.packageName, 0),
                        ).toString()
                    }.getOrDefault(usage.packageName)
                    AppUsageEntity(dateKey, usage.packageName, label, usage.totalTimeInForeground)
                }
            val total = apps.sumOf { it.foregroundMs }
            if (total == 0L) continue

            val (unlocks, notifications) = countEvents(usageManager, dayStart, dayEnd)
            dao.upsertDay(ScreenTimeDayEntity(dateKey, total, unlocks, notifications, System.currentTimeMillis()))
            dao.upsertApps(apps)
        }
    }

    private fun countEvents(manager: UsageStatsManager, start: Long, end: Long): Pair<Int, Int> {
        // Unlocks = keyguard-hidden events. Notification counts have no public
        // UsageEvents API, so they stay 0 (kept in the schema for a future source).
        var unlocks = 0
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks++
        }
        return unlocks to 0
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
