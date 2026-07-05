package com.lifeos.core.database.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insertConversation(conversation: AiConversationEntity): Long

    @Insert
    suspend fun insertMessage(message: AiMessageEntity): Long

    @Query("UPDATE ai_messages SET content = :content, engine = :engine WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String, engine: String?)

    @Query("UPDATE ai_conversations SET updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: Long, updatedAt: Long)

    @Query("SELECT * FROM ai_conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<AiConversationEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(conversationId: Long): List<AiMessageEntity>

    @Query("DELETE FROM ai_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)
}
