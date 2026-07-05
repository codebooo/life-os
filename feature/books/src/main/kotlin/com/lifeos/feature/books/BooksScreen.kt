package com.lifeos.feature.books

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.SectionHeader

/** Private book tracker (§Module 16, [src 45,46]): shelves, half-stars, on-device recs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksRoute(viewModel: BooksViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(BooksUiEvent.DismissMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Books") },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(BooksUiEvent.Recommend) }) {
                        Icon(Icons.Filled.Psychology, contentDescription = "What to read next")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = uiState.search,
                onValueChange = { viewModel.onEvent(BooksUiEvent.SearchChanged(it)) },
                placeholder = { Text("Search Open Library (title or ISBN)") },
                trailingIcon = {
                    if (uiState.searching) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (uiState.searchResults.isNotEmpty()) {
                SectionHeader(title = "Results")
                uiState.searchResults.take(4).forEach { result ->
                    ListItem(
                        headlineContent = { Text(result.title, maxLines = 1) },
                        supportingContent = { Text(result.author, maxLines = 1) },
                        trailingContent = {
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.onEvent(BooksUiEvent.AddBook(result)) },
                                label = { Text("Want") },
                            )
                        },
                    )
                }
            }

            uiState.recommendation?.let { rec ->
                Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("What to read next", style = MaterialTheme.typography.titleSmall)
                        Text(rec, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(listOf("ALL", "WANT", "READING", "READ")) { shelf ->
                    FilterChip(
                        selected = uiState.shelf == shelf,
                        onClick = { viewModel.onEvent(BooksUiEvent.SelectShelf(shelf)) },
                        label = { Text(shelf.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            val books = uiState.books.filter { uiState.shelf == "ALL" || it.status == uiState.shelf }
            if (books.isEmpty()) {
                EmptyState(
                    title = "Empty shelf",
                    description = "Ad-free, Amazon-free, yours forever — add books via search above.",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(books, key = { it.id }) { book ->
                        ListItem(
                            headlineContent = { Text(book.title, maxLines = 1) },
                            supportingContent = {
                                Column {
                                    Text(book.author, maxLines = 1)
                                    Row {
                                        (1..5).forEach { star ->
                                            val rating = book.ratingHalfStars ?: 0
                                            Icon(
                                                when {
                                                    rating >= star * 2 -> Icons.Filled.Star
                                                    rating == star * 2 - 1 -> Icons.Filled.StarHalf
                                                    else -> Icons.Outlined.StarOutline
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(end = 2.dp)
                                                    .clickableHalfStar(star) { half ->
                                                        viewModel.onEvent(BooksUiEvent.Rate(book.id, half))
                                                    },
                                            )
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row {
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.onEvent(BooksUiEvent.CycleStatus(book.id, book.status)) },
                                        label = { Text(book.status.lowercase()) },
                                    )
                                    IconButton(onClick = { viewModel.onEvent(BooksUiEvent.Delete(book.id)) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Tap = full star (star*2 half-stars); half-star granularity via repeat taps. */
private fun Modifier.clickableHalfStar(star: Int, onRate: (Int) -> Unit): Modifier =
    clickable { onRate(star * 2) }
