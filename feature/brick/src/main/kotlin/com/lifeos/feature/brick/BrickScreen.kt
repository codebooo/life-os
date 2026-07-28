package com.lifeos.feature.brick

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifeos.core.database.brick.BrickProfileEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.brick.nfc.BrickReader
import com.lifeos.feature.brick.nfc.BrickTagWriter
import com.lifeos.feature.brick.nfc.uid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Brick (§Module Brick): tap-to-block modes. Pick the apps a mode blocks, how
 * it turns on (NFC tag, time window, or by hand) and what it takes to end it.
 * While a mode runs, opening a blocked app lands on a full-screen wall.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrickRoute(viewModel: BrickViewModel = hiltViewModel()) {
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.active.collectAsState()
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()
    val message by viewModel.message.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val pairing by viewModel.pairingTag.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-check the accessibility grant whenever we come back into view.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    // Reader mode stays on the whole time Brick is open: while pairing a tag it
    // captures the id, otherwise a tap flips the matching mode right here.
    // (Outside the app, the manifest's NFC filters route taps to BrickNfcActivity.)
    val appContext = LocalContext.current
    val nfcReady = remember(appContext) {
        android.nfc.NfcAdapter.getDefaultAdapter(appContext)?.isEnabled != false
    }
    BrickReaderEffect(
        onTag = { tag ->
            val uid = tag.uid() ?: return@BrickReaderEffect
            if (viewModel.pairingTag.value) {
                // Pairing also programs the tag, which is what makes taps work
                // with LifeOS closed.
                viewModel.onTagPaired(uid, BrickTagWriter.write(tag, uid, appContext.packageName))
            } else {
                viewModel.onTagTapped(uid)
            }
        },
    )

    if (draft != null) {
        ProfileEditor(viewModel = viewModel, snackbarHostState = snackbarHostState)
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Brick") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.newProfile() }) {
                Icon(Icons.Filled.Add, contentDescription = "New mode")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!serviceEnabled) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Blocking needs one grant", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Enable \"LifeOS Brick\" in accessibility settings. It only reads which app is in " +
                                    "front — never screen content — and that is how Android lets an app block others.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { viewModel.openAccessibilitySettings() }) {
                                Text("Open accessibility settings")
                            }
                        }
                    }
                }
            }

            if (!nfcReady) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("NFC is off", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Turn NFC on to pair a tag and to flip modes by tapping it. Android only " +
                                    "dispatches tags while the screen is unlocked, so a tap wakes nothing " +
                                    "from a locked phone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    runCatching {
                                        appContext.startActivity(
                                            Intent(android.provider.Settings.ACTION_NFC_SETTINGS),
                                        )
                                    }
                                },
                            ) { Text("Open NFC settings") }
                        }
                    }
                }
            }

            active?.let { mode ->
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Blocking now", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(mode.profile.name, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${mode.blockedPackages.size} app(s) blocked · since ${TIME.format(Date(mode.session.startedAt))} " +
                                    "· ${mode.session.blockedAttempts} attempt(s) stopped",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                when (mode.profile.deactivator) {
                                    "NFC" -> "Ends when you scan the paired tag"
                                    "TIME" -> "Ends at ${formatMinute(mode.profile.endMinuteOfDay)}"
                                    else -> if (mode.profile.strict) "Strict — cannot be ended early" else "Can be ended here"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = { viewModel.stopNow() }) { Text("End mode") }
                        }
                    }
                }
            }

            if (profiles.isEmpty()) {
                item {
                    EmptyState(
                        title = "No modes yet",
                        description = "Create a mode, pick the apps it blocks, and pair an NFC tag you keep far away — " +
                            "then a tap is the only way in or out.",
                    )
                }
            } else {
                item { Text("Modes", style = MaterialTheme.typography.titleMedium) }
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isActive = active?.profile?.id == profile.id,
                        onStart = { viewModel.startNow(profile) },
                        onEdit = { viewModel.editProfile(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: BrickProfileEntity,
    isActive: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append("${profile.blockedPackages.lines().count { it.isNotBlank() }} app(s)")
                        append(" · on: ${activatorLabel(profile.activator, profile.startMinuteOfDay)}")
                        append(" · off: ${activatorLabel(profile.deactivator, profile.endMinuteOfDay)}")
                        if (profile.strict) append(" · strict")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isActive) {
                IconButton(onClick = onStart) { Icon(Icons.Filled.PlayArrow, contentDescription = "Start now") }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete, enabled = !isActive) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(viewModel: BrickViewModel, snackbarHostState: SnackbarHostState) {
    val draft by viewModel.draft.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val pairing by viewModel.pairingTag.collectAsState()
    val current = draft ?: return
    val nfcSettingsContext = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (current.id == 0L) "New mode" else "Edit mode") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeEditor() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = { viewModel.saveDraft() }) { Text("Save") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = current.name,
                    onValueChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
                    label = { Text("Mode name, e.g. No socials") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { Text("Turns on with", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MANUAL" to "By hand", "NFC" to "NFC tag", "TIME" to "Time").forEach { (value, label) ->
                        FilterChip(
                            selected = current.activator == value,
                            onClick = { viewModel.updateDraft { it.copy(activator = value) } },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item { Text("Turns off with", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MANUAL" to "By hand", "NFC" to "NFC tag", "TIME" to "Time").forEach { (value, label) ->
                        FilterChip(
                            selected = current.deactivator == value,
                            onClick = { viewModel.updateDraft { it.copy(deactivator = value) } },
                            label = { Text(label) },
                        )
                    }
                }
            }

            if (current.activator == "NFC" || current.deactivator == "NFC") {
                item {
                    Card {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    "  " + (current.nfcTagId?.let { "Tag paired: $it" } ?: "No tag paired"),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                if (pairing) {
                                    "Hold the tag against the back of the phone. LifeOS also writes a " +
                                        "small record onto it so taps work with the app closed."
                                } else {
                                    "Any writable NFC tag works. Pairing programs the tag, so a tap flips " +
                                        "this mode from anywhere - no need to open LifeOS first. The screen " +
                                        "does have to be unlocked; Android never dispatches tags while locked."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.startTagPairing() }, enabled = !pairing) {
                                    Text(if (current.nfcTagId == null) "Pair tag" else "Pair a different tag")
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            nfcSettingsContext.startActivity(
                                                Intent(android.provider.Settings.ACTION_NFC_SETTINGS),
                                            )
                                        }
                                    },
                                ) { Text("NFC settings") }
                            }
                        }
                    }
                }
            }

            if (current.activator == "TIME") {
                item {
                    MinuteField(
                        label = "Starts at",
                        minuteOfDay = current.startMinuteOfDay,
                        onChange = { value -> viewModel.updateDraft { it.copy(startMinuteOfDay = value) } },
                    )
                }
            }
            if (current.deactivator == "TIME") {
                item {
                    MinuteField(
                        label = "Ends at",
                        minuteOfDay = current.endMinuteOfDay,
                        onChange = { value -> viewModel.updateDraft { it.copy(endMinuteOfDay = value) } },
                    )
                }
            }

            item {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Strict mode") },
                        supportingContent = { Text("Hides the in-app end button — only the real condition unlocks it") },
                        trailingContent = {
                            Switch(
                                checked = current.strict,
                                onCheckedChange = { value -> viewModel.updateDraft { it.copy(strict = value) } },
                            )
                        },
                    )
                }
            }

            item { HorizontalDivider() }
            item {
                Text(
                    "Blocked apps (${current.blocked.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showAllApps, onCheckedChange = { showAllApps = it })
                    Text("Include system apps", style = MaterialTheme.typography.bodySmall)
                }
            }

            val shown = apps
                .filter { showAllApps || !it.system }
                .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in current.blocked
                val limit = current.limits[app.packageName]
                Column {
                    Surface(
                        onClick = {
                            viewModel.updateDraft { draftState ->
                                val blocked = if (checked) draftState.blocked - app.packageName
                                else draftState.blocked + app.packageName
                                draftState.copy(
                                    blocked = blocked,
                                    limits = if (checked) draftState.limits - app.packageName else draftState.limits,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    if (limit != null) "Allowance: $limit min/day" else app.packageName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        )
                    }
                    if (checked) {
                        Row(
                            modifier = Modifier.padding(start = 56.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Limit:", style = MaterialTheme.typography.bodySmall)
                            listOf(null, 15, 30, 60).forEach { minutes ->
                                FilterChip(
                                    selected = limit == minutes,
                                    onClick = {
                                        viewModel.updateDraft { draftState ->
                                            draftState.copy(
                                                limits = if (minutes == null) {
                                                    draftState.limits - app.packageName
                                                } else {
                                                    draftState.limits + (app.packageName to minutes)
                                                },
                                            )
                                        }
                                    },
                                    label = { Text(minutes?.let { "${it}m" } ?: "Block") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinuteField(label: String, minuteOfDay: Int?, onChange: (Int?) -> Unit) {
    var hours by remember(minuteOfDay) { mutableStateOf(minuteOfDay?.let { (it / 60).toString() } ?: "") }
    var minutes by remember(minuteOfDay) { mutableStateOf(minuteOfDay?.let { "%02d".format(it % 60) } ?: "") }

    fun push() {
        val h = hours.toIntOrNull()
        val m = minutes.toIntOrNull() ?: 0
        onChange(if (h == null) null else (h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59)))
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.padding(end = 4.dp))
        OutlinedTextField(
            value = hours,
            onValueChange = { hours = it.filter(Char::isDigit).take(2); push() },
            label = { Text("HH") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            modifier = Modifier.width(84.dp),
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter(Char::isDigit).take(2); push() },
            label = { Text("MM") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            modifier = Modifier.width(84.dp),
        )
    }
}

/**
 * Keeps NFC reader mode running while the Brick screen is on top, and stops it
 * when the screen goes away so the system's normal tag dispatch takes over again.
 */
@Composable
private fun BrickReaderEffect(onTag: (android.nfc.Tag) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> BrickReader.startForTag(activity) { tag -> onTag(tag); true }
                Lifecycle.Event.ON_PAUSE -> BrickReader.stop(activity)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            BrickReader.stop(activity)
        }
    }
}

private fun activatorLabel(kind: String, minuteOfDay: Int?): String = when (kind) {
    "NFC" -> "tag"
    "TIME" -> formatMinute(minuteOfDay)
    else -> "hand"
}

private fun formatMinute(minuteOfDay: Int?): String =
    minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--"

private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())
