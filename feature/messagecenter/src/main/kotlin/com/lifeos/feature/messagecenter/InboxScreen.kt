package com.lifeos.feature.messagecenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.database.messages.MessageDao
import com.lifeos.core.database.messages.UnifiedMessageEntity
import com.lifeos.core.designsystem.component.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val messageDao: MessageDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val messages = _query
        .flatMapLatest { q -> if (q.isBlank()) messageDao.observeAll() else messageDao.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<UnifiedMessageEntity>())

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    fun delete(id: Long) {
        viewModelScope.launch { messageDao.delete(id) }
    }
}

/** Unified notification inbox (§Module 7). Email joins this tab in Phase 8. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxRoute(viewModel: InboxViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Inbox") }) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text("Search messages") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (messages.isEmpty()) {
                EmptyState(
                    title = "No messages yet",
                    description = "Grant notification access in system settings so LifeOS can unify your notifications — everything stays on this device.",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { message ->
                        ListItem(
                            overlineContent = {
                                Text(
                                    message.appLabel + " · " +
                                        DateFormat.getTimeInstance(DateFormat.SHORT)
                                            .format(Date(message.postedAt)),
                                )
                            },
                            headlineContent = {
                                Text(
                                    message.title ?: "(no title)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                message.text?.let {
                                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.delete(message.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
