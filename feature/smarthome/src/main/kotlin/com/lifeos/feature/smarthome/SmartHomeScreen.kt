package com.lifeos.feature.smarthome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState

/** Smart Home (§Module 23): live HA entities, tap-to-toggle, scenes; R11 tags. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHomeRoute(viewModel: SmartHomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(SmartHomeUiEvent.DismissMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart home") },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(SmartHomeUiEvent.Refresh) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.onEvent(SmartHomeUiEvent.ToggleSettings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (uiState.showSettings || uiState.entities.isEmpty()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.baseUrl,
                        onValueChange = { viewModel.onEvent(SmartHomeUiEvent.UrlChanged(it)) },
                        label = { Text("Home Assistant URL") },
                        placeholder = { Text("http://192.168.1.5:8123") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.token,
                        onValueChange = { viewModel.onEvent(SmartHomeUiEvent.TokenChanged(it)) },
                        label = { Text("Long-lived access token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.onEvent(SmartHomeUiEvent.SaveAndConnect) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save & connect")
                    }
                    Text(
                        "Tip: put @scene.movie_night in a reminder or event title and LifeOS runs it (rule R11).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.entities.isEmpty() && !uiState.showSettings) {
                EmptyState(title = "Not connected", description = "Configure HA above.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.entities, key = { it.entityId }) { entity ->
                        val domain = entity.entityId.substringBefore('.')
                        Surface {
                            ListItem(
                                headlineContent = { Text(entity.friendlyName) },
                                supportingContent = { Text(entity.entityId + " · " + entity.state) },
                                trailingContent = {
                                    when (domain) {
                                        "scene" -> IconButton(onClick = {
                                            viewModel.onEvent(SmartHomeUiEvent.RunScene(entity.entityId))
                                        }) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = "Run scene")
                                        }
                                        "light", "switch" -> Switch(
                                            checked = entity.state == "on",
                                            onCheckedChange = {
                                                viewModel.onEvent(SmartHomeUiEvent.Toggle(entity.entityId))
                                            },
                                        )
                                        else -> {}
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
