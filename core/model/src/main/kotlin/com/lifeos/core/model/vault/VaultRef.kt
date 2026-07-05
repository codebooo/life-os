package com.lifeos.core.model.vault

/**
 * Opaque handle to an encrypted blob stored by `:core:vault`.
 * Only metadata is visible outside the vault; bodies stay encrypted at rest (§core:vault).
 */
@JvmInline
value class VaultRef(val value: String)

/** Non-sensitive metadata stored alongside an encrypted blob for indexing/search. */
data class VaultMeta(
    val title: String? = null,
    val mimeType: String = "application/octet-stream",
    val tags: List<String> = emptyList(),
)
