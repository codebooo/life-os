package com.lifeos.feature.screentime.data

import com.lifeos.core.database.screentime.AppUsageEntity
import com.lifeos.core.database.screentime.ScreenTimeDao
import com.lifeos.core.database.screentime.ScreenTimeDayEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Export formats offered by the module and by Jarvis. */
enum class ScreenTimeExportFormat { JSON, CSV_DAYS, CSV_APPS }

/**
 * Builds screen-time exports (§Module Screen Time). Shared by the export dialog
 * and by Jarvis's `[[export_screen_time: …]]` tool so both produce byte-identical
 * files.
 */
@Singleton
class ScreenTimeExporter @Inject constructor(
    private val dao: ScreenTimeDao,
) {

    /** @return file name to body. [dateKeys] limits the range; null = everything. */
    suspend fun build(
        format: ScreenTimeExportFormat,
        dateKeys: Set<String>?,
        suffix: String,
    ): Pair<String, String> {
        val days = dao.allDays().let { all -> if (dateKeys == null) all else all.filter { it.date in dateKeys } }
        val keys = days.map { it.date }.toSet()
        val apps = dao.allApps().filter { it.date in keys }
        return when (format) {
            ScreenTimeExportFormat.JSON ->
                "lifeos-screentime-$suffix.json" to json(days, apps)

            ScreenTimeExportFormat.CSV_DAYS ->
                "lifeos-screentime-days-$suffix.csv" to buildString {
                    appendLine("date,screen_time_minutes,unlocks,notifications")
                    days.sortedBy { it.date }.forEach {
                        appendLine("${it.date},${it.totalForegroundMs / 60_000},${it.unlocks},${it.notifications}")
                    }
                }

            ScreenTimeExportFormat.CSV_APPS ->
                "lifeos-screentime-apps-$suffix.csv" to buildString {
                    appendLine("date,app,package,minutes")
                    apps.sortedWith(compareBy({ it.date }, { -it.foregroundMs })).forEach {
                        appendLine(
                            "${it.date},${it.label.replace(",", " ")},${it.packageName}," +
                                "${it.foregroundMs / 60_000}",
                        )
                    }
                }
        }
    }

    /** Tool-friendly entry point: accepts the format as free text. */
    suspend fun build(format: String, weekOnly: Boolean): Pair<String, String> {
        val parsed = when {
            format.contains("app", ignoreCase = true) -> ScreenTimeExportFormat.CSV_APPS
            format.contains("csv", ignoreCase = true) -> ScreenTimeExportFormat.CSV_DAYS
            else -> ScreenTimeExportFormat.JSON
        }
        val keys = if (weekOnly) dao.allDays().take(7).map { it.date }.toSet() else null
        return build(parsed, keys, if (weekOnly) "week" else "all")
    }

    private fun json(days: List<ScreenTimeDayEntity>, appRows: List<AppUsageEntity>): String {
        val apps = appRows.groupBy { it.date }
        val array = JsonArray(
            days.map { day ->
                JsonObject(
                    mapOf(
                        "date" to JsonPrimitive(day.date),
                        "totalForegroundMs" to JsonPrimitive(day.totalForegroundMs),
                        "unlocks" to JsonPrimitive(day.unlocks),
                        "notifications" to JsonPrimitive(day.notifications),
                        "apps" to JsonArray(
                            (apps[day.date] ?: emptyList()).sortedByDescending { it.foregroundMs }.map {
                                JsonObject(
                                    mapOf(
                                        "package" to JsonPrimitive(it.packageName),
                                        "label" to JsonPrimitive(it.label),
                                        "foregroundMs" to JsonPrimitive(it.foregroundMs),
                                    ),
                                )
                            },
                        ),
                    ),
                )
            },
        )
        return Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), array)
    }
}
