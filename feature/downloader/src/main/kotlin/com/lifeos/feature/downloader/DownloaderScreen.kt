package com.lifeos.feature.downloader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.lifeos.core.designsystem.component.FadeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.downloads.DownloadDao
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.downloader.data.DownloadEngine
import com.lifeos.feature.downloader.data.MediaCandidate
import com.lifeos.feature.downloader.data.MediaResolver
import com.lifeos.feature.downloader.data.SiteCatalog
import com.lifeos.feature.downloader.data.SupportedSite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloaderViewModel @Inject constructor(
    private val resolver: MediaResolver,
    private val engine: DownloadEngine,
    private val downloadDao: DownloadDao,
) : ViewModel() {

    val downloads = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _url = MutableStateFlow("")
    val url = _url.asStateFlow()
    private val _candidates = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val candidates = _candidates.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun onUrl(value: String) { _url.value = value }

    fun scan() {
        val target = _url.value.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            _candidates.value = emptyList()
            val result = runCatching { resolver.resolve(target) }
            _busy.value = false
            result.onSuccess { outcome ->
                _candidates.value = outcome.candidates
                _message.value = when {
                    outcome.candidates.isEmpty() ->
                        "No downloadable media found on that page."
                    outcome.usedPlayer ->
                        "Resolved with the in-app player - links are tied to this session, " +
                            "so download them now."
                    else -> null
                }
            }.onFailure { _message.value = it.message ?: "Couldn't reach that URL." }
        }
    }

    fun download(candidate: MediaCandidate) {
        engine.enqueue(candidate, _url.value.trim())
        _message.value = "Queued: ${candidate.title}"
    }

    fun delete(id: Long) {
        viewModelScope.launch { downloadDao.delete(id) }
    }

    private val _sourceQuery = MutableStateFlow("")
    val sourceQuery = _sourceQuery.asStateFlow()
    private val _showSources = MutableStateFlow(false)
    val showSources = _showSources.asStateFlow()

    fun onSourceQuery(value: String) { _sourceQuery.value = value }
    fun toggleSources() { _showSources.value = !_showSources.value }

    fun sources(): List<SupportedSite> = SiteCatalog.search(_sourceQuery.value)
}

/**
 * Downloader (§Module Downloader): paste any page or media URL, LifeOS finds
 * the direct video/audio streams on-device (OpenGraph, <video> tags, raw
 * links, HLS) and saves them to Downloads — private, no accounts, no cloud.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderRoute(viewModel: DownloaderViewModel = hiltViewModel()) {
    val url by viewModel.url.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    val showSources by viewModel.showSources.collectAsState()
    val sourceQuery by viewModel.sourceQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloader") },
                actions = {
                    IconButton(onClick = viewModel::toggleSources) {
                        Icon(Icons.Filled.Public, contentDescription = "Supported sites")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = viewModel::onUrl,
                    label = { Text("Video/audio page or file URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = viewModel::scan, enabled = !busy) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                    else Text("Scan")
                }
            }
            FadeVisible(visible = message != null) {
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (showSources) {
                    item {
                        OutlinedTextField(
                            value = sourceQuery,
                            onValueChange = viewModel::onSourceQuery,
                            label = { Text("Search ${SiteCatalog.sites.size} supported sites") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    val matches = viewModel.sources()
                    SiteCatalog.categories.forEach { category ->
                        val inCategory = matches.filter { it.category == category }
                        if (inCategory.isEmpty()) return@forEach
                        item(key = "cat-$category") {
                            Text(
                                category,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(inCategory, key = { "site-${it.host}-${it.name}" }) { site ->
                            SiteRow(site) { viewModel.onUrl("https://${it.host}/") }
                        }
                    }
                }
                items(candidates, key = { it.url }) { candidate ->
                    ListItem(
                        headlineContent = { Text(candidate.title, maxLines = 1) },
                        supportingContent = {
                            Text("${candidate.kind} · ${candidate.url.take(64)}", maxLines = 1)
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.download(candidate) }) {
                                Icon(Icons.Filled.Download, contentDescription = "Download")
                            }
                        },
                    )
                }
                if (downloads.isNotEmpty()) {
                    item {
                        Text(
                            "Downloads",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(downloads, key = { "dl-${it.id}" }) { download ->
                    Column {
                        ListItem(
                            headlineContent = { Text(download.title, maxLines = 1) },
                            supportingContent = {
                                Text(
                                    when (download.status) {
                                        "DONE" -> "Saved to Downloads · ${download.sizeBytes / 1_048_576} MB"
                                        "FAILED" -> "Failed: ${download.error ?: "unknown"}"
                                        else -> "${download.status.lowercase()} ${download.progressPercent}%"
                                    },
                                    maxLines = 1,
                                )
                            },
                            leadingContent = {
                                if (download.status == "DONE" && download.savedUri != null) {
                                    IconButton(onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(download.savedUri), download.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                        )
                                    }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play") }
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.delete(download.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                }
                            },
                        )
                        if (download.status == "RUNNING") {
                            LinearProgressIndicator(
                                progress = { download.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                if (candidates.isEmpty() && downloads.isEmpty() && !showSources) {
                    item {
                        EmptyState(
                            title = "Nothing here yet",
                            description = "Paste a link from YouTube, Vimeo, TikTok, X, Instagram, Threads, " +
                                "LinkedIn, Reddit, ThisVid or any page with media - LifeOS finds the stream " +
                                "on-device and saves it to Downloads. The globe icon lists every supported site.",
                        )
                    }
                }
            }
        }
    }
}

/** One catalogue row: tapping it seeds the URL field with that site. */
@Composable
private fun SiteRow(site: SupportedSite, onPick: (SupportedSite) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onPick(site) },
        headlineContent = { Text(site.name) },
        supportingContent = { Text("${site.host} - ${site.note}", maxLines = 2) },
    )
}
