package com.lifeos.feature.chat.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
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

            Text("On-device models", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.hfToken,
                onValueChange = { viewModel.onEvent(AiSettingsUiEvent.HfTokenChanged(it)) },
                label = { Text("Hugging Face token") },
                supportingText = { Text("Gemma is license-gated: accept it once on huggingface.co, then paste a read token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.variants.forEach { variant ->
                val installed = variant.id in uiState.installedVariantIds
                val downloading = uiState.downloadingId == variant.id
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(variant.label, style = MaterialTheme.typography.bodyLarge)
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { uiState.downloadPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${uiState.downloadPercent}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (installed) {
                        OutlinedButton(onClick = { viewModel.onEvent(AiSettingsUiEvent.DeleteModel(variant)) }) {
                            Text("Delete")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.onEvent(AiSettingsUiEvent.DownloadModel(variant)) },
                            enabled = uiState.downloadingId == null,
                        ) {
                            Text(if (downloading) "…" else "Download")
                        }
                    }
                }
            }

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
