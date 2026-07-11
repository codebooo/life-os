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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.ListItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
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
    val savedOrder by homeViewModel.homeOrder.collectAsStateWithLifecycle()
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
        AppGridItem(
            label = "Downloader",
            description = "Private video & audio downloads",
            icon = Icons.Filled.Download,
            destination = LifeDestination.Downloader,
        ),
        AppGridItem(
            label = "Plants",
            description = "Care atlas + watering reminders",
            icon = Icons.Filled.LocalFlorist,
            destination = LifeDestination.Plants,
        ),
        AppGridItem(
            label = "News",
            description = "Headlines from credible sources",
            icon = Icons.Filled.Newspaper,
            destination = LifeDestination.News,
        ),
        AppGridItem(
            label = "Screen Time",
            description = "Digital wellbeing, kept forever",
            icon = Icons.Filled.Timelapse,
            destination = LifeDestination.ScreenTime,
        ),
    )

    // Hidden Vault reveal (§Module Vault): long-press the "LifeOS" title and a
    // lock appears for 5 seconds — the only way in. Biometrics gate the screen.
    var vaultRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(vaultRevealed) {
        if (vaultRevealed) {
            kotlinx.coroutines.delay(5_000)
            vaultRevealed = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "LifeOS",
                    style = MaterialTheme.typography.headlineMedium,
                    // Hold for a full 5 seconds (not the ~0.5s system long-press) to
                    // reveal the hidden Vault — deliberate, hard to trigger by accident.
                    modifier = Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val revealed = withTimeoutOrNull(5_000L) {
                                waitForUpOrCancellation()
                                false // released before 5s
                            } ?: true // 5s elapsed while still held
                            if (revealed) vaultRevealed = true
                        }
                    },
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = vaultRevealed,
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                ) {
                    IconButton(onClick = { onNavigate(LifeDestination.Vault) }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Vault", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
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
        ReorderableTileGrid(
            items = items,
            savedOrder = savedOrder,
            listLayout = listLayout,
            onNavigate = onNavigate,
            onOrderChanged = { homeViewModel.saveOrder(it) },
        )
    }
}

/**
 * The Home tiles with long-press drag-to-rearrange (§7.6). One implementation
 * serves both layouts: list mode is simply a one-column grid. The final order
 * is persisted when the drag ends.
 */
@Composable
private fun ReorderableTileGrid(
    items: List<AppGridItem>,
    savedOrder: List<String>,
    listLayout: Boolean,
    onNavigate: (LifeDestination) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
) {
    val gridState = rememberLazyGridState()
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Live order lives in a stable state list so recompositions (or the items
    // list being rebuilt each pass) never wipe an in-progress drag — that was
    // the "snap back to origin" bug.
    val order = remember { mutableStateListOf<AppGridItem>() }
    LaunchedEffect(savedOrder, items) {
        val byLabel = items.associateBy { it.label }
        val arranged = if (savedOrder.isEmpty()) {
            items
        } else {
            savedOrder.mapNotNull { byLabel[it] } + items.filter { it.label !in savedOrder }
        }
        // Only re-sync when idle and the content actually changed.
        if (draggingKey == null && order.map { it.label } != arranged.map { it.label }) {
            order.clear()
            order.addAll(arranged)
        }
    }

    fun itemAt(position: Offset) = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        IntRect(info.offset, info.size).contains(position.round())
    }

    LazyVerticalGrid(
        state = gridState,
        columns = if (listLayout) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (listLayout) 8.dp else 12.dp),
        modifier = Modifier.pointerInput(listLayout) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    itemAt(position)?.let { info ->
                        draggingKey = info.key as? String
                        dragOffset = Offset.Zero
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    dragOffset += amount
                    val key = draggingKey ?: return@detectDragGesturesAfterLongPress
                    val dragged = gridState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                    val center = Offset(
                        dragged.offset.x + dragOffset.x + dragged.size.width / 2f,
                        dragged.offset.y + dragOffset.y + dragged.size.height / 2f,
                    )
                    val target = itemAt(center)
                    if (target != null && target.key != key) {
                        val from = order.indexOfFirst { it.label == key }
                        val to = order.indexOfFirst { it.label == target.key }
                        if (from != -1 && to != -1) {
                            order.add(to, order.removeAt(from))
                            // Keep the tile under the finger across the position swap.
                            dragOffset += Offset(
                                (dragged.offset.x - target.offset.x).toFloat(),
                                (dragged.offset.y - target.offset.y).toFloat(),
                            )
                        }
                    }
                },
                onDragEnd = {
                    draggingKey = null
                    dragOffset = Offset.Zero
                    onOrderChanged(order.map { it.label })
                },
                onDragCancel = {
                    draggingKey = null
                    dragOffset = Offset.Zero
                },
            )
        },
    ) {
        items(order, key = { it.label }) { item ->
            val dragging = draggingKey == item.label
            val tileModifier = Modifier
                .fillMaxWidth()
                .then(
                    if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = 16f
                            }
                    } else {
                        Modifier.animateItem()
                    },
                )
            Card(onClick = { onNavigate(item.destination) }, modifier = tileModifier) {
                if (listLayout) {
                    ListItem(
                        headlineContent = { Text(item.label) },
                        supportingContent = { Text(item.description) },
                        leadingContent = {
                            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                    )
                } else {
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
