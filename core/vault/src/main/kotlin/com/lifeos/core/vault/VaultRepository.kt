package com.lifeos.core.vault

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.model.vault.VaultMeta
import com.lifeos.core.model.vault.VaultRef

/**
 * Encrypted at-rest store for sensitive captures (§core:vault, [src 20]).
 * Bodies are filesystem-backed encrypted blobs (safe to mirror to the NAS
 * still-encrypted); only non-sensitive metadata lands in Room.
 *
 * Write path requires no unlock; read path is auth-gated at the call site
 * (BiometricPrompt) before invoking [openBlob].
 */
interface VaultRepository {

    /** Encrypts and stores [bytes]; returns the opaque reference. Never requires unlock. */
    suspend fun putBlob(bytes: ByteArray, meta: VaultMeta): LifeResult<VaultRef>

    /** Decrypts and returns a blob body. Callers must gate this behind biometric auth. */
    suspend fun openBlob(ref: VaultRef): LifeResult<ByteArray>

    /** Removes the encrypted blob and its metadata row. */
    suspend fun deleteBlob(ref: VaultRef): LifeResult<Unit>
}
