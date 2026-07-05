package com.lifeos.core.database.messages

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One captured notification message (§Module 7). */
@Entity(
    tableName = "unified_messages",
    indices = [Index("appPackage"), Index(value = ["notificationKey", "postedAt"], unique = true)],
)
data class UnifiedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appPackage: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val notificationKey: String,
    val postedAt: Long,
)

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: UnifiedMessageEntity): Long

    @Query("SELECT * FROM unified_messages ORDER BY postedAt DESC LIMIT 500")
    fun observeAll(): Flow<List<UnifiedMessageEntity>>

    @Query(
        "SELECT * FROM unified_messages WHERE title LIKE '%' || :query || '%' " +
            "OR text LIKE '%' || :query || '%' OR appLabel LIKE '%' || :query || '%' " +
            "ORDER BY postedAt DESC LIMIT 500",
    )
    fun search(query: String): Flow<List<UnifiedMessageEntity>>

    @Query("DELETE FROM unified_messages WHERE id = :id")
    suspend fun delete(id: Long)
}
