package com.lifeos.feature.capture

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickCaptureSheet(
    onDismiss: () -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let { viewModel.onEvent(QuickCaptureUiEvent.VoiceResult(it)) }
    }

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
                    {
                        IconButton(onClick = {
                            // Brain-dump path ([src 16]): speak freely, review the split.
                            voiceLauncher.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Just get it out of your head")
                                },
                            )
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = "Voice brain-dump")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onEvent(QuickCaptureUiEvent.Submit) }),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (uiState.dumpItems.isNotEmpty()) {
                Text(
                    "Review ${uiState.dumpItems.size} items:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                uiState.dumpItems.forEachIndexed { index, item ->
                    Card {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Text(
                                "${item.destination.name.lowercase()}: ${item.title}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(onClick = { viewModel.onEvent(QuickCaptureUiEvent.ConfirmDumpItem(index)) }) {
                                Icon(Icons.Filled.Check, contentDescription = "Save")
                            }
                            IconButton(onClick = { viewModel.onEvent(QuickCaptureUiEvent.DiscardDumpItem(index)) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Discard")
                            }
                        }
                    }
                }
                Button(
                    onClick = { viewModel.onEvent(QuickCaptureUiEvent.SaveAllDumpItems) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save all")
                }
            }

            uiState.pending?.let { pending ->
                Text(
                    "Save \"${pending.suggestion.title}\" as:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                pending.suggestion.at?.let { at ->
                    Text(
                        "⏰ ${formatWhen(at)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val suggested = pending.suggestion.destination
                val hasTime = pending.suggestion.at != null
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DestinationChip(
                        label = "Note",
                        icon = { Icon(Icons.AutoMirrored.Filled.Note, null) },
                        suggested = suggested == CaptureDestination.NOTE,
                        onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.NOTE)) },
                    )
                    DestinationChip(
                        label = "Task",
                        icon = { Icon(Icons.Filled.Checklist, null) },
                        suggested = suggested == CaptureDestination.TASK,
                        onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.TASK)) },
                    )
                    if (hasTime) {
                        DestinationChip(
                            label = "Reminder",
                            icon = { Icon(Icons.Filled.Alarm, null) },
                            suggested = suggested == CaptureDestination.REMINDER,
                            onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.REMINDER)) },
                        )
                        DestinationChip(
                            label = "Event",
                            icon = { Icon(Icons.Filled.Event, null) },
                            suggested = suggested == CaptureDestination.EVENT,
                            onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.EVENT)) },
                        )
                        DestinationChip(
                            label = "Timer",
                            icon = { Icon(Icons.Filled.HourglassEmpty, null) },
                            suggested = suggested == CaptureDestination.TIMER,
                            onClick = { viewModel.onEvent(QuickCaptureUiEvent.Confirm(CaptureDestination.TIMER)) },
                        )
                    }
                    if (pending.suggestion.formName != null) {
                        DestinationChip(
                            label = "Log: ${pending.suggestion.formName}",
                            icon = { Icon(Icons.Filled.Insights, null) },
                            suggested = suggested == CaptureDestination.LOG,
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

private fun formatWhen(at: Long): String {
    val now = System.currentTimeMillis()
    val deltaMin = (at - now) / 60_000L
    val clock = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(at))
    return when {
        deltaMin in 0..90 -> "in ${deltaMin} min · $clock"
        else -> clock
    }
}
