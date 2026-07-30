package com.lifeos.app.surfaces

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.glance.unit.ColorProvider
import com.lifeos.app.MainActivity
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Next up" home-screen widget (§Module Surfaces).
 *
 * Reads the same DAOs the app does through a Hilt entry point - a widget is not
 * an Android component Hilt can inject directly. Tapping it opens LifeOS.
 */
class NextUpWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lines = loadLines(context)
        provideContent {
            WidgetBody(lines)
        }
    }

    @Composable
    private fun WidgetBody(lines: List<String>) {
        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text(
                    "Next up",
                    style = TextStyle(color = ColorProvider(TITLE)),
                )
                lines.forEach { line ->
                    Text(line, style = TextStyle(color = GlanceTheme.colors.onSurface))
                }
            }
        }
    }

    private suspend fun loadLines(context: Context): List<String> = runCatching {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val now = System.currentTimeMillis()
        val events = entryPoint.calendarDao().observeUpcoming(now, limit = 2).first()
        val tasks = entryPoint.captureDao().observeTasks().first().filter { !it.done }
        buildList {
            events.forEach { add("${AT.format(Date(it.startsAt))} ${it.title.take(28)}") }
            tasks.take(3 - events.size.coerceAtMost(2)).forEach { add("- ${it.title.take(30)}") }
            if (isEmpty()) add("Nothing scheduled")
        }
    }.getOrElse { listOf("LifeOS") }

    private companion object {
        val AT = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        val TITLE = androidx.compose.ui.graphics.Color(0xFF9FCBA6)
    }
}

/** Receiver the system talks to. */
class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun calendarDao(): CalendarDao
    fun captureDao(): CaptureDao
}
