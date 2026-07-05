package com.lifeos.core.database.email

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A fetched mail header + preview (§Module 1). Bodies stay on the server. */
@Entity(
    tableName = "email_messages",
    indices = [Index(value = ["messageUid"], unique = true)],
)
data class EmailMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageUid: String,
    val from: String,
    val subject: String,
    val preview: String,
    val receivedAt: Long,
    val hasInvoiceSignal: Boolean,
    val hasInviteSignal: Boolean,
    val hasSubscriptionSignal: Boolean,
)

@Dao
interface EmailDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(email: EmailMessageEntity): Long

    @Query("SELECT * FROM email_messages ORDER BY receivedAt DESC LIMIT 200")
    fun observeAll(): Flow<List<EmailMessageEntity>>

    @Query("DELETE FROM email_messages WHERE id = :id")
    suspend fun delete(id: Long)
}
