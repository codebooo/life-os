package com.lifeos.core.database.evolution

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One logged AI interaction (§Module 13). No content is stored — only which
 * engine served which kind of request and whether the user accepted the
 * result, so routing/templates can be scored locally over time.
 */
@Entity(tableName = "interaction_logs")
data class InteractionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ON_DEVICE or NAS. */
    val engine: String,
    /** CHAT, PLANNER, BRAIN_DUMP, MACRO, CLASSIFY, RAG. */
    val kind: String,
    /** Null until the user accepts/rejects (where the surface supports it). */
    val accepted: Boolean? = null,
    val at: Long,
)

@Dao
interface EvolutionDao {

    @Query("SELECT * FROM interaction_logs ORDER BY at DESC LIMIT 200")
    fun observeRecent(): Flow<List<InteractionLogEntity>>

    @Query("SELECT COUNT(*) FROM interaction_logs WHERE kind = :kind")
    suspend fun countByKind(kind: String): Int

    @Query("SELECT COUNT(*) FROM interaction_logs WHERE kind = :kind AND accepted = 1")
    suspend fun acceptedByKind(kind: String): Int

    @Insert
    suspend fun insert(log: InteractionLogEntity)
}
