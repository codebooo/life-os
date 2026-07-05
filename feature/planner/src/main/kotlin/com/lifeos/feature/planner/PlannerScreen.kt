package com.lifeos.feature.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.database.evolution.EvolutionDao
import com.lifeos.core.database.evolution.InteractionLogEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.planner.data.PlanItem
import com.lifeos.feature.planner.data.PlannerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlannerUiState(
    val computing: Boolean = false,
    val plan: List<PlanItem> = emptyList(),
    val rationale: String? = null,
)

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val plannerEngine: PlannerEngine,
    private val evolutionDao: EvolutionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlannerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        recompute()
    }

    fun recompute() {
        _uiState.value = _uiState.value.copy(computing = true)
        viewModelScope.launch {
            val plan = plannerEngine.computePlan()
            _uiState.value = PlannerUiState(computing = false, plan = plan)
            _uiState.value = _uiState.value.copy(rationale = plannerEngine.rationale(plan))
        }
    }

    /** Acceptance signal feeds the evolution layer (§Module 13). */
    fun resolve(item: PlanItem, accepted: Boolean) {
        viewModelScope.launch {
            evolutionDao.insert(
                InteractionLogEntity(
                    engine = "ON_DEVICE",
                    kind = "PLANNER",
                    accepted = accepted,
                    at = System.currentTimeMillis(),
                ),
            )
        }
        _uiState.value = _uiState.value.copy(plan = _uiState.value.plan - item)
    }
}

/** "Jarvis" (§Module 24, [src 40]): the ranked what's-next with rationale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerRoute(viewModel: PlannerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What's next") },
                actions = {
                    if (uiState.computing) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::recompute) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Recompute")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            uiState.rationale?.let { rationale ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        rationale,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (uiState.plan.isEmpty() && !uiState.computing) {
                EmptyState(
                    title = "Nothing needs you right now",
                    description = "Reminders, events, tasks, deliveries and renewals all feed this ranking.",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.plan, key = { "${it.module}-${it.entityId}" }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = { Text(item.reason) },
                            leadingContent = {
                                AssistChip(onClick = {}, label = { Text(item.module.name.lowercase()) })
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { viewModel.resolve(item, accepted = true) }) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "On it",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.resolve(item, accepted = false) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Skip")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
