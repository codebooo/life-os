package com.lifeos.core.database.scan

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A scanned/analyzed document (§Module 11). */
@Entity(tableName = "scanned_documents")
data class ScannedDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** RECEIPT, WHITEBOARD, BARCODE, OTHER */
    val kind: String,
    val imagePath: String?,
    val ocrText: String,
    /** JSON of the structured extraction (fields depend on kind). */
    val extractedJson: String?,
    val linkedModule: String?,
    val linkedEntityId: Long?,
    val createdAt: Long,
)

@Dao
interface ScanDao {

    @Insert
    suspend fun insert(doc: ScannedDocumentEntity): Long

    @Update
    suspend fun update(doc: ScannedDocumentEntity)

    @Query("SELECT * FROM scanned_documents WHERE id = :id")
    suspend fun getById(id: Long): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<ScannedDocumentEntity>>

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun delete(id: Long)
}
