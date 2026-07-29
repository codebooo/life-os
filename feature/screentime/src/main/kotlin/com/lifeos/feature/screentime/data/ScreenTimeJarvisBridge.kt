package com.lifeos.feature.screentime.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.screentime.ScreenTimeDao
import com.lifeos.core.service.ActionEcho
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Screen Time as Jarvis reads it (§Module 9): totals, trend, worst apps. */
internal class ScreenTimeProvider @Inject constructor(
    private val screenTimeDao: ScreenTimeDao,
) : LifeDataProvider {

    override val topic: String = "screen_time"
    override val description: String = "phone usage per day, unlocks, top apps"

    override suspend fun read(query: String?): String {
        val days = query?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(1, 60) ?: 7
        val rows = screenTimeDao.allDays().take(days)
        if (rows.isEmpty()) return "Screen time: nothing recorded yet (grant Usage access in the module)."
        val total = rows.sumOf { it.totalForegroundMs }
        val apps = screenTimeDao.appsBetween(rows.last().date, rows.first().date)
            .groupBy { it.packageName }
            .map { (_, rowsForApp) -> rowsForApp.first().label to rowsForApp.sumOf { it.foregroundMs } }
            .sortedByDescending { it.second }
            .take(6)
        return buildString {
            appendLine("Screen time, last ${rows.size} day(s):")
            appendLine("- total ${human(total)}, average ${human(total / rows.size)}/day")
            appendLine("- unlocks ${rows.sumOf { it.unlocks }}")
            rows.take(7).forEach { day ->
                appendLine("- ${day.date}: ${human(day.totalForegroundMs)} (${day.unlocks} unlocks)")
            }
            if (apps.isNotEmpty()) {
                appendLine("Top apps: " + apps.joinToString("; ") { "${it.first} ${human(it.second)}" })
            }
        }.trim()
    }

    private fun human(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
}

/** Screen Time actions Jarvis can take: sync now, export to Downloads. */
internal class ScreenTimeActionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collector: ScreenTimeCollector,
    private val exporter: ScreenTimeExporter,
    private val echo: ActionEcho,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean =
        action is LifeAction.SyncScreenTime || action is LifeAction.ExportScreenTime

    override suspend fun execute(action: LifeAction): LifeResult<Long?> = when (action) {
        is LifeAction.SyncScreenTime -> {
            if (!collector.hasPermission()) {
                LifeResult.Failure(LifeError.Validation("Usage access is not granted yet"))
            } else {
                collector.sync()
                LifeResult.Success(null)
            }
        }

        is LifeAction.ExportScreenTime -> {
            val (name, body) = exporter.build(action.format, action.weekOnly)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, if (name.endsWith(".csv")) "text/csv" else "application/json")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return LifeResult.Failure(LifeError.Unknown("Downloads folder refused the file"))
            context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            echo.fileName(name)
            LifeResult.Success(null)
        }

        else -> LifeResult.Failure(LifeError.Validation("Unsupported action"))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ScreenTimeJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: ScreenTimeProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: ScreenTimeActionHandler): LifeActionHandler
}
