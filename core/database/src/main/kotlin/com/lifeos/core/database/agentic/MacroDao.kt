package com.lifeos.core.database.agentic

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {

    @Query("SELECT * FROM macros ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun byId(id: Long): MacroEntity?

    @Insert
    suspend fun insert(macro: MacroEntity): Long

    @Update
    suspend fun update(macro: MacroEntity)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun delete(id: Long)
}
