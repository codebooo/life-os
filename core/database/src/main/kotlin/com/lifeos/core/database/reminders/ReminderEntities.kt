package com.lifeos.core.database.reminders

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An exact-time reminder (§Module 2). */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String?,
    /** Epoch millis of the (next) fire time. */
    val at: Long,
    /** NONE, DAILY, WEEKLY, MONTHLY — RRULE support widens later. */
    val recurrence: String = "NONE",
    val enabled: Boolean = true,
    val firedAt: Long? = null,
    val sourceModule: String? = null,
    val sourceEntityId: Long? = null,
    val createdAt: Long,
)
