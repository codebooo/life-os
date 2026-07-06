package com.lifeos.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.ui.navigation.LifeDestination
import com.lifeos.feature.planner.PlannerViewModel

private data class AppGridItem(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val destination: LifeDestination,
)

/**
 * Home (§7.6, minimal cut): the app grid for modules outside the bottom bar.
 * The ranked card feed and Planner top-card land in Phase 13.
 */
@Composable
fun HomeScreen(
    onNavigate: (LifeDestination) -> Unit,
    plannerViewModel: PlannerViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val plannerState by plannerViewModel.uiState.collectAsStateWithLifecycle()
    val listLayout by homeViewModel.listLayout.collectAsStateWithLifecycle()
    // Fresh ranking every time Home comes back into view — never a stale card.
    androidx.compose.runtime.LaunchedEffect(Unit) { plannerViewModel.recompute() }
    val items = listOf(
        AppGridItem(
            label = "Notes",
            description = "Local-first Markdown + ask-my-notes",
            icon = Icons.AutoMirrored.Filled.Note,
            destination = LifeDestination.Notes,
        ),
        AppGridItem(
            label = "Logger",
            description = "Log anything, with structure",
            icon = Icons.Filled.Insights,
            destination = LifeDestination.Logger,
        ),
        AppGridItem(
            label = "Packages",
            description = "DHL tracking + delivery reminders",
            icon = Icons.Filled.LocalShipping,
            destination = LifeDestination.Packages,
        ),
        AppGridItem(
            label = "Finance",
            description = "Private on-device budget",
            icon = Icons.Filled.AccountBalanceWallet,
            destination = LifeDestination.Finance,
        ),
        AppGridItem(
            label = "Scan",
            description = "Receipts, whiteboards, barcodes",
            icon = Icons.Filled.DocumentScanner,
            destination = LifeDestination.Scan,
        ),
        AppGridItem(
            label = "Planner",
            description = "Jarvis: what deserves you now",
            icon = Icons.Filled.AutoAwesome,
            destination = LifeDestination.Planner,
        ),
        AppGridItem(
            label = "Books",
            description = "Private shelves + what to read next",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            destination = LifeDestination.Books,
        ),
        AppGridItem(
            label = "Routes",
            description = "Saved places, one-tap navigation",
            icon = Icons.Filled.Navigation,
            destination = LifeDestination.Routes,
        ),
        AppGridItem(
            label = "Smart home",
            description = "Home Assistant scenes + toggles",
            icon = Icons.Filled.Home,
            destination = LifeDestination.SmartHome,
        ),
        AppGridItem(
            label = "NAS",
            description = "FileStation browser + server apps",
            icon = Icons.Filled.Storage,
            destination = LifeDestination.Nas,
        ),
        AppGridItem(
            label = "Clock",
            description = "Faces, world clock, stopwatch, timer",
            icon = Icons.Filled.Schedule,
            destination = LifeDestination.Clock,
        ),
        AppGridItem(
            label = "Focus",
            description = "Visual timer, streaks, overwhelm mode",
            icon = Icons.Filled.Bolt,
            destination = LifeDestination.Focus,
        ),
        AppGridItem(
            label = "Memex",
            description = "Private archive of what you read",
            icon = Icons.Filled.Archive,
            destination = LifeDestination.Memex,
        ),
        AppGridItem(
            label = "Macros",
            description = "Describe automations in plain words",
            icon = Icons.Filled.SmartToy,
            destination = LifeDestination.Macros,
        ),
        AppGridItem(
            label = "Evolution",
            description = "How your on-device AI is learning",
            icon = Icons.Filled.Timeline,
            destination = LifeDestination.Evolution,
        ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("LifeOS", style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = { homeViewModel.toggleLayout() }) {
                    Icon(
                        if (listLayout) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = if (listLayout) "Grid view" else "List view",
                    )
                }
                IconButton(onClick = { onNavigate(LifeDestination.Settings) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
        // §7.6: the Planner "Next:" top card ([src 40]).
        plannerState.plan.firstOrNull()?.let { next ->
            Card(
                onClick = { onNavigate(LifeDestination.Planner) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(next.title, style = MaterialTheme.typography.titleLarge)
                    Text(next.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (listLayout) {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(items.size) { index ->
                    val item = items[index]
                    Card(
                        onClick = { onNavigate(item.destination) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(item.label) },
                            supportingContent = { Text(item.description) },
                            leadingContent = {
                                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items) { item ->
                    Card(onClick = { onNavigate(item.destination) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
