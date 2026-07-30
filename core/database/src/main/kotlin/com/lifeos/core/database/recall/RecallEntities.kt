package com.lifeos.core.database.recall

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One embedded chunk of the user's own text (§Module Recall).
 *
 * [sourceKey] is "module:id" so re-indexing a changed note replaces its chunks
 * instead of piling up duplicates. Vectors are stored as a comma-separated
 * string: small enough for a phone-sized corpus and it keeps Room schema-simple.
 */
@Entity(tableName = "recall_chunks", indices = [Index("sourceKey"), Index("module")])
data class RecallChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,
    val module: String,
    val title: String,
    val body: String,
    val vector: String,
    val updatedAt: Long,
)

@Dao
interface RecallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<RecallChunkEntity>)

    @Query("DELETE FROM recall_chunks WHERE sourceKey = :sourceKey")
    suspend fun deleteSource(sourceKey: String)

    @Query("SELECT * FROM recall_chunks")
    suspend fun all(): List<RecallChunkEntity>

    @Query("SELECT sourceKey, updatedAt FROM recall_chunks GROUP BY sourceKey")
    suspend fun indexedSources(): List<IndexedSource>

    @Query("SELECT COUNT(*) FROM recall_chunks")
    suspend fun count(): Int

    @Query("DELETE FROM recall_chunks")
    suspend fun clear()
}

data class IndexedSource(val sourceKey: String, val updatedAt: Long)
