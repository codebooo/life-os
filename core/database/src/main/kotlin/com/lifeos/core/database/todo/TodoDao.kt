package com.lifeos.core.database.todo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lifeos.core.database.capture.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Insert
    suspend fun insertList(list: TaskListEntity): Long

    @Query("SELECT * FROM task_lists ORDER BY position, createdAt")
    fun observeLists(): Flow<List<TaskListEntity>>

    @Query("DELETE FROM task_lists WHERE id = :listId")
    suspend fun deleteList(listId: Long)

    @Query("UPDATE tasks SET listId = NULL WHERE listId = :listId")
    suspend fun orphanTasksOf(listId: Long)

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE listId = :listId OR (:listId IS NULL AND listId IS NULL) ORDER BY done ASC, createdAt DESC")
    fun observeTasks(listId: Long?): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE (listId = :listId OR (:listId IS NULL AND listId IS NULL)) AND done = 0")
    fun observeOpenCount(listId: Long?): Flow<Int>

    /** The single most urgent open task (overwhelm overlay's "What's next?"). */
    @Query(
        "SELECT * FROM tasks WHERE done = 0 " +
            "ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, createdAt ASC LIMIT 1",
    )
    suspend fun nextOpenTask(): TaskEntity?

    @Query("UPDATE tasks SET done = :done WHERE id = :taskId")
    suspend fun setDone(taskId: Long, done: Boolean)

    @Query("DELETE FROM tasks WHERE id = :taskId OR parentId = :taskId")
    suspend fun deleteWithChildren(taskId: Long)
}
