package com.lifeos.feature.agentic

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.ai.macro.MacroCatalog
import com.lifeos.core.ai.macro.MacroStep
import com.lifeos.core.designsystem.component.EmptyState

/** NL macro authoring + dry-run preview + run (§Module 12, [src 41]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosRoute(viewModel: MacrosViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(MacrosUiEvent.DismissMessage)
        }
    }
    // Re-check whenever the screen resumes (e.g. returning from accessibility settings).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(MacrosUiEvent.RefreshServiceState)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    uiState.draft?.let { draft ->
        MacroEditorScreen(draft = draft, uiState = uiState, onEvent = viewModel::onEvent)
        return
    }

    uiState.detail?.let { macro ->
        MacroDetailScreen(macro = macro, uiState = uiState, onEvent = viewModel::onEvent)
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Macros") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(MacrosUiEvent.NewMacro) }) {
                Icon(Icons.Filled.Add, contentDescription = "New macro")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                // Breathing room under the app bar: the field's label used to
                // ride up into it.
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!uiState.serviceEnabled) {
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Macros need the \"LifeOS Macros\" accessibility service — enable it once, " +
                                "then every run still requires your tap here.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Open accessibility settings") }
                    }
                }
            }
            OutlinedTextField(
                value = uiState.nlPrompt,
                onValueChange = { viewModel.onEvent(MacrosUiEvent.PromptChanged(it)) },
                label = { Text("Create with Jarvis") },
                placeholder = { Text("e.g. open Spotify") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onEvent(MacrosUiEvent.Compile) },
                    enabled = uiState.nlPrompt.isNotBlank() && !uiState.compiling,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (uiState.compiling) "Compiling…" else "Compile with AI")
                }
                OutlinedButton(onClick = { viewModel.onEvent(MacrosUiEvent.NewMacro) }) {
                    Text("Build by hand")
                }
            }
            if (uiState.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (uiState.macros.isEmpty()) {
                EmptyState(
                    title = "No macros yet",
                    description = "Describe one above, or tap + to build it step by step. " +
                        "Nothing runs until you say so.",
                )
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.macros, key = { it.id }) { macro ->
                    Card(onClick = { viewModel.onEvent(MacrosUiEvent.OpenDetail(macro)) }) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(macro.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${stepCount(macro.stepsJson)} steps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = macro.enabled,
                                onCheckedChange = { viewModel.onEvent(MacrosUiEvent.ToggleEnabled(macro)) },
                            )
                            IconButton(
                                onClick = { viewModel.onEvent(MacrosUiEvent.Run(macro)) },
                                enabled = macro.enabled && !uiState.running,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Run")
                            }
                            IconButton(onClick = { viewModel.onEvent(MacrosUiEvent.EditMacro(macro)) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.onEvent(MacrosUiEvent.Delete(macro.id)) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.preview?.let { steps ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MacrosUiEvent.DiscardPreview) },
            title = { Text("Compiled steps") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. ${step.action} " +
                                listOfNotNull(step.target, step.text, step.delayMs?.let { "${it}ms" })
                                    .joinToString(" "),
                        )
                    }
                    Spacer(modifier = Modifier.padding(2.dp))
                    Text(
                        "Opens in the editor next, so you can fix or extend anything before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(MacrosUiEvent.SavePreview) }) { Text("Open in editor") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(MacrosUiEvent.DiscardPreview) }) { Text("Discard") }
            },
        )
    }
}

private fun stepCount(stepsJson: String): Int = stepsJson.count { it == '{' }

/**
 * Macro detail (§Module 12): full step list, rename, run and delete —
 * opened by tapping a macro card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroDetailScreen(
    macro: com.lifeos.core.database.agentic.MacroEntity,
    uiState: MacrosUiState,
    onEvent: (MacrosUiEvent) -> Unit,
) {
    var name by remember(macro.id) { androidx.compose.runtime.mutableStateOf(macro.name) }
    val steps = remember(macro.stepsJson) {
        runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.lifeos.core.ai.macro.MacroStep.serializer()),
                macro.stepsJson,
            )
        }.getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Macro") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(MacrosUiEvent.CloseDetail) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(MacrosUiEvent.EditMacro(macro)) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit steps")
                    }
                    IconButton(onClick = { onEvent(MacrosUiEvent.Delete(macro.id)) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { onEvent(MacrosUiEvent.Rename(macro, name)) }) { Text("Save") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Prompt: ${macro.nlPrompt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onEvent(MacrosUiEvent.Run(macro)) },
                enabled = macro.enabled && !uiState.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (uiState.running) "Running…" else "Run macro") }
            if (uiState.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Text("Steps (${steps.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(steps.size) { index ->
                    val step = steps[index]
                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${index + 1}. ${step.action}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            listOfNotNull(
                                step.target?.let { "Target: $it" },
                                step.text?.let { "Text: $it" },
                                step.delayMs?.let { "Wait: ${it}ms" },
                            ).forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Macro editor (§Module 12): build a macro by hand, or fix one the AI wrote.
 * Steps are picked from the same catalogue the executor implements, so nothing
 * here can compile to an action that then fails at run time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroEditorScreen(
    draft: MacroDraft,
    uiState: MacrosUiState,
    onEvent: (MacrosUiEvent) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "New macro" else "Edit macro") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(MacrosUiEvent.CloseDraft) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onEvent(MacrosUiEvent.SaveDraft) }) { Text("Save") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add step")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onEvent(MacrosUiEvent.DraftNameChanged(it)) },
                label = { Text("Macro name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onEvent(MacrosUiEvent.TestDraft) },
                    enabled = draft.steps.isNotEmpty() && !uiState.running,
                ) { Text(if (uiState.running) "Running…" else "Test now") }
                OutlinedButton(onClick = { showPicker = true }) { Text("Add step") }
            }
            if (uiState.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (draft.steps.isEmpty()) {
                Text(
                    "No steps yet. Add one: open an app, tap something, type, wait, swipe, toggle the " +
                        "torch, control media, or fire a LifeOS action.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(draft.steps.size) { index ->
                    StepCard(
                        index = index,
                        step = draft.steps[index],
                        lastIndex = draft.steps.lastIndex,
                        appSuggestions = uiState.installedApps,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }

    if (showPicker) {
        ActionPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { action ->
                onEvent(MacrosUiEvent.AddStep(action))
                showPicker = false
            },
        )
    }
}

/** One editable step: its argument field, reorder arrows and a delete button. */
@Composable
private fun StepCard(
    index: Int,
    step: MacroStep,
    lastIndex: Int,
    appSuggestions: List<String>,
    onEvent: (MacrosUiEvent) -> Unit,
) {
    val spec = MacroCatalog.byAction[step.action]
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${spec?.label ?: step.action}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onEvent(MacrosUiEvent.MoveStep(index, -1)) },
                    enabled = index > 0,
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                IconButton(
                    onClick = { onEvent(MacrosUiEvent.MoveStep(index, +1)) },
                    enabled = index < lastIndex,
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                IconButton(onClick = { onEvent(MacrosUiEvent.RemoveStep(index)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove step")
                }
            }
            when (spec?.arg) {
                com.lifeos.core.ai.macro.MacroArg.TARGET -> {
                    OutlinedTextField(
                        value = step.target.orEmpty(),
                        onValueChange = { onEvent(MacrosUiEvent.UpdateStep(index, step.copy(target = it))) },
                        label = { Text(spec.hint.ifBlank { "Target" }) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (step.action == "LAUNCH" && step.target.orEmpty().length >= 2) {
                        val matches = appSuggestions.filter {
                            it.contains(step.target.orEmpty(), ignoreCase = true) &&
                                !it.equals(step.target.orEmpty(), ignoreCase = true)
                        }.take(4)
                        if (matches.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                matches.forEach { label ->
                                    TextButton(
                                        onClick = {
                                            onEvent(MacrosUiEvent.UpdateStep(index, step.copy(target = label)))
                                        },
                                    ) { Text(label, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }

                com.lifeos.core.ai.macro.MacroArg.TEXT -> OutlinedTextField(
                    value = step.text.orEmpty(),
                    onValueChange = { onEvent(MacrosUiEvent.UpdateStep(index, step.copy(text = it))) },
                    label = { Text(spec.hint.ifBlank { "Text" }) },
                    modifier = Modifier.fillMaxWidth(),
                )

                com.lifeos.core.ai.macro.MacroArg.DELAY -> OutlinedTextField(
                    value = step.delayMs?.toString().orEmpty(),
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(7)
                        onEvent(MacrosUiEvent.UpdateStep(index, step.copy(delayMs = digits.toLongOrNull())))
                    },
                    label = { Text(spec.hint.ifBlank { "Milliseconds" }) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> if (spec == null) {
                    Text(
                        "Unknown action \"${step.action}\" — delete this step.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Every action the executor supports, grouped, with a search box. */
@Composable
private fun ActionPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = MacroCatalog.actions.filter {
        query.isBlank() ||
            it.label.contains(query, ignoreCase = true) ||
            it.action.contains(query, ignoreCase = true) ||
            it.group.contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a step") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search actions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    MacroCatalog.groups.forEach { group ->
                        val inGroup = matches.filter { it.group == group }
                        if (inGroup.isEmpty()) return@forEach
                        item(key = "group-$group") {
                            Text(
                                group,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                        items(inGroup.size, key = { "action-$group-$it" }) { position ->
                            val spec = inGroup[position]
                            TextButton(
                                onClick = { onPick(spec.action) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(spec.label)
                                    if (spec.hint.isNotBlank()) {
                                        Text(
                                            spec.hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
