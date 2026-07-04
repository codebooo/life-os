package com.lifeos.core.database.vault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultBlobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blob: VaultBlobEntity)

    @Query("SELECT * FROM vault_blobs WHERE ref = :ref")
    suspend fun getByRef(ref: String): VaultBlobEntity?

    @Query("SELECT * FROM vault_blobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VaultBlobEntity>>

    @Query("DELETE FROM vault_blobs WHERE ref = :ref")
    suspend fun deleteByRef(ref: String)
}
