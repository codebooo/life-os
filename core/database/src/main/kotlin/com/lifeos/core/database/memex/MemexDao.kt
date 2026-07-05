package com.lifeos.core.database.memex

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemexDao {

    @Query("SELECT * FROM archive_items ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ArchiveItemEntity>>

    @Query(
        "SELECT * FROM archive_items WHERE title LIKE '%' || :query || '%' " +
            "OR body LIKE '%' || :query || '%' OR annotation LIKE '%' || :query || '%' " +
            "ORDER BY capturedAt DESC",
    )
    fun search(query: String): Flow<List<ArchiveItemEntity>>

    @Insert
    suspend fun insert(item: ArchiveItemEntity): Long

    @Update
    suspend fun update(item: ArchiveItemEntity)

    @Query("DELETE FROM archive_items WHERE id = :id")
    suspend fun delete(id: Long)

    /** Retention: purge expired items that were never annotated. */
    @Query("DELETE FROM archive_items WHERE annotated = 0 AND expiresAt < :now")
    suspend fun purgeExpired(now: Long): Int
}
