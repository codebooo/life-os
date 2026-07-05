package com.lifeos.feature.dhl

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.dhl.PackageEntity
import com.lifeos.core.database.dhl.TrackingEventEntity
import com.lifeos.core.datastore.IntegrationsRepository
import com.lifeos.feature.dhl.data.PackagesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PackageItem(
    val pkg: PackageEntity,
    val expanded: Boolean,
    val events: List<TrackingEventEntity>,
)

data class PackagesUiState(
    val packages: List<PackageItem> = emptyList(),
    val newTrackingNumber: String = "",
    val showKeyEditor: Boolean = false,
    val apiKeyDraft: String = "",
    val error: String? = null,
)

sealed interface PackagesUiEvent {
    data class TrackingNumberChanged(val value: String) : PackagesUiEvent
    data object AddPackage : PackagesUiEvent
    data class ToggleExpand(val packageId: Long) : PackagesUiEvent
    data class Delete(val packageId: Long) : PackagesUiEvent
    data object RefreshAll : PackagesUiEvent
    data object ToggleKeyEditor : PackagesUiEvent
    data class ApiKeyChanged(val value: String) : PackagesUiEvent
    data object SaveApiKey : PackagesUiEvent
    data object DismissError : PackagesUiEvent
}

sealed interface PackagesUiEffect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PackagesViewModel @Inject constructor(
    private val packagesRepository: PackagesRepository,
    private val integrationsRepository: IntegrationsRepository,
) : LifeViewModel<PackagesUiState, PackagesUiEvent, PackagesUiEffect>(PackagesUiState()) {

    private val expandedId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            combine(
                packagesRepository.observePackages(),
                expandedId.flatMapLatest { id ->
                    if (id == null) emptyFlow() else packagesRepository.observeEvents(id)
                },
                expandedId,
            ) { packages, events, expanded ->
                packages.map { pkg ->
                    PackageItem(
                        pkg = pkg,
                        expanded = pkg.id == expanded,
                        events = if (pkg.id == expanded) events else emptyList(),
                    )
                }
            }.collect { items -> updateState { it.copy(packages = items) } }
        }
        viewModelScope.launch {
            val key = integrationsRepository.dhlApiKey.first()
            updateState { it.copy(apiKeyDraft = key) }
        }
    }

    override fun onEvent(event: PackagesUiEvent) {
        when (event) {
            is PackagesUiEvent.TrackingNumberChanged ->
                updateState { it.copy(newTrackingNumber = event.value) }
            PackagesUiEvent.AddPackage -> add()
            is PackagesUiEvent.ToggleExpand ->
                expandedId.value = if (expandedId.value == event.packageId) null else event.packageId
            is PackagesUiEvent.Delete -> viewModelScope.launch {
                packagesRepository.delete(event.packageId)
            }
            PackagesUiEvent.RefreshAll -> viewModelScope.launch {
                packagesRepository.refreshAllActive()
            }
            PackagesUiEvent.ToggleKeyEditor ->
                updateState { it.copy(showKeyEditor = !it.showKeyEditor) }
            is PackagesUiEvent.ApiKeyChanged -> updateState { it.copy(apiKeyDraft = event.value) }
            PackagesUiEvent.SaveApiKey -> viewModelScope.launch {
                integrationsRepository.setDhlApiKey(uiState.value.apiKeyDraft)
                updateState { it.copy(showKeyEditor = false) }
            }
            PackagesUiEvent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private fun add() {
        val number = uiState.value.newTrackingNumber.trim()
        if (number.isEmpty()) return
        viewModelScope.launch {
            when (val result = packagesRepository.addPackage(number, label = null, source = null)) {
                is LifeResult.Success -> updateState { it.copy(newTrackingNumber = "") }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }
}
