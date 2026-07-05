package com.lifeos.core.database.todo

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A named task list (§Module 3). Tasks with no list live in the implicit Inbox. */
@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
    val createdAt: Long,
)
