package com.lifeos.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.SectionHeader
import com.lifeos.core.designsystem.theme.PALETTE_DYNAMIC
import com.lifeos.core.designsystem.theme.ThemePalettes

/** Central settings (§8.4 onboarding grants + endpoints in one place). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(SettingsUiEvent.DismissMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(title = "Theme")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = uiState.themePalette == PALETTE_DYNAMIC,
                    onClick = { viewModel.onEvent(SettingsUiEvent.ThemePaletteChanged(PALETTE_DYNAMIC)) },
                    label = { Text("Dynamic") },
                )
                ThemePalettes.forEach { (id, color) ->
                    val selected = uiState.themePalette == id
                    Box(
                        modifier = Modifier
                            .size(if (selected) 36.dp else 30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { viewModel.onEvent(SettingsUiEvent.ThemePaletteChanged(id)) },
                    )
                }
            }
            Text(
                "Dynamic follows your wallpaper (Material You); a color fixes the accent palette.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(title = "Navigation bar")
            val navLabels = mapOf(
                "CALENDAR" to "Calendar",
                "TASKS" to "Tasks",
                "INBOX" to "Inbox",
                "ASSISTANT" to "Jarvis",
            )
            // Enabled tabs first (orderable), then disabled ones to re-enable.
            uiState.navBarItems.forEachIndexed { index, id ->
                ReorderRow(
                    label = navLabels[id] ?: id,
                    enabled = true,
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.navBarItems.lastIndex,
                    onMoveUp = { viewModel.onEvent(SettingsUiEvent.MoveNavItem(id, -1)) },
                    onMoveDown = { viewModel.onEvent(SettingsUiEvent.MoveNavItem(id, +1)) },
                    onToggle = { viewModel.onEvent(SettingsUiEvent.ToggleNavItem(id)) },
                )
            }
            navLabels.keys.filter { it !in uiState.navBarItems }.forEach { id ->
                ReorderRow(
                    label = navLabels[id] ?: id,
                    enabled = false,
                    canMoveUp = false,
                    canMoveDown = false,
                    onMoveUp = {},
                    onMoveDown = {},
                    onToggle = { viewModel.onEvent(SettingsUiEvent.ToggleNavItem(id)) },
                )
            }
            Text(
                "Toggle tabs and order them with the arrows — Home stays pinned first. " +
                    "Everything stays reachable from the Home grid.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(title = "AI")
            OutlinedTextField(
                value = uiState.ollamaBaseUrl,
                onValueChange = { viewModel.onEvent(SettingsUiEvent.OllamaUrlChanged(it)) },
                label = { Text("NAS Ollama URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.ollamaModel,
                onValueChange = { viewModel.onEvent(SettingsUiEvent.OllamaModelChanged(it)) },
                label = { Text("Ollama model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.hfToken,
                onValueChange = { viewModel.onEvent(SettingsUiEvent.HfTokenChanged(it)) },
                label = { Text("Hugging Face token (Gemma downloads)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Model downloads live in Assistant → settings icon.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(title = "Integrations")
            OutlinedTextField(
                value = uiState.dhlApiKey,
                onValueChange = { viewModel.onEvent(SettingsUiEvent.DhlKeyChanged(it)) },
                label = { Text("DHL API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.dhlApiSecret,
                onValueChange = { viewModel.onEvent(SettingsUiEvent.DhlSecretChanged(it)) },
                label = { Text("DHL API secret (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Email (Proton Bridge), Home Assistant and NAS credentials are configured inside their modules.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { viewModel.onEvent(SettingsUiEvent.Save) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            SectionHeader(title = "System access")
            SystemSettingRow(
                title = "Default digital assistant",
                subtitle = "Pick LifeOS so long-press home opens quick capture",
            ) {
                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
            SystemSettingRow(
                title = "Notification access",
                subtitle = "Feeds the unified Inbox and rules like package tracking",
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            SystemSettingRow(
                title = "Battery optimization",
                subtitle = "Exempt LifeOS so reminders and rules keep running",
            ) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                SystemSettingRow(
                    title = "Full-screen reminder alarms",
                    subtitle = "Allow reminders to take over the screen like an alarm clock",
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            android.net.Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                SystemSettingRow(
                    title = "Alarms & reminders",
                    subtitle = "Let reminders fire at the exact time, even in Doze",
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(android.net.Uri.parse("package:${context.packageName}")),
                    )
                }
            }

            SectionHeader(title = "About")
            Card(modifier = Modifier.padding(bottom = 24.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LifeOS ${uiState.versionName}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Private, offline-first. AI runs on this device or on your NAS — never a third-party cloud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Bank sync note: Sparkasse's PSD2 API is restricted to BaFin-licensed providers, so a personal app " +
                            "can't connect directly (that's how Finanzguru does it, via a licensed aggregator). The open " +
                            "FinTS/HBCI route is planned — until then, use CSV import in Finance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemSettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
        )
    }
}

/**
 * One orderable row: long-press-drag anywhere on the row to move it (same
 * gesture as the Home tiles), with up/down arrows kept for one-tap moves.
 */
@Composable
private fun ReorderRow(
    label: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
    showToggle: Boolean = true,
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragY }
                } else {
                    Modifier
                },
            )
            .pointerInput(label, canMoveUp, canMoveDown) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragY += amount.y
                        // One row of travel = one position step.
                        if (dragY <= -rowHeightPx && canMoveUp) {
                            onMoveUp()
                            dragY += rowHeightPx
                        } else if (dragY >= rowHeightPx && canMoveDown) {
                            onMoveDown()
                            dragY -= rowHeightPx
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragY = 0f
                    },
                )
            },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move $label up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move $label down")
        }
        if (showToggle) {
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}
