package com.lifeos.feature.signals

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.signals.SignalDao
import com.lifeos.core.database.signals.SignalEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.FadeThrough
import com.lifeos.feature.signals.data.SignalDigest
import com.lifeos.feature.signals.data.SignalListenerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SignalsViewModel @Inject constructor(
    private val signalDao: SignalDao,
    private val digest: SignalDigest,
) : ViewModel() {

    val signals = signalDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _digestText = MutableStateFlow<String?>(null)
    val digestText = _digestText.asStateFlow()

    private val _filter = MutableStateFlow("ALL")
    val filter = _filter.asStateFlow()

    fun setFilter(value: String) { _filter.value = value }

    fun buildDigest(hours: Int = 12) {
        viewModelScope.launch { _digestText.value = digest.build(hours) }
    }

    fun markAllRead() {
        viewModelScope.launch { signalDao.markAllRead(System.currentTimeMillis()) }
    }

    fun clear() {
        viewModelScope.launch {
            signalDao.clear()
            _digestText.value = null
        }
    }
}

/**
 * Signals (§Module Signals): every notification LifeOS captured, grouped into a
 * digest so "what did I miss" is one screen instead of a scroll of interruptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalsRoute(viewModel: SignalsViewModel = hiltViewModel()) {
    val signals by viewModel.signals.collectAsState()
    val digest by viewModel.digestText.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val context = LocalContext.current
    val granted = SignalListenerService.isGranted(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signals") },
                actions = {
                    IconButton(onClick = viewModel::markAllRead) {
                        Icon(Icons.Filled.DoneAll, contentDescription = "Mark all read")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!granted) {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notification access needed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "LifeOS can only see notifications after you allow it once. Nothing is uploaded; " +
                                "rows older than 30 days are deleted automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                                    )
                                }
                            },
                        ) { Text("Open notification access") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "CODE" to "Codes", "PARCEL" to "Parcels", "RECEIPT" to "Money")
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = filter == value,
                            onClick = { viewModel.setFilter(value) },
                            label = { Text(label) },
                        )
                    }
                Button(onClick = { viewModel.buildDigest() }) { Text("Digest") }
            }
            FadeThrough(targetState = digest, label = "signals-digest") { text ->
                if (text != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val visible = signals.filter { filter == "ALL" || it.extracted == filter }
            if (visible.isEmpty()) {
                EmptyState(
                    title = if (granted) "Nothing captured yet" else "Waiting for access",
                    description = "Captured notifications land here, tagged when they look like a code, " +
                        "a parcel update or something you paid for.",
                )
            } else {
                LazyColumn {
                    items(visible, key = { it.id }) { signal -> SignalRow(signal) }
                }
            }
        }
    }
}

@Composable
private fun SignalRow(signal: SignalEntity) {
    ListItem(
        headlineContent = { Text(signal.title.ifBlank { signal.appLabel }, maxLines = 1) },
        supportingContent = { Text(signal.text.take(140), maxLines = 2) },
        trailingContent = {
            Text(
                TIME.format(Date(signal.postedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        overlineContent = {
            Text(
                if (signal.extracted == "NONE") signal.appLabel else "${signal.appLabel} - ${signal.extracted}",
                style = MaterialTheme.typography.labelSmall,
            )
        },
    )
}

private val TIME = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
