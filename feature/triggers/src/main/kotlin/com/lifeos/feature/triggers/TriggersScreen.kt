package com.lifeos.feature.triggers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lifeos.core.database.triggers.TriggerRuleEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.FadeThrough
import com.lifeos.feature.triggers.data.TriggerArg
import com.lifeos.feature.triggers.data.TriggerCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Triggers (§Module Triggers): when this happens, do that. Every rule is visible,
 * editable, runnable by hand, and every fire is in the log underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersRoute(viewModel: TriggersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    FadeThrough(
        targetState = when {
            state.draft != null -> "rule"
            state.placeDraft != null -> "place"
            state.tab == 1 -> "places"
            else -> "rules"
        },
        label = "triggers-screen",
    ) { screen ->
        when (screen) {
            "rule" -> RuleEditor(state = state, viewModel = viewModel)
            "place" -> PlaceEditor(state = state, viewModel = viewModel)
            "places" -> PlaceList(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
            else -> RuleList(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleList(
    state: TriggersUiState,
    viewModel: TriggersViewModel,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Triggers") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::newRule) {
                Icon(Icons.Filled.Add, contentDescription = "New rule")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TriggerTabs(state = state, viewModel = viewModel) }
            if (state.rules.isEmpty()) {
                item {
                    EmptyState(
                        title = "No rules yet",
                        description = "A rule is one trigger and one action: arrive home, start a Brick mode; " +
                            "22:00 on weekdays, start Wind-down; a parcel notification, add a task.",
                    )
                }
            }
            items(state.rules, key = { it.id }) { rule ->
                RuleCard(rule = rule, viewModel = viewModel)
            }
            if (state.fires.isNotEmpty()) {
                item {
                    Text(
                        "Fire log",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.fires, key = { "fire-${it.id}" }) { fire ->
                    ListItem(
                        headlineContent = { Text(fire.ruleName) },
                        supportingContent = {
                            Text(
                                listOf(fire.outcome, fire.detail).filter { it.isNotBlank() }.joinToString(" - "),
                            )
                        },
                        trailingContent = {
                            Text(AT.format(Date(fire.at)), style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: TriggerRuleEntity, viewModel: TriggersViewModel) {
    Card {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append("when ${TriggerCatalog.triggerByType[rule.triggerType]?.label ?: rule.triggerType}")
                        if (rule.triggerArg.isNotBlank()) append(" ${rule.triggerArg}")
                        append(" -> ${TriggerCatalog.actionByType[rule.actionType]?.label ?: rule.actionType}")
                        if (rule.actionArg.isNotBlank()) append(" ${rule.actionArg}")
                        if (rule.days.isNotBlank()) append(" - days ${rule.days}")
                        if (rule.fireCount > 0) append(" - fired ${rule.fireCount}x")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = { viewModel.toggle(rule) })
            IconButton(onClick = { viewModel.runNow(rule) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Run now")
            }
            IconButton(onClick = { viewModel.editRule(rule) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { viewModel.delete(rule.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(state: TriggersUiState, viewModel: TriggersViewModel) {
    val draft = state.draft ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeEditor) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = viewModel::saveDraft) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::onName,
                    label = { Text("Rule name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Text("When", style = MaterialTheme.typography.titleSmall) }
            item {
                ChipFlow(
                    options = TriggerCatalog.triggers.map { it.type to it.label },
                    selected = draft.triggerType,
                    onSelect = viewModel::onTriggerType,
                )
            }
            TriggerCatalog.triggerByType[draft.triggerType]?.let { spec ->
                if (spec.arg != TriggerArg.NONE) {
                    item {
                        ArgField(
                            arg = spec.arg,
                            hint = spec.hint,
                            value = draft.triggerArg,
                            places = state.places,
                            onChange = viewModel::onTriggerArg,
                        )
                    }
                }
            }
            item { Text("Then", style = MaterialTheme.typography.titleSmall) }
            item {
                ChipFlow(
                    options = TriggerCatalog.actions.map { it.type to it.label },
                    selected = draft.actionType,
                    onSelect = viewModel::onActionType,
                )
            }
            TriggerCatalog.actionByType[draft.actionType]?.let { spec ->
                if (spec.arg != TriggerArg.NONE) {
                    item {
                        ArgField(
                            arg = spec.arg,
                            hint = spec.hint,
                            value = draft.actionArg,
                            places = state.places,
                            onChange = viewModel::onActionArg,
                        )
                    }
                }
            }
            item { Text("Only on these days", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)
                        .forEach { (label, day) ->
                            FilterChip(
                                selected = draft.days.contains(day),
                                onClick = { viewModel.toggleDay(day) },
                                label = { Text(label) },
                            )
                        }
                }
            }
            item {
                Text(
                    "Leave the days empty for every day. Rules fire at most once a minute, and every fire is " +
                        "written to the log so you can see why something happened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedButton(onClick = viewModel::testDraft) { Text("Save and run once") }
            }
        }
    }
}

@Composable
private fun ChipFlow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ArgField(
    arg: TriggerArg,
    hint: String,
    value: String,
    places: List<Pair<Long, String>>,
    onChange: (String) -> Unit,
) {
    when (arg) {
        TriggerArg.PLACE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (places.isEmpty()) {
                Text(
                    "No places saved yet - add one in Places first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            places.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (id, name) ->
                        FilterChip(
                            selected = value == id.toString(),
                            onClick = { onChange(id.toString()) },
                            label = { Text(name) },
                        )
                    }
                }
            }
        }

        TriggerArg.MINUTE_OF_DAY -> OutlinedTextField(
            value = value,
            onValueChange = { input -> onChange(input.filter { it.isDigit() || it == ':' }) },
            label = { Text("Time, e.g. 22:00") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        TriggerArg.NUMBER, TriggerArg.DURATION -> OutlinedTextField(
            value = value,
            onValueChange = { input -> onChange(input.filter { it.isDigit() }) },
            label = { Text(hint.ifBlank { "Number" }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        else -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(hint.ifBlank { "Value" }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val AT = SimpleDateFormat("EEE HH:mm", Locale.getDefault())

@Composable
private fun TriggerTabs(state: TriggersUiState, viewModel: TriggersViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Rules", "Places").forEachIndexed { index, label ->
            FilterChip(
                selected = state.tab == index,
                onClick = { viewModel.selectTab(index) },
                label = { Text(label) },
            )
        }
        state.here?.let {
            Text(
                "  at $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceList(
    state: TriggersUiState,
    viewModel: TriggersViewModel,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Places") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::newPlace) {
                Icon(Icons.Filled.Add, contentDescription = "New place")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TriggerTabs(state = state, viewModel = viewModel) }
            if (state.placeRows.isEmpty()) {
                item {
                    EmptyState(
                        title = "No places yet",
                        description = "A place is a Wi-Fi name, a coordinate with a radius, or both. " +
                            "Android has no geofence API without Play Services, so LifeOS matches these " +
                            "cheaply instead - Wi-Fi is the most reliable indoors.",
                    )
                }
            }
            items(state.placeRows, key = { it.id }) { place ->
                Card {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    place.wifiSsid?.let { append("wifi $it") }
                                    if (place.latitude != null && place.longitude != null) {
                                        if (isNotEmpty()) append(" - ")
                                        append("${place.latitude}, ${place.longitude} (${place.radiusMeters}m)")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.editPlace(place) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.deletePlace(place.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceEditor(state: TriggersUiState, viewModel: TriggersViewModel) {
    val draft = state.placeDraft ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "New place" else "Edit place") },
                navigationIcon = {
                    IconButton(onClick = viewModel::closePlaceEditor) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = viewModel::savePlace) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> viewModel.updatePlaceDraft { it.copy(name = value) } },
                label = { Text("Name, e.g. Home") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.wifiSsid,
                onValueChange = { value -> viewModel.updatePlaceDraft { it.copy(wifiSsid = value) } },
                label = { Text("Wi-Fi network (most reliable)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.latitude,
                    onValueChange = { value -> viewModel.updatePlaceDraft { it.copy(latitude = value) } },
                    label = { Text("Latitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.longitude,
                    onValueChange = { value -> viewModel.updatePlaceDraft { it.copy(longitude = value) } },
                    label = { Text("Longitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = draft.radiusMeters,
                onValueChange = { value -> viewModel.updatePlaceDraft { it.copy(radiusMeters = value) } },
                label = { Text("Radius in metres") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = viewModel::useHere) { Text("Use where I am now") }
            Text(
                "Matching runs every two minutes and never asks for an active GPS fix, so it costs " +
                    "almost nothing on battery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
