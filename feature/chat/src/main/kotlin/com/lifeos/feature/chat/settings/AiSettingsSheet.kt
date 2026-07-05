package com.lifeos.feature.chat.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Endpoint + model configuration (§Module 9's settings surface, minimal
 * Phase 1 cut). Values live in DataStore; secrets stay out of source (§9.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is AiSettingsUiEffect.Saved) onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI engines", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = uiState.ollamaBaseUrl,
                onValueChange = { viewModel.onEvent(AiSettingsUiEvent.BaseUrlChanged(it)) },
                label = { Text("NAS Ollama URL") },
                placeholder = { Text("http://192.168.1.2:11434") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.ollamaModel,
                onValueChange = { viewModel.onEvent(AiSettingsUiEvent.ModelChanged(it)) },
                label = { Text("Ollama model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.onDeviceModelPath,
                onValueChange = { viewModel.onEvent(AiSettingsUiEvent.ModelPathChanged(it)) },
                label = { Text("On-device model path (optional)") },
                supportingText = {
                    Text(
                        if (uiState.onDeviceModelInstalled) {
                            "On-device model found — private requests stay local"
                        } else {
                            "Leave blank to auto-detect in Android/data/…/files/models"
                        },
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.testResult?.let { result ->
                Text(result, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(AiSettingsUiEvent.TestConnection) },
                    enabled = !uiState.testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (uiState.testing) "Testing…" else "Test")
                }
                Button(
                    onClick = { viewModel.onEvent(AiSettingsUiEvent.Save) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
