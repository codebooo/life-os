package com.lifeos.feature.clock

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class ClockUiState(
    val tab: Int = 0,
    val face: Int = 0,
    val worldClocks: List<String> = emptyList(),
    val zoneDraft: String = "",
    val message: String? = null,
)

sealed interface ClockUiEvent {
    data class SelectTab(val index: Int) : ClockUiEvent
    data class SelectFace(val index: Int) : ClockUiEvent
    data class ZoneDraftChanged(val value: String) : ClockUiEvent
    data object AddZone : ClockUiEvent
    data class RemoveZone(val zoneId: String) : ClockUiEvent
    data object DismissMessage : ClockUiEvent
}

sealed interface ClockUiEffect

@HiltViewModel
class ClockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : LifeViewModel<ClockUiState, ClockUiEvent, ClockUiEffect>(ClockUiState()) {

    init {
        viewModelScope.launch {
            settingsRepository.worldClocks.collect { zones ->
                updateState { it.copy(worldClocks = zones) }
            }
        }
    }

    override fun onEvent(event: ClockUiEvent) {
        when (event) {
            is ClockUiEvent.SelectTab -> updateState { it.copy(tab = event.index) }
            is ClockUiEvent.SelectFace -> updateState { it.copy(face = event.index) }
            is ClockUiEvent.ZoneDraftChanged -> updateState { it.copy(zoneDraft = event.value) }
            ClockUiEvent.AddZone -> viewModelScope.launch {
                val draft = uiState.value.zoneDraft.trim()
                val zone = ZoneId.getAvailableZoneIds()
                    .firstOrNull { it.equals(draft, ignoreCase = true) }
                    ?: ZoneId.getAvailableZoneIds()
                        .firstOrNull { it.substringAfterLast('/').equals(draft, ignoreCase = true) }
                if (zone == null) {
                    updateState { it.copy(message = "Unknown time zone — try a city like Tokyo or Europe/Berlin") }
                    return@launch
                }
                val current = settingsRepository.worldClocks.first()
                if (zone !in current) settingsRepository.setWorldClocks(current + zone)
                updateState { it.copy(zoneDraft = "") }
            }
            is ClockUiEvent.RemoveZone -> viewModelScope.launch {
                val current = settingsRepository.worldClocks.first()
                settingsRepository.setWorldClocks(current - event.zoneId)
            }
            ClockUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }
}
