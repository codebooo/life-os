package com.lifeos.core.database.capture

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A raw capture from the quick-capture spine (§Module 20, [src 11,16]).
 * [routedTo] records the confirmed destination (NOTE/TASK/LOG/…), null while
 * the suggestion is pending.
 */
@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val text: String?,
    val blobVaultRef: String?,
    val routedTo: String?,
    val routedEntityId: Long?,
    val createdAt: Long,
)

/** A user-defined logging form (§Module 20, [src 11]); fields are typed JSON. */
@Entity(tableName = "log_forms")
data class LogFormEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** JSON array of {name, type, unit?} — types: number/text/boolean/rating/duration. */
    val fieldsJson: String,
    val color: Int?,
    val createdAt: Long,
)

@Entity(
    tableName = "log_entries",
    foreignKeys = [
        ForeignKey(
            entity = LogFormEntity::class,
            parentColumns = ["id"],
            childColumns = ["formId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("formId")],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val formId: Long,
    /** JSON object of fieldName → value. */
    val valuesJson: String,
    val source: String,
    val at: Long,
)

/**
 * A task (§Module 3). [listId] null = the implicit Inbox (where quick
 * captures land); [parentId] enables one-level-plus nesting.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    val listId: Long? = null,
    val parentId: Long? = null,
    val dueAt: Long? = null,
    val sourceModule: String?,
    val sourceEntityId: Long?,
    val createdAt: Long,
)
