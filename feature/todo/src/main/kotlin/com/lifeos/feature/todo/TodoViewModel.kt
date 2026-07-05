package com.lifeos.feature.todo

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.database.todo.TaskListEntity
import com.lifeos.core.database.todo.TodoDao
import com.lifeos.core.model.LifeModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A task with its resolved children (one-level nesting for now, §Module 3). */
data class TaskNode(
    val task: TaskEntity,
    val children: List<TaskEntity>,
)

data class TodoUiState(
    val lists: List<TaskListEntity> = emptyList(),
    /** null = Inbox (captures land here). */
    val activeListId: Long? = null,
    val nodes: List<TaskNode> = emptyList(),
    val newTaskTitle: String = "",
    val newListName: String = "",
    val showNewList: Boolean = false,
    /** When set, the inline add creates a subtask of this task. */
    val addingSubtaskOf: Long? = null,
)

sealed interface TodoUiEvent {
    data class SelectList(val listId: Long?) : TodoUiEvent
    data class NewTaskTitleChanged(val value: String) : TodoUiEvent
    data object AddTask : TodoUiEvent
    data class SetDone(val taskId: Long, val done: Boolean) : TodoUiEvent
    data class DeleteTask(val taskId: Long) : TodoUiEvent
    data class StartSubtask(val parentId: Long?) : TodoUiEvent
    data object ToggleNewList : TodoUiEvent
    data class NewListNameChanged(val value: String) : TodoUiEvent
    data object AddList : TodoUiEvent
    data class DeleteList(val listId: Long) : TodoUiEvent
}

sealed interface TodoUiEffect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoDao: TodoDao,
) : LifeViewModel<TodoUiState, TodoUiEvent, TodoUiEffect>(TodoUiState()) {

    private val activeListId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            todoDao.observeLists().collect { lists -> updateState { it.copy(lists = lists) } }
        }
        viewModelScope.launch {
            activeListId
                .flatMapLatest { todoDao.observeTasks(it) }
                .collect { tasks -> updateState { it.copy(nodes = buildNodes(tasks)) } }
        }
    }

    override fun onEvent(event: TodoUiEvent) {
        when (event) {
            is TodoUiEvent.SelectList -> {
                activeListId.value = event.listId
                updateState { it.copy(activeListId = event.listId, addingSubtaskOf = null) }
            }
            is TodoUiEvent.NewTaskTitleChanged -> updateState { it.copy(newTaskTitle = event.value) }
            TodoUiEvent.AddTask -> addTask()
            is TodoUiEvent.SetDone -> viewModelScope.launch {
                todoDao.setDone(event.taskId, event.done)
            }
            is TodoUiEvent.DeleteTask -> viewModelScope.launch {
                todoDao.deleteWithChildren(event.taskId)
            }
            is TodoUiEvent.StartSubtask ->
                updateState { it.copy(addingSubtaskOf = event.parentId) }
            TodoUiEvent.ToggleNewList ->
                updateState { it.copy(showNewList = !it.showNewList, newListName = "") }
            is TodoUiEvent.NewListNameChanged -> updateState { it.copy(newListName = event.value) }
            TodoUiEvent.AddList -> addList()
            is TodoUiEvent.DeleteList -> viewModelScope.launch {
                todoDao.orphanTasksOf(event.listId)
                todoDao.deleteList(event.listId)
                if (uiState.value.activeListId == event.listId) {
                    onEvent(TodoUiEvent.SelectList(null))
                }
            }
        }
    }

    private fun addTask() {
        val state = uiState.value
        val title = state.newTaskTitle.trim()
        if (title.isEmpty()) return
        viewModelScope.launch {
            todoDao.insertTask(
                TaskEntity(
                    title = title,
                    listId = state.activeListId,
                    parentId = state.addingSubtaskOf,
                    sourceModule = LifeModule.TODO.name,
                    sourceEntityId = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            updateState { it.copy(newTaskTitle = "", addingSubtaskOf = null) }
        }
    }

    private fun addList() {
        val name = uiState.value.newListName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val id = todoDao.insertList(
                TaskListEntity(name = name, createdAt = System.currentTimeMillis()),
            )
            updateState { it.copy(showNewList = false, newListName = "") }
            onEvent(TodoUiEvent.SelectList(id))
        }
    }

    companion object {
        fun buildNodes(tasks: List<TaskEntity>): List<TaskNode> {
            val byParent = tasks.groupBy { it.parentId }
            return byParent[null].orEmpty().map { parent ->
                TaskNode(task = parent, children = byParent[parent.id].orEmpty())
            }
        }
    }
}
