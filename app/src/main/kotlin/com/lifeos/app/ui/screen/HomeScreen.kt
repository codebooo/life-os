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
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifeos.core.ui.navigation.LifeDestination

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
fun HomeScreen(onNavigate: (LifeDestination) -> Unit) {
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
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "LifeOS",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
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
