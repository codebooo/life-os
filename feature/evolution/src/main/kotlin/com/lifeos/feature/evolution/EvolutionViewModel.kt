package com.lifeos.feature.evolution

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.evolution.EvolutionDao
import com.lifeos.core.database.evolution.InteractionLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EvolutionUiState(
    val logs: List<InteractionLogEntity> = emptyList(),
    val totalInteractions: Int = 0,
    val plannerAcceptRate: Float? = null,
)

sealed interface EvolutionUiEvent

sealed interface EvolutionUiEffect

@HiltViewModel
class EvolutionViewModel @Inject constructor(
    private val evolutionDao: EvolutionDao,
) : LifeViewModel<EvolutionUiState, EvolutionUiEvent, EvolutionUiEffect>(EvolutionUiState()) {

    init {
        viewModelScope.launch {
            evolutionDao.observeRecent().collect { logs ->
                val plannerTotal = evolutionDao.countByKind("PLANNER")
                val plannerAccepted = evolutionDao.acceptedByKind("PLANNER")
                updateState {
                    it.copy(
                        logs = logs,
                        totalInteractions = logs.size,
                        plannerAcceptRate = if (plannerTotal > 0) plannerAccepted.toFloat() / plannerTotal else null,
                    )
                }
            }
        }
    }

    override fun onEvent(event: EvolutionUiEvent) = Unit
}
