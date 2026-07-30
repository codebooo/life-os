package com.lifeos.core.database.signals

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One captured notification (§Module Signals). */
@Entity(tableName = "signals", indices = [Index("postedAt"), Index("appPackage")])
data class SignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appPackage: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** Set when the user has seen it in the digest. */
    val readAt: Long? = null,
    /** What LifeOS did with it: NONE, PARCEL, CODE, RECEIPT. */
    val extracted: String = "NONE",
)

@Dao
interface SignalDao {

    @Insert
    suspend fun insert(signal: SignalEntity): Long

    @Query("SELECT * FROM signals ORDER BY postedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE postedAt >= :since ORDER BY postedAt DESC")
    suspend fun since(since: Long): List<SignalEntity>

    @Query("SELECT * FROM signals WHERE readAt IS NULL ORDER BY postedAt DESC LIMIT :limit")
    suspend fun unread(limit: Int): List<SignalEntity>

    @Query("UPDATE signals SET readAt = :at WHERE readAt IS NULL")
    suspend fun markAllRead(at: Long)

    @Query("DELETE FROM signals WHERE postedAt < :before")
    suspend fun trim(before: Long)

    @Query("DELETE FROM signals")
    suspend fun clear()
}
