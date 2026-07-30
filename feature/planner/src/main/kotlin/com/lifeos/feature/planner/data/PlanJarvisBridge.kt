package com.lifeos.feature.planner.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** The shape of a day as Jarvis reads it: what is booked, what is waiting. */
internal class PlanProvider @Inject constructor(
    private val calendarDao: CalendarDao,
    private val captureDao: CaptureDao,
) : LifeDataProvider {

    override val topic: String = "plan"
    override val description: String = "today's shape: booked blocks, free gaps and unscheduled tasks"

    override suspend fun read(query: String?): String {
        val offset = query?.let { if (it.contains("tomorrow", ignoreCase = true)) 1 else null } ?: 0
        val dayStart = System.currentTimeMillis() - System.currentTimeMillis() % 86_400_000L + offset * 86_400_000L
        val events = calendarDao.observeWindow(dayStart, dayStart + 86_400_000L).first().sortedBy { it.startsAt }
        val tasks = captureDao.observeTasks().first().filter { !it.done }
        return buildString {
            appendLine(if (offset == 0) "Today:" else "Tomorrow:")
            if (events.isEmpty()) {
                appendLine("- nothing booked")
            } else {
                events.forEach {
                    appendLine("- ${AT.format(Date(it.startsAt))}-${AT.format(Date(it.endsAt))} ${it.title}")
                }
            }
            val dated = tasks.filter { it.dueAt != null }.sortedBy { it.dueAt }
            if (dated.isNotEmpty()) {
                appendLine("Due soon:")
                dated.take(6).forEach { appendLine("- [${it.id}] ${it.title} (${AT.format(Date(it.dueAt!!))})") }
            }
            appendLine("Unscheduled tasks: ${tasks.count { it.dueAt == null }}")
        }.trim()
    }

    private companion object {
        val AT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}

internal class PlanActionHandler @Inject constructor(
    private val planner: DayPlanner,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.PlanDay

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val placed = planner.plan((action as LifeAction.PlanDay).dayOffset)
        return if (placed.isEmpty()) {
            LifeResult.Failure(LifeError.Validation("No free slot long enough, or nothing to schedule"))
        } else {
            LifeResult.Success(placed.size.toLong())
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlanJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: PlanProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: PlanActionHandler): LifeActionHandler
}
