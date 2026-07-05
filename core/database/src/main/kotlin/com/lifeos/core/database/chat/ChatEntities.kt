package com.lifeos.core.database.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A chat thread with the assistant (§Module 18). */
@Entity(tableName = "ai_conversations")
data class AiConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One message in a conversation. [engine] records which engine produced an
 * assistant message (provenance, §7.2); null for user messages.
 */
@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val engine: String?,
    val createdAt: Long,
)
