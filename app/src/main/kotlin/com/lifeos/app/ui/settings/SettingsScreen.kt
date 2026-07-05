package com.lifeos.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.SectionHeader

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
