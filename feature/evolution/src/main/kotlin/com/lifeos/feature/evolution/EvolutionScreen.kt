package com.lifeos.feature.evolution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.SectionHeader
import java.text.DateFormat
import java.util.Date

/**
 * Evolution insights (§Module 13): what the AI layer is doing and how often
 * its suggestions are accepted. Data never leaves the device; adaptation
 * (template scoring) grows on top of this log as it accrues.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvolutionRoute(viewModel: EvolutionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Evolution") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${uiState.totalInteractions}", style = MaterialTheme.typography.displaySmall)
                        Text("AI interactions", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            uiState.plannerAcceptRate?.let { "${(it * 100).toInt()}%" } ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text("planner suggestions accepted", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                "Every AI call is logged locally (engine + kind only, never content). As acceptance data " +
                    "accrues, prompt-template scoring picks the variant that works best for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionHeader(title = "Recent activity")
            if (uiState.logs.isEmpty()) {
                EmptyState(
                    title = "No interactions logged yet",
                    description = "Use the Assistant, Planner or Macros — activity shows up here.",
                )
                return@Column
            }
            LazyColumn {
                items(uiState.logs, key = { it.id }) { log ->
                    ListItem(
                        headlineContent = { Text("${log.kind.lowercase()} · ${log.engine.lowercase()}") },
                        supportingContent = {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(log.at)),
                            )
                        },
                        trailingContent = {
                            Text(
                                when (log.accepted) {
                                    true -> "accepted"
                                    false -> "dismissed"
                                    null -> ""
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}
