package com.lifeos.core.vault.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

/**
 * Phase 0 exit criterion: the vault can encrypt and decrypt a blob.
 * Uses a software keyset on the JVM; on device the same [VaultCrypto] runs
 * over a Keystore-wrapped keyset from [VaultKeysetProvider].
 */
class VaultCryptoTest {

    private lateinit var crypto: VaultCrypto

    @Before
    fun setUp() {
        AeadConfig.register()
        val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
        crypto = VaultCrypto(handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
    }

    @Test
    fun `encrypt then decrypt round-trips the plaintext`() {
        val plaintext = "a sensitive brain-dump capture".toByteArray()

        val ciphertext = crypto.encrypt(plaintext, ref = "blob-1")

        assertFalse(ciphertext.contentEquals(plaintext))
        assertArrayEquals(plaintext, crypto.decrypt(ciphertext, ref = "blob-1"))
    }

    @Test
    fun `decrypt fails when the ref does not match`() {
        val ciphertext = crypto.encrypt("secret".toByteArray(), ref = "blob-1")

        assertThrows(GeneralSecurityException::class.java) {
            crypto.decrypt(ciphertext, ref = "blob-2")
        }
    }

    @Test
    fun `ciphertexts differ across calls for the same plaintext`() {
        val plaintext = "same input".toByteArray()

        val first = crypto.encrypt(plaintext, ref = "blob-1")
        val second = crypto.encrypt(plaintext, ref = "blob-1")

        assertFalse(first.contentEquals(second))
    }
}
