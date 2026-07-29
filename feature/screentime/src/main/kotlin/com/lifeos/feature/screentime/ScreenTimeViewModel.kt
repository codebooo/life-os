package com.lifeos.feature.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.screentime.AppUsageEntity
import com.lifeos.core.database.screentime.ScreenTimeDao
import com.lifeos.core.database.screentime.ScreenTimeDayEntity
import com.lifeos.core.datastore.SettingsRepository
import com.lifeos.feature.screentime.data.ScreenTimeExportFormat
import com.lifeos.feature.screentime.data.ScreenTimeExporter
import com.lifeos.feature.screentime.data.ScreenTimeCollector
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class DayBar(val date: String, val label: String, val totalMs: Long, val unlocks: Int, val notifications: Int)

data class AppLine(val label: String, val packageName: String, val ms: Long)

/** Export shapes offered by the download dialog. */
enum class ExportFormat { JSON, CSV_DAYS, CSV_APPS }

data class ScreenTimeUiState(
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    /** 0 = current week, 1 = last week, … */
    val weekOffset: Int = 0,
    val weekLabel: String = "",
    val days: List<DayBar> = emptyList(),
    val dailyAverageMs: Long = 0,
    val weekTotalMs: Long = 0,
    val topApps: List<AppLine> = emptyList(),
    val totalDaysStored: Int = 0,
    /**
     * Selected day (yyyy-MM-dd) or null for the whole week. When set, the stat
     * cards and the app list describe that day only and the other bars dim —
     * the iOS-style inline drill-down instead of a popup.
     */
    val selectedDate: String? = null,
    val selectedLabel: String = "",
    val showExportDialog: Boolean = false,
    val exportMessage: String? = null,
)

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val exporter: ScreenTimeExporter,
    private val dao: ScreenTimeDao,
    private val collector: ScreenTimeCollector,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenTimeUiState())
    val uiState = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val rangeFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val dayTitleFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(hasPermission = collector.hasPermission(), loading = true)
            if (collector.hasPermission()) {
                // The first release derived totals from queryAndAggregateUsageStats,
                // which reports whole-bucket sums per day (the "191h/day" bug).
                // Drop those rows once and rebuild from the event stream.
                val rebuilt = settingsRepository.screenTimeRebuilt.first()
                if (!rebuilt) {
                    dao.deleteAllDays()
                    dao.deleteAllApps()
                    collector.sync(force = true)
                    settingsRepository.setScreenTimeRebuilt(true)
                } else {
                    collector.sync()
                }
            }
            loadWeek(_uiState.value.weekOffset)
            _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    /** Selects a day inline, or clears the selection when tapping it again. */
    fun selectDay(date: String) {
        if (_uiState.value.selectedDate == date) {
            clearSelection()
            return
        }
        viewModelScope.launch {
            val apps = dao.appsOn(date).map { AppLine(it.label, it.packageName, it.foregroundMs) }
            val parsed = runCatching { dateFormat.parse(date) }.getOrNull()
            _uiState.value = _uiState.value.copy(
                selectedDate = date,
                selectedLabel = parsed?.let { dayTitleFormat.format(it) } ?: date,
                topApps = apps.take(20),
            )
        }
    }

    fun clearSelection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedDate = null, selectedLabel = "")
            loadWeek(_uiState.value.weekOffset)
        }
    }

    fun showExportDialog() { _uiState.value = _uiState.value.copy(showExportDialog = true) }
    fun dismissExportDialog() { _uiState.value = _uiState.value.copy(showExportDialog = false) }
    fun dismissExportMessage() { _uiState.value = _uiState.value.copy(exportMessage = null) }

    /** Builds the requested export body; the screen writes it to Downloads. */
    suspend fun buildExport(format: ExportFormat, weekOnly: Boolean): Pair<String, String> {
        val keys = if (weekOnly) _uiState.value.days.map { it.date }.toSet() else null
        return exporter.build(
            format = when (format) {
                ExportFormat.JSON -> ScreenTimeExportFormat.JSON
                ExportFormat.CSV_DAYS -> ScreenTimeExportFormat.CSV_DAYS
                ExportFormat.CSV_APPS -> ScreenTimeExportFormat.CSV_APPS
            },
            dateKeys = keys,
            suffix = if (weekOnly) "week" else "all",
        )
    }

    fun onExported(fileName: String?) {
        _uiState.value = _uiState.value.copy(
            showExportDialog = false,
            exportMessage = fileName?.let { "Saved $it to Downloads" } ?: "Export failed",
        )
    }

    fun previousWeek() { loadWeekAsync(_uiState.value.weekOffset + 1) }
    fun nextWeek() { if (_uiState.value.weekOffset > 0) loadWeekAsync(_uiState.value.weekOffset - 1) }

    private fun loadWeekAsync(offset: Int) = viewModelScope.launch { loadWeek(offset) }

    private suspend fun loadWeek(offset: Int) {
        val allDays = dao.allDays().associateBy { it.date }
        // Monday-start week containing today, shifted back by offset weeks.
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, -offset)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val weekStart = cal.time
        val bars = ArrayList<DayBar>(7)
        val dateKeys = ArrayList<String>(7)
        repeat(7) {
            val key = dateFormat.format(cal.time)
            dateKeys += key
            val day = allDays[key]
            bars += DayBar(key, dayLabelFormat.format(cal.time), day?.totalForegroundMs ?: 0, day?.unlocks ?: 0, day?.notifications ?: 0)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val weekEnd = Calendar.getInstance().apply { time = weekStart; add(Calendar.DAY_OF_YEAR, 6) }.time

        val daysWithData = bars.count { it.totalMs > 0 }
        val weekTotal = bars.sumOf { it.totalMs }
        val apps = dao.appsBetween(dateKeys.first(), dateKeys.last())
            .groupBy { it.packageName }
            .map { (pkg, rows) -> AppLine(rows.first().label, pkg, rows.sumOf { it.foregroundMs }) }
            .sortedByDescending { it.ms }
            .take(12)

        _uiState.value = _uiState.value.copy(
            weekOffset = offset,
            weekLabel = "${rangeFormat.format(weekStart)} – ${rangeFormat.format(weekEnd)}",
            days = bars,
            weekTotalMs = weekTotal,
            dailyAverageMs = if (daysWithData == 0) 0 else weekTotal / daysWithData,
            topApps = apps,
            totalDaysStored = allDays.size,
        )
    }

    /** JSON body for the given rows. */
}
