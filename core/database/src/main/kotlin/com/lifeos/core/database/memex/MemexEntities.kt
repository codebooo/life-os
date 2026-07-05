package com.lifeos.core.database.memex

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Personal Memex archive item (§Module 22, [src 12]). Alpha scope: opt-in
 * streams only (share-ins, clips, scans); bodies stay in Room until the
 * vault-encrypted blob path lands for archives. Un-annotated items expire.
 */
@Entity(tableName = "archive_items")
data class ArchiveItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Origin: SHARE, CLIP, SCAN, NOTIFICATION. */
    val source: String,
    /** Payload kind: TEXT, URL. */
    val kind: String,
    val title: String,
    val body: String,
    val capturedAt: Long,
    val annotated: Boolean = false,
    val annotation: String = "",
    /** Un-annotated items are purged after this instant (§retention). */
    val expiresAt: Long,
)
