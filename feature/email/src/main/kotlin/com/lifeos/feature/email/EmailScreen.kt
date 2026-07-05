package com.lifeos.feature.email

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import java.text.DateFormat
import java.util.Date

/** Email pane of the Inbox tab (§Module 1, IMAP-to-Bridge fallback path). */
@Composable
fun EmailRoute(viewModel: EmailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(EmailUiEvent.DismissMessage)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (uiState.refreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { viewModel.onEvent(EmailUiEvent.Refresh) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
            IconButton(onClick = { viewModel.onEvent(EmailUiEvent.ToggleSettings) }) {
                Icon(Icons.Filled.Settings, contentDescription = "IMAP settings")
            }
        }

        if (uiState.showSettings) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.host,
                        onValueChange = { viewModel.onEvent(EmailUiEvent.HostChanged(it)) },
                        label = { Text("Bridge host") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = uiState.port,
                        onValueChange = { viewModel.onEvent(EmailUiEvent.PortChanged(it)) },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = uiState.user,
                    onValueChange = { viewModel.onEvent(EmailUiEvent.UserChanged(it)) },
                    label = { Text("User") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.onEvent(EmailUiEvent.PasswordChanged(it)) },
                    label = { Text("Bridge app password (never the account password)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.onEvent(EmailUiEvent.SaveSettings) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }
            }
        }

        if (uiState.emails.isEmpty()) {
            EmptyState(
                title = "No mail synced",
                description = "Point LifeOS at your NAS Proton Bridge (IMAP) — invoices become tasks, invites become events.",
            )
        } else {
            LazyColumn {
                items(uiState.emails, key = { it.id }) { email ->
                    ListItem(
                        overlineContent = {
                            Text(
                                email.from.take(40) + " · " +
                                    DateFormat.getDateInstance(DateFormat.SHORT)
                                        .format(Date(email.receivedAt)),
                            )
                        },
                        headlineContent = {
                            Text(email.subject, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (email.hasInvoiceSignal) AssistChip(onClick = {}, label = { Text("Invoice") })
                                if (email.hasInviteSignal) AssistChip(onClick = {}, label = { Text("Invite") })
                                if (email.hasSubscriptionSignal) AssistChip(onClick = {}, label = { Text("Subscription") })
                            }
                        },
                    )
                }
            }
        }
    }
}
