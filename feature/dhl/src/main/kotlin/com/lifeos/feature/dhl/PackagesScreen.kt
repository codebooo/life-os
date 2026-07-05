package com.lifeos.feature.dhl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import java.text.DateFormat
import java.util.Date

/** Package tracker (§Module 15): manual adds here, automatic adds via rule R1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagesRoute(viewModel: PackagesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(PackagesUiEvent.DismissError)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Packages") },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(PackagesUiEvent.ToggleKeyEditor) }) {
                        Icon(Icons.Filled.Key, contentDescription = "DHL API key")
                    }
                    IconButton(onClick = { viewModel.onEvent(PackagesUiEvent.RefreshAll) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh all")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (uiState.showKeyEditor) {
                OutlinedTextField(
                    value = uiState.apiKeyDraft,
                    onValueChange = { viewModel.onEvent(PackagesUiEvent.ApiKeyChanged(it)) },
                    label = { Text("DHL API key") },
                    supportingText = { Text("From developer.dhl.com — stored on-device only") },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.onEvent(PackagesUiEvent.SaveApiKey) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Save key")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = uiState.newTrackingNumber,
                onValueChange = { viewModel.onEvent(PackagesUiEvent.TrackingNumberChanged(it)) },
                placeholder = { Text("Add tracking number") },
                trailingIcon = {
                    IconButton(onClick = { viewModel.onEvent(PackagesUiEvent.AddPackage) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Track")
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.packages.isEmpty()) {
                EmptyState(
                    title = "No packages",
                    description = "Tracking numbers in your notifications are picked up automatically (rule R1).",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(uiState.packages, key = { it.pkg.id }) { item ->
                        Card(
                            onClick = { viewModel.onEvent(PackagesUiEvent.ToggleExpand(item.pkg.id)) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            ListItem(
                                headlineContent = { Text(item.pkg.label ?: item.pkg.trackingNumber) },
                                supportingContent = {
                                    Column {
                                        Text(item.pkg.statusDescription ?: item.pkg.status)
                                        item.pkg.estimatedDeliveryAt?.let {
                                            Text(
                                                "Arrives " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                                                    .format(Date(it)),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                leadingContent = { AssistChip(onClick = {}, label = { Text(item.pkg.status) }) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.onEvent(PackagesUiEvent.Delete(item.pkg.id)) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                },
                            )
                            if (item.expanded) {
                                item.events.forEach { event ->
                                    Text(
                                        "· ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.at))} — " +
                                            (event.description ?: event.status) +
                                            (event.location?.let { " ($it)" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    )
                                }
                                androidx.compose.foundation.layout.Spacer(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
