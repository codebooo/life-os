package com.lifeos.feature.notes.data

import android.content.Context
import com.lifeos.core.ai.rag.NotesRag
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.storage.LifeOsPublicMirror
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.getOrNull
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.notes.NoteDao
import com.lifeos.core.database.notes.NoteEmbeddingEntity
import com.lifeos.core.database.notes.NoteEntity
import com.lifeos.core.database.notes.NoteLinkEntity
import com.lifeos.core.model.vault.VaultMeta
import com.lifeos.core.model.vault.VaultRef
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import com.lifeos.core.vault.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class NoteWithBody(
    val note: NoteEntity,
    val body: String,
    val backlinks: List<NoteEntity>,
)

/**
 * Local-first Markdown notes (§Module 21, [src 28]). Bodies are plain `.md`
 * files under the app's notes dir — portable forever — unless the note is
 * marked sensitive, in which case the body lives encrypted in the Vault.
 * Saves publish [LifeEvent.NoteSaved] and re-embed for RAG ([src 29]).
 */
interface NotesRepository {
    fun observeNotes(query: String?): Flow<List<NoteEntity>>
    suspend fun load(noteId: Long): LifeResult<NoteWithBody>
    suspend fun save(noteId: Long?, title: String, body: String, sensitive: Boolean): LifeResult<Long>
    suspend fun delete(noteId: Long)
}

@Singleton
internal class DefaultNotesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val vaultRepository: VaultRepository,
    private val notesRag: NotesRag,
    private val eventBus: LifeEventBus,
    private val publicMirror: LifeOsPublicMirror,
    private val dispatchers: DispatcherProvider,
) : NotesRepository {

    private val notesDir: File
        get() = File(context.filesDir, "notes").apply { mkdirs() }

    override fun observeNotes(query: String?): Flow<List<NoteEntity>> =
        if (query.isNullOrBlank()) noteDao.observeAll() else noteDao.search(query)

    override suspend fun load(noteId: Long): LifeResult<NoteWithBody> = withContext(dispatchers.io) {
        val note = noteDao.getById(noteId)
            ?: return@withContext LifeResult.Failure(LifeError.NotFound("Note $noteId not found"))
        runCatchingLife {
            val body = if (note.bodyVaultRef != null) {
                String(
                    vaultRepository.openBlob(VaultRef(note.bodyVaultRef!!)).getOrNull()
                        ?: error("Vault blob unreadable — authentication may be required"),
                )
            } else {
                File(note.path).takeIf { it.exists() }?.readText().orEmpty()
            }
            NoteWithBody(note = note, body = body, backlinks = noteDao.backlinksTo(note.title))
        }
    }

    override suspend fun save(
        noteId: Long?,
        title: String,
        body: String,
        sensitive: Boolean,
    ): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            val now = System.currentTimeMillis()
            val existing = noteId?.let { noteDao.getById(it) }

            var vaultRef: String? = null
            val path: String
            if (sensitive) {
                vaultRef = when (
                    val stored = vaultRepository.putBlob(
                        body.toByteArray(),
                        VaultMeta(title = title, mimeType = "text/markdown"),
                    )
                ) {
                    is LifeResult.Success -> stored.value.value
                    is LifeResult.Failure -> error(stored.error.message)
                }
                path = existing?.path ?: File(notesDir, "${fileSlug(title, now)}.md.vault").absolutePath
                // Never leave a plaintext body behind when a note turns sensitive.
                existing?.path?.let { File(it).delete() }
            } else {
                path = existing?.path ?: File(notesDir, "${fileSlug(title, now)}.md").absolutePath
                File(path).writeText(body)
            }

            val id = if (existing == null) {
                noteDao.insert(
                    NoteEntity(
                        path = path,
                        title = title,
                        bodyVaultRef = vaultRef,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                noteDao.update(
                    existing.copy(title = title, bodyVaultRef = vaultRef, updatedAt = now),
                )
                existing.id
            }

            reindexLinks(id, body)
            reindexEmbeddings(id, title, body, sensitive)
            // Mirror non-sensitive notes into the readable /LifeOS/Notes folder
            // (no-op unless All-files access is granted). Sensitive notes never leave the vault.
            // Filename tracks the note's own file so edits overwrite in place.
            if (!sensitive) publicMirror.writeText("Notes", File(path).name, body)
            eventBus.tryPublish(LifeEvent.NoteSaved(noteId = id, title = title))
            id
        }
    }

    override suspend fun delete(noteId: Long) = withContext(dispatchers.io) {
        noteDao.getById(noteId)?.let { note ->
            if (note.bodyVaultRef == null) {
                File(note.path).delete()
                publicMirror.delete("Notes", File(note.path).name)
            }
            note.bodyVaultRef?.let { vaultRepository.deleteBlob(VaultRef(it)) }
            noteDao.delete(noteId)
        } ?: Unit
    }

    private suspend fun reindexLinks(noteId: Long, body: String) {
        noteDao.deleteLinksFrom(noteId)
        val links = WIKI_LINK.findAll(body)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { NoteLinkEntity(fromNoteId = noteId, toTitle = it) }
            .toList()
        if (links.isNotEmpty()) noteDao.insertLinks(links)
    }

    private suspend fun reindexEmbeddings(noteId: Long, title: String, body: String, sensitive: Boolean) {
        noteDao.deleteEmbeddings(noteId)
        if (sensitive) return // sensitive bodies are never indexed in plaintext
        val embeddings = notesRag.chunk(body).mapIndexed { index, chunk ->
            NoteEmbeddingEntity(
                noteId = noteId,
                chunkIndex = index,
                chunkText = chunk,
                vector = notesRag.embed("$title\n$chunk"),
            )
        }
        if (embeddings.isNotEmpty()) noteDao.insertEmbeddings(embeddings)
    }

    private fun fileSlug(title: String, now: Long): String {
        val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        return if (slug.isEmpty()) "note-$now" else "$slug-$now"
    }

    private companion object {
        val WIKI_LINK = Regex("\\[\\[([^\\]]+)]]")
    }
}
