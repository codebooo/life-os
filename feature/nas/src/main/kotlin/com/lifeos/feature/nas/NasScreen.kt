package com.lifeos.feature.nas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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

/** NAS access (§Module 8): DSM FileStation browser + one-tap self-host status ([src 23]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NasRoute(viewModel: NasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(NasUiEvent.DismissMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NAS") },
                navigationIcon = {
                    if (uiState.tab == 0 && uiState.currentPath != null) {
                        IconButton(onClick = { viewModel.onEvent(NasUiEvent.NavigateUp) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = uiState.tab) {
                Tab(selected = uiState.tab == 0, onClick = { viewModel.onEvent(NasUiEvent.SelectTab(0)) }, text = { Text("Files") })
                Tab(selected = uiState.tab == 1, onClick = { viewModel.onEvent(NasUiEvent.SelectTab(1)) }, text = { Text("Server apps") })
            }
            if (uiState.tab == 0) FilesTab(uiState, viewModel::onEvent) else ServerAppsTab(uiState, viewModel::onEvent)
        }
    }
}

@Composable
private fun FilesTab(uiState: NasUiState, onEvent: (NasUiEvent) -> Unit) {
    if (!uiState.connected) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = { onEvent(NasUiEvent.BaseUrlChanged(it)) },
                label = { Text("DSM URL") },
                placeholder = { Text("https://192.168.1.2:5001") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.user,
                    onValueChange = { onEvent(NasUiEvent.UserChanged(it)) },
                    label = { Text("User") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { onEvent(NasUiEvent.PasswordChanged(it)) },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = { onEvent(NasUiEvent.Connect) },
                enabled = !uiState.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.busy) "Connecting…" else "Connect")
            }
        }
        return
    }

    Text(
        uiState.currentPath ?: "Shares",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    if (uiState.files.isEmpty()) {
        EmptyState(title = "Empty", description = "No entries in this folder.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.files, key = { it.path }) { file ->
                Surface(onClick = { if (file.isDir) onEvent(NasUiEvent.Open(file.path)) }) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                if (file.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                            )
                        },
                        headlineContent = { Text(file.name) },
                        supportingContent = {
                            if (!file.isDir && file.sizeBytes > 0) {
                                Text("%,d KB".format(file.sizeBytes / 1024))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerAppsTab(uiState: NasUiState, onEvent: (NasUiEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.serverApps, key = { it.name }) { app ->
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = { onEvent(NasUiEvent.CheckHealth) },
                            label = {
                                Text(
                                    when (app.healthy) {
                                        true -> "Running"
                                        false -> "Unreachable"
                                        null -> "Check"
                                    },
                                )
                            },
                        )
                    }
                    Text(app.purpose, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        app.setup,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
