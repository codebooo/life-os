package com.lifeos.feature.pastebin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.pastebin.data.PasteExpiry
import com.lifeos.feature.pastebin.data.PasteVisibility
import com.lifeos.feature.pastebin.data.PastebinApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pastebin (§Module Pastebin): compose a paste with every option the API
 * exposes (expiry, visibility, syntax, burn-after-read, password), browse and
 * delete the account's pastes, and set the defaults used when text is shared
 * to LifeOS from another app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastebinRoute(viewModel: PastebinViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pastebin") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = state.tab) {
                listOf("New paste", "My pastes", "Settings").forEachIndexed { index, label ->
                    Tab(
                        selected = state.tab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(label) },
                    )
                }
            }
            when (state.tab) {
                0 -> ComposerTab(state, viewModel)
                1 -> ListTab(state, viewModel)
                else -> SettingsTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun ComposerTab(state: PastebinUiState, viewModel: PastebinViewModel) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::onContent,
                label = { Text("Paste content") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Text("Expires", style = MaterialTheme.typography.labelLarge) }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PasteExpiry.entries.forEach { option ->
                    FilterChip(
                        selected = state.expiry == option,
                        onClick = { viewModel.onExpiry(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        item { Text("Visibility", style = MaterialTheme.typography.labelLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PasteVisibility.entries.forEach { option ->
                    FilterChip(
                        selected = state.visibility == option,
                        onClick = { viewModel.onVisibility(option) },
                        label = { Text(option.label.substringBefore(" (")) },
                    )
                }
            }
        }
        item { Text("Syntax", style = MaterialTheme.typography.labelLarge) }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PastebinApi.FORMATS.forEach { option ->
                    FilterChip(
                        selected = state.format == option,
                        onClick = { viewModel.onFormat(option) },
                        label = { Text(option) },
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.burnAfterRead, onCheckedChange = viewModel::onBurn)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("Burn after read")
                    Text(
                        "Deleted the moment it is opened once. Pastebin only allows this on guest pastes, " +
                            "so a burner paste is posted outside your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPassword,
                label = { Text("Password (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            if (state.posting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { viewModel.createPaste() },
                enabled = !state.posting && state.content.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create paste") }
        }
        state.lastUrl?.let { url ->
            item {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Created", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(url, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("paste", url))
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null); Text("  Copy")
                            }
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null); Text("  Open")
                            }
                            OutlinedButton(onClick = { viewModel.clearComposer() }) { Text("New") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListTab(state: PastebinUiState, viewModel: PastebinViewModel) {
    val context = LocalContext.current
    if (!state.signedIn) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sign in with your Pastebin account to list, read and delete your pastes. " +
                    "Creating pastes works without signing in.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsername,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.accountPassword,
                onValueChange = viewModel::onAccountPassword,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { viewModel.signIn() }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${state.pastes.size} paste(s)", style = MaterialTheme.typography.bodyMedium)
            Row {
                IconButton(onClick = { viewModel.refreshList() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
                androidx.compose.material3.TextButton(onClick = { viewModel.signOut() }) { Text("Sign out") }
            }
        }
        if (state.loadingList) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.pastes.isEmpty() && !state.loadingList) {
            EmptyState(title = "No pastes", description = "Anything you create shows up here.")
            return
        }
        LazyColumn {
            items(state.pastes, key = { it.key }) { paste ->
                ListItem(
                    headlineContent = { Text(paste.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            "${paste.visibility} · ${paste.size} B · ${paste.hits} view(s)" +
                                if (paste.createdAt > 0) " · ${DAY.format(Date(paste.createdAt))}" else "",
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paste.url)))
                            }) { Icon(Icons.Filled.OpenInNew, contentDescription = "Open") }
                            IconButton(onClick = { viewModel.deletePaste(paste.key) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SettingsTab(state: PastebinUiState, viewModel: PastebinViewModel) {
    val defaults = state.shareDefaults
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Share-sheet defaults", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                "Applied when you share text or a link to LifeOS and pick \"Pastebin\". " +
                    "Set burn-after-read here to make every shared link a one-time burner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Text("Expires", style = MaterialTheme.typography.labelLarge) }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PasteExpiry.entries.forEach { option ->
                    FilterChip(
                        selected = defaults.expiry == option,
                        onClick = { viewModel.updateShareDefaults { it.copy(expiry = option) } },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        item { Text("Visibility", style = MaterialTheme.typography.labelLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PasteVisibility.entries.forEach { option ->
                    FilterChip(
                        selected = defaults.visibility == option,
                        onClick = { viewModel.updateShareDefaults { it.copy(visibility = option) } },
                        label = { Text(option.label.substringBefore(" (")) },
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = defaults.burnAfterRead,
                    onCheckedChange = { value -> viewModel.updateShareDefaults { it.copy(burnAfterRead = value) } },
                )
                Text("  Burn after read (one-time links)")
            }
        }
        item {
            OutlinedTextField(
                value = defaults.password,
                onValueChange = { value -> viewModel.updateShareDefaults { it.copy(password = value) } },
                label = { Text("Password for shared pastes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val DAY = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
