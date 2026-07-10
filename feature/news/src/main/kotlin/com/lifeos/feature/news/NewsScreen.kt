package com.lifeos.feature.news

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.news.data.NewsArticle
import com.lifeos.feature.news.data.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    val sources = repository.sources

    private val _enabled = MutableStateFlow(repository.sources.map { it.id }.toSet())
    val enabled = _enabled.asStateFlow()
    private val _articles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val articles = _articles.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun toggle(id: String) {
        _enabled.value = if (id in _enabled.value) _enabled.value - id else _enabled.value + id
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _articles.value = repository.latest(_enabled.value)
            _loading.value = false
        }
    }
}

/** News (§Module News): a tile scroll of the latest headlines, straight RSS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsRoute(viewModel: NewsViewModel = hiltViewModel()) {
    val articles by viewModel.articles.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (articles.isEmpty()) viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(viewModel.sources, key = { it.id }) { source ->
                    FilterChip(
                        selected = source.id in enabled,
                        onClick = { viewModel.toggle(source.id) },
                        label = { Text(source.name) },
                    )
                }
            }

            if (loading && articles.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else if (articles.isEmpty()) {
                EmptyState(
                    title = "No headlines",
                    description = "Check the connection and pull the refresh button — feeds load straight from the outlets.",
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(articles, key = { it.link }) { article ->
                        Card(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                        }) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        article.source,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (article.publishedAt > 0) {
                                        Text(
                                            TIME.format(Date(article.publishedAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(article.title, style = MaterialTheme.typography.titleMedium)
                                if (article.summary.isNotBlank()) {
                                    Text(
                                        article.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TIME = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
