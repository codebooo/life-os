package com.lifeos.feature.vault.data

import kotlinx.serialization.Serializable

/** MIME markers that tell the vault how to render a decrypted blob. */
object VaultMime {
    const val PASSWORD = "application/x-lifeos-password"
    const val SECURE_TEXT = "text/markdown"
    // Images/video/files keep their real MIME (image/*, video/*, …).
}

/** A custom label/value pair on a password entry (Proton-Pass "Add field"). */
@Serializable
data class CustomField(val label: String, val value: String, val hidden: Boolean = false)

/** A file attached to a password entry, stored as its own encrypted blob. */
@Serializable
data class AttachmentRef(val ref: String, val name: String, val mimeType: String, val sizeBytes: Long)

/** A Proton-Pass-style login item, serialized to JSON inside one vault blob. */
@Serializable
data class PasswordEntry(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    /** Base32 TOTP secret; the vault generates the live 2FA code from it. */
    val totpSecret: String = "",
    val website: String = "",
    val note: String = "",
    val customFields: List<CustomField> = emptyList(),
    val attachments: List<AttachmentRef> = emptyList(),
)
