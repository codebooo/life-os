package com.lifeos.feature.notes.data

import com.lifeos.core.ai.rag.NotesRag
import com.lifeos.core.ai.rag.RagAnswer
import com.lifeos.core.ai.rag.RagChunk
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.notes.NoteDao
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** "Ask my notes" (§5.4): local retrieval → grounded on-device answer. */
@Singleton
class AskNotesUseCase @Inject constructor(
    private val noteDao: NoteDao,
    private val notesRag: NotesRag,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun retrieve(question: String): List<RagChunk> = withContext(dispatchers.io) {
        val notesById = mutableMapOf<Long, String>()
        val candidates = noteDao.allEmbeddings().map { embedding ->
            val title = notesById.getOrPut(embedding.noteId) {
                noteDao.getById(embedding.noteId)?.title ?: "Untitled"
            }
            NotesRag.StoredEmbedding(
                sourceId = embedding.noteId,
                sourceTitle = title,
                chunkText = embedding.chunkText,
                vector = embedding.vector,
            )
        }
        notesRag.retrieve(question, candidates)
    }

    suspend fun answer(question: String, retrieved: List<RagChunk>): LifeResult<RagAnswer> =
        notesRag.answer(question, retrieved)
}
