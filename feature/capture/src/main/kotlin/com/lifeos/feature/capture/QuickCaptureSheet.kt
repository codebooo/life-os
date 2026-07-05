package com.lifeos.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.feature.capture.data.CaptureDestination

/**
 * The global quick-capture sheet (§7.8, [src 11,16]): one focused field,
 * ≤1 tap to capture. The classifier proposes a destination; destination chips
 * make redirecting a single tap. Raw text is already persisted before
 * confirmation — nothing typed here is ever lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(
    onDismiss: () -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is QuickCaptureUiEffect.Dismiss) {
                viewModel.onEvent(QuickCaptureUiEvent.Reset)
                onDismiss()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = {
        viewModel.onEvent(QuickCaptureUiEvent.Reset)
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Get it out of your head", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = uiState.input,
                onValueChange = { viewModel.onEvent(QuickCaptureUiEvent.InputChanged(it)) },
                placeholder = { Text("A thought, a task, a number to log…") },
                trailingIcon = if (uiState.classifying) {
                    { CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onEvent(QuickCaptureUiEvent.Submit) }),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            uiState.pending?.let { pending ->
                Text(
                    "Save \"${pending.suggestion.title}\" as:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DestinationChip(
                        label = "Note",
                        icon = { Icon(Icons.AutoMirrored.Filled.Note, null) },
                        suggested = pending.suggestion.destination == CaptureDestination.NOTE,
                        onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.NOTE)) },
                    )
                    DestinationChip(
                        label = "Task",
                        icon = { Icon(Icons.Filled.Checklist, null) },
                        suggested = pending.suggestion.destination == CaptureDestination.TASK,
                        onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.TASK)) },
                    )
                    if (pending.suggestion.formName != null) {
                        DestinationChip(
                            label = "Log: ${pending.suggestion.formName}",
                            icon = { Icon(Icons.Filled.Insights, null) },
                            suggested = pending.suggestion.destination == CaptureDestination.LOG,
                            onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.LOG)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationChip(
    label: String,
    icon: @Composable () -> Unit,
    suggested: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = suggested,
        onClick = onClick,
        label = { Text(if (suggested) "$label ✓" else label) },
        leadingIcon = icon,
    )
}
