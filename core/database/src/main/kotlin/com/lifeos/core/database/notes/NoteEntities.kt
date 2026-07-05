package com.lifeos.core.database.notes

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Note metadata (§Module 21, [src 28]). The body lives as a Markdown file at
 * [path] for portability — or, when [bodyVaultRef] is set, as an encrypted
 * vault blob instead (sensitive notes never sit in plaintext).
 */
@Entity(tableName = "notes", indices = [Index(value = ["path"], unique = true)])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val title: String,
    val bodyVaultRef: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A wiki-link from one note to another ([src 28] backlinks). */
@Entity(
    tableName = "note_links",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromNoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fromNoteId"), Index("toTitle")],
)
data class NoteLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromNoteId: Long,
    /** Link target by title — resolved lazily so links may point at not-yet-created notes. */
    val toTitle: String,
)

/**
 * One embedded chunk of a note for on-device semantic search (§5.4, [src 29]).
 * The chunk text is retained for grounded citations; sensitive notes are
 * never embedded.
 */
@Entity(
    tableName = "note_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class NoteEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val chunkIndex: Int,
    val chunkText: String,
    val vector: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is NoteEmbeddingEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()
}
