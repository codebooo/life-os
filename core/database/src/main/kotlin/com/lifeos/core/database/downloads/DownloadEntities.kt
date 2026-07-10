package com.lifeos.core.database.downloads

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One media download (§Module Downloader). */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val mediaUrl: String,
    val title: String,
    val mimeType: String,
    /** QUEUED, RUNNING, DONE, FAILED */
    val status: String = "QUEUED",
    val progressPercent: Int = 0,
    val sizeBytes: Long = 0,
    /** MediaStore content URI once saved to the public Downloads collection. */
    val savedUri: String? = null,
    val error: String? = null,
    val createdAt: Long,
)

@Dao
interface DownloadDao {

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("UPDATE downloads SET status = :status, progressPercent = :progress, error = :error WHERE id = :id")
    suspend fun setProgress(id: Long, status: String, progress: Int, error: String? = null)

    @Query("UPDATE downloads SET status = 'DONE', progressPercent = 100, sizeBytes = :sizeBytes, savedUri = :savedUri WHERE id = :id")
    suspend fun setDone(id: Long, sizeBytes: Long, savedUri: String?)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}
