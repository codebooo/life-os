package com.lifeos.core.vault

import android.content.Context
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.vault.VaultBlobDao
import com.lifeos.core.database.vault.VaultBlobEntity
import com.lifeos.core.model.vault.VaultMeta
import com.lifeos.core.model.vault.VaultRef
import com.lifeos.core.vault.crypto.AndroidKeystoreVaultKeysetProvider
import com.lifeos.core.vault.crypto.VaultCrypto
import com.lifeos.core.vault.crypto.VaultKeysetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultVaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keysetProvider: VaultKeysetProvider,
    private val vaultBlobDao: VaultBlobDao,
    private val dispatchers: DispatcherProvider,
) : VaultRepository {

    private val crypto: VaultCrypto by lazy { VaultCrypto(keysetProvider.aead()) }

    private val vaultDir: File
        get() = File(context.filesDir, VAULT_DIR).apply { mkdirs() }

    override suspend fun putBlob(bytes: ByteArray, meta: VaultMeta): LifeResult<VaultRef> =
        withContext(dispatchers.io) {
            runCatchingLife(mapError = { LifeError.Crypto("Failed to store vault blob", it) }) {
                val ref = UUID.randomUUID().toString()
                val ciphertext = crypto.encrypt(bytes, ref)
                blobFile(ref).writeBytes(ciphertext)
                vaultBlobDao.insert(
                    VaultBlobEntity(
                        ref = ref,
                        algo = VaultCrypto.ALGO,
                        keyAlias = AndroidKeystoreVaultKeysetProvider.MASTER_KEY_ALIAS,
                        sizeBytes = ciphertext.size.toLong(),
                        mimeType = meta.mimeType,
                        title = meta.title,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                VaultRef(ref)
            }
        }

    override suspend fun openBlob(ref: VaultRef): LifeResult<ByteArray> =
        withContext(dispatchers.io) {
            val file = blobFile(ref.value)
            if (!file.exists()) {
                LifeResult.Failure(LifeError.NotFound("No vault blob for ref ${ref.value}"))
            } else {
                runCatchingLife(mapError = { LifeError.Crypto("Failed to decrypt vault blob", it) }) {
                    crypto.decrypt(file.readBytes(), ref.value)
                }
            }
        }

    override suspend fun deleteBlob(ref: VaultRef): LifeResult<Unit> =
        withContext(dispatchers.io) {
            runCatchingLife(mapError = { LifeError.Crypto("Failed to delete vault blob", it) }) {
                blobFile(ref.value).delete()
                vaultBlobDao.deleteByRef(ref.value)
            }
        }

    private fun blobFile(ref: String): File = File(vaultDir, "$ref.bin")

    private companion object {
        const val VAULT_DIR = "vault"
    }
}
