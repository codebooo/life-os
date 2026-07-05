package com.lifeos.feature.adhd

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.adhd.FocusDao
import com.lifeos.core.database.adhd.FocusSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class FocusUiState(
    val tab: Int = 0,
    val sessions: List<FocusSessionEntity> = emptyList(),
    val completedCount: Int = 0,
    val streakDays: Int = 0,
)

sealed interface FocusUiEvent {
    data class SelectTab(val index: Int) : FocusUiEvent
    data class SessionFinished(val minutes: Int, val completed: Boolean) : FocusUiEvent
}

sealed interface FocusUiEffect

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val focusDao: FocusDao,
) : LifeViewModel<FocusUiState, FocusUiEvent, FocusUiEffect>(FocusUiState()) {

    init {
        viewModelScope.launch {
            focusDao.observeRecent().collect { sessions ->
                updateState {
                    it.copy(
                        sessions = sessions,
                        completedCount = sessions.count { s -> s.completed },
                        streakDays = streakDays(sessions),
                    )
                }
            }
        }
    }

    override fun onEvent(event: FocusUiEvent) {
        when (event) {
            is FocusUiEvent.SelectTab -> updateState { it.copy(tab = event.index) }
            is FocusUiEvent.SessionFinished -> viewModelScope.launch {
                focusDao.insert(
                    FocusSessionEntity(
                        minutes = event.minutes,
                        startedAt = System.currentTimeMillis() - event.minutes * 60_000L,
                        completed = event.completed,
                    ),
                )
            }
        }
    }

    /** Consecutive days (ending today or yesterday) with a completed session. */
    private fun streakDays(sessions: List<FocusSessionEntity>): Int {
        val zone = ZoneId.systemDefault()
        val days = sessions.filter { it.completed }
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .toSortedSet(compareByDescending { it })
        if (days.isEmpty()) return 0
        val today = java.time.LocalDate.now(zone)
        var cursor = when (days.first()) {
            today -> today
            today.minusDays(1) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        for (day in days) {
            if (day == cursor) {
                streak++
                cursor = cursor.minusDays(1)
            } else if (day.isBefore(cursor)) {
                break
            }
        }
        return streak
    }
}
