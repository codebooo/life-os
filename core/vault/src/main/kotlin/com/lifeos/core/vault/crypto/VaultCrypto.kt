package com.lifeos.core.vault.crypto

import com.google.crypto.tink.Aead

/**
 * The pure crypto layer of the vault (§core:vault, [src 20]): AEAD envelope
 * encryption over blob bodies. Key provisioning (Android Keystore-wrapped
 * keyset) is injected via [VaultKeysetProvider] so this class stays
 * unit-testable on the JVM with a software keyset.
 */
class VaultCrypto(private val aead: Aead) {

    /** Encrypts [plaintext], binding the ciphertext to [ref] as associated data. */
    fun encrypt(plaintext: ByteArray, ref: String): ByteArray =
        aead.encrypt(plaintext, ref.toByteArray(Charsets.UTF_8))

    /** Decrypts [ciphertext] previously produced by [encrypt] with the same [ref]. */
    fun decrypt(ciphertext: ByteArray, ref: String): ByteArray =
        aead.decrypt(ciphertext, ref.toByteArray(Charsets.UTF_8))

    companion object {
        /** Algorithm label recorded in blob metadata. */
        const val ALGO = "AES256_GCM/Tink"
    }
}
