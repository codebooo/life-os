package com.lifeos.feature.capture

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.capture.data.CaptureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Interim task list fed by the capture spine. The full to-do module
 * (nesting, lists, cross-links) replaces this surface in Phase 3.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
) : ViewModel() {

    val tasks = captureRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<TaskEntity>())

    fun setDone(taskId: Long, done: Boolean) {
        viewModelScope.launch { captureRepository.setTaskDone(taskId, done) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksRoute(viewModel: TasksViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Tasks") }) }) { innerPadding ->
        if (tasks.isEmpty()) {
            EmptyState(
                title = "Nothing to do",
                description = "Tasks land here from quick capture — nested lists arrive in Phase 3.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
                items(tasks, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = {
                            Text(
                                task.title,
                                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                            )
                        },
                        leadingContent = {
                            Checkbox(
                                checked = task.done,
                                onCheckedChange = { viewModel.setDone(task.id, it) },
                            )
                        },
                    )
                }
            }
        }
    }
}
