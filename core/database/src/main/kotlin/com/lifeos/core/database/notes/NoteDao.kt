package com.lifeos.core.database.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<NoteEntity>>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM note_links WHERE fromNoteId = :noteId")
    suspend fun deleteLinksFrom(noteId: Long)

    @Insert
    suspend fun insertLinks(links: List<NoteLinkEntity>)

    @Query("SELECT n.* FROM notes n JOIN note_links l ON l.fromNoteId = n.id WHERE l.toTitle = :title")
    suspend fun backlinksTo(title: String): List<NoteEntity>

    @Query("DELETE FROM note_embeddings WHERE noteId = :noteId")
    suspend fun deleteEmbeddings(noteId: Long)

    @Insert
    suspend fun insertEmbeddings(embeddings: List<NoteEmbeddingEntity>)

    @Query("SELECT * FROM note_embeddings")
    suspend fun allEmbeddings(): List<NoteEmbeddingEntity>
}
