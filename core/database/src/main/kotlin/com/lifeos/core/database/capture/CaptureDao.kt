package com.lifeos.core.database.capture

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert
    suspend fun insertCapture(capture: CaptureEntity): Long

    @Query("UPDATE captures SET routedTo = :routedTo, routedEntityId = :routedEntityId WHERE id = :captureId")
    suspend fun markRouted(captureId: Long, routedTo: String, routedEntityId: Long?)

    @Query("SELECT * FROM captures ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<CaptureEntity>>

    @Insert
    suspend fun insertForm(form: LogFormEntity): Long

    @Query("SELECT * FROM log_forms ORDER BY name")
    fun observeForms(): Flow<List<LogFormEntity>>

    @Query("SELECT * FROM log_forms WHERE id = :formId")
    suspend fun getForm(formId: Long): LogFormEntity?

    @Query("SELECT * FROM log_forms WHERE name = :name LIMIT 1")
    suspend fun getFormByName(name: String): LogFormEntity?

    @Query("DELETE FROM log_forms WHERE id = :formId")
    suspend fun deleteForm(formId: Long)

    @Insert
    suspend fun insertEntry(entry: LogEntryEntity): Long

    @Query("SELECT * FROM log_entries WHERE formId = :formId ORDER BY at DESC")
    fun observeEntries(formId: Long): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Query("SELECT * FROM tasks ORDER BY done ASC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    /** Time-stamped to-dos overlap this window — surfaced in the calendar (§Module 19). */
    @Query("SELECT * FROM tasks WHERE dueAt IS NOT NULL AND dueAt >= :start AND dueAt < :end ORDER BY dueAt")
    fun observeTimedTasks(start: Long, end: Long): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET done = :done WHERE id = :taskId")
    suspend fun setTaskDone(taskId: Long, done: Boolean)
}
