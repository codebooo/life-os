package com.lifeos.core.database.vault

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata row for an encrypted vault blob (§1.7). The blob body lives on the
 * filesystem, always encrypted; no plaintext body is ever stored in Room.
 */
@Entity(tableName = "vault_blobs")
data class VaultBlobEntity(
    @PrimaryKey val ref: String,
    val algo: String,
    val keyAlias: String,
    val sizeBytes: Long,
    val mimeType: String,
    val title: String?,
    val createdAt: Long,
    val nasSynced: Boolean = false,
)
