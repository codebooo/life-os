package com.lifeos.core.recall

import com.lifeos.core.ai.rag.TextEmbedder
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.chat.ChatDao
import com.lifeos.core.database.memex.MemexDao
import com.lifeos.core.database.notes.NoteDao
import com.lifeos.core.database.recall.RecallChunkEntity
import com.lifeos.core.database.recall.RecallDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One hit, with enough provenance for Jarvis to cite it. */
data class RecallHit(
    val module: String,
    val title: String,
    val text: String,
    val score: Float,
)

/**
 * Semantic memory over everything the user wrote (§Module Recall).
 *
 * Notes, captures, memex clips, chat history and the readable files under
 * /LifeOS are chunked, embedded on-device and stored in Room. Indexing is
 * incremental - a source whose timestamp has not moved is skipped - so the
 * usual run costs almost nothing, and retrieval is a cosine scan, which is
 * plenty at personal-corpus scale.
 */
@Singleton
class RecallIndex @Inject constructor(
    private val recallDao: RecallDao,
    private val embedder: TextEmbedder,
    private val noteDao: NoteDao,
    private val captureDao: CaptureDao,
    private val memexDao: MemexDao,
    private val chatDao: ChatDao,
) {

    private val mutex = Mutex()

    /** Adds or replaces one source's chunks. Safe to call from any module. */
    suspend fun put(module: String, sourceId: String, title: String, body: String, updatedAt: Long) {
        val text = body.trim()
        if (text.isBlank()) return
        val key = "$module:$sourceId"
        recallDao.deleteSource(key)
        val chunks = chunk(text).mapIndexed { index, piece ->
            RecallChunkEntity(
                sourceKey = key,
                module = module,
                title = title.take(120).ifBlank { module },
                body = piece,
                vector = embedder.embed("$title\n$piece").joinToString(",") { it.toString() },
                updatedAt = updatedAt,
            )
        }
        if (chunks.isNotEmpty()) recallDao.insertAll(chunks)
    }

    /**
     * Walks every indexable source. [force] re-embeds even unchanged rows,
     * which is what the "rebuild" button does after the embedder changes.
     */
    suspend fun reindex(force: Boolean = false): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (force) recallDao.clear()
            val known = recallDao.indexedSources().associate { it.sourceKey to it.updatedAt }
            var indexed = 0

            noteDao.observeAll().first().forEach { note ->
                if (note.bodyVaultRef != null) return@forEach
                val key = "note:${note.id}"
                if (!force && known[key] == note.updatedAt) return@forEach
                val body = runCatching { File(note.path).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
                put("note", note.id.toString(), note.title, body, note.updatedAt)
                indexed++
            }

            captureDao.observeRecent().first().forEach { capture ->
                val text = capture.text ?: return@forEach
                val key = "capture:${capture.id}"
                if (!force && known[key] == capture.createdAt) return@forEach
                put("capture", capture.id.toString(), text.take(60), text, capture.createdAt)
                indexed++
            }

            memexDao.observeAll().first().forEach { clip ->
                val key = "memex:${clip.id}"
                if (!force && known[key] == clip.capturedAt) return@forEach
                put("memex", clip.id.toString(), clip.title, clip.body, clip.capturedAt)
                indexed++
            }

            chatDao.observeConversations().first().take(40).forEach { conversation ->
                val key = "chat:${conversation.id}"
                if (!force && known[key] == conversation.updatedAt) return@forEach
                val body = chatDao.getMessages(conversation.id)
                    .joinToString("\n") { "${it.role}: ${it.content}" }
                put("chat", conversation.id.toString(), conversation.title, body, conversation.updatedAt)
                indexed++
            }

            indexFolder(known, force)?.let { indexed += it }
            LifeLogger.i(TAG, "Recall indexed $indexed source(s), ${recallDao.count()} chunks")
            indexed
        }
    }

    /** The readable mirror under /Internal storage/LifeOS, when access was granted. */
    private suspend fun indexFolder(known: Map<String, Long>, force: Boolean): Int? {
        val root = File(android.os.Environment.getExternalStorageDirectory(), "LifeOS")
        if (!root.exists() || !root.canRead()) return null
        var count = 0
        root.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && it.length() in 1..MAX_FILE_BYTES && it.extension.lowercase() in TEXT_EXTENSIONS }
            .take(300)
            .forEach { file ->
                val key = "file:${file.absolutePath}"
                if (!force && known[key] == file.lastModified()) return@forEach
                val body = runCatching { file.readText() }.getOrNull() ?: return@forEach
                put("file", file.absolutePath, file.name, body, file.lastModified())
                count++
            }
        return count
    }

    /** Top matches by cosine similarity. */
    suspend fun search(query: String, limit: Int = 6): List<RecallHit> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()
        val target = embedder.embed(query)
        recallDao.all()
            .mapNotNull { chunk ->
                val vector = chunk.vector.split(',').mapNotNull { it.toFloatOrNull() }
                if (vector.size != target.size) return@mapNotNull null
                var dot = 0f
                for (i in target.indices) dot += target[i] * vector[i]
                if (dot <= MIN_SCORE) null else RecallHit(chunk.module, chunk.title, chunk.body, dot)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    suspend fun size(): Int = recallDao.count()

    /** Paragraph-ish chunks, bounded so one huge note cannot dominate the index. */
    private fun chunk(text: String): List<String> {
        val paragraphs = text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<StringBuilder>()
        paragraphs.forEach { paragraph ->
            val current = chunks.lastOrNull()
            if (current != null && current.length + paragraph.length < CHUNK_CHARS) {
                current.append("\n\n").append(paragraph)
            } else {
                chunks += StringBuilder(paragraph.take(CHUNK_CHARS))
            }
        }
        return chunks.map { it.toString() }.take(MAX_CHUNKS_PER_SOURCE)
    }

    private companion object {
        const val TAG = "RecallIndex"
        const val CHUNK_CHARS = 700
        const val MAX_CHUNKS_PER_SOURCE = 24
        const val MAX_FILE_BYTES = 512L * 1024
        const val MIN_SCORE = 0.05f
        val TEXT_EXTENSIONS = setOf("md", "txt", "json", "csv", "log")
    }
}
