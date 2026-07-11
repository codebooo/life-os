package com.lifeos.feature.nas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    // App-store-style rows (Aurora-search look): square app glyph, name +
    // subtitle stacked, and a status/Check action button on the right.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
    ) {
        items(uiState.serverApps, key = { it.name }) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(
                            app.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        app.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                val (label, container) = when (app.healthy) {
                    true -> "Running" to MaterialTheme.colorScheme.primary
                    false -> "Retry" to MaterialTheme.colorScheme.error
                    null -> "Check" to MaterialTheme.colorScheme.secondaryContainer
                }
                androidx.compose.material3.Button(
                    onClick = { onEvent(NasUiEvent.CheckHealth) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = container),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) { Text(label) }
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 82.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
