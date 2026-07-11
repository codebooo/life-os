package com.lifeos.feature.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.screentime.AppUsageEntity
import com.lifeos.core.database.screentime.ScreenTimeDao
import com.lifeos.core.database.screentime.ScreenTimeDayEntity
import com.lifeos.feature.screentime.data.ScreenTimeCollector
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
)

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val dao: ScreenTimeDao,
    private val collector: ScreenTimeCollector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenTimeUiState())
    val uiState = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val rangeFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(hasPermission = collector.hasPermission(), loading = true)
            if (collector.hasPermission()) collector.sync()
            loadWeek(_uiState.value.weekOffset)
            _uiState.value = _uiState.value.copy(loading = false)
        }
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

    /** Full export of every stored day + per-app row, as JSON. */
    suspend fun exportJson(): String {
        val days = dao.allDays()
        val apps = dao.allApps().groupBy { it.date }
        val json = Json { prettyPrint = true }
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
        return json.encodeToString(JsonArray.serializer(), array)
    }
}
