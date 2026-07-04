package com.lifeos.core.vault.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provisions the vault AEAD keyset: generated on first use, persisted in app
 * prefs, and wrapped by a hardware-backed Android Keystore master key (§8.8).
 * Writes never require user auth; read-side biometric gating happens above
 * this layer in [com.lifeos.core.vault.VaultRepository] consumers.
 */
interface VaultKeysetProvider {
    fun aead(): Aead
}

@Singleton
internal class AndroidKeystoreVaultKeysetProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : VaultKeysetProvider {

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFS_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun aead(): Aead = aead

    companion object {
        const val MASTER_KEY_ALIAS = "lifeos_vault_master"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
        private const val KEYSET_NAME = "lifeos_vault_keyset"
        private const val PREFS_NAME = "lifeos_vault_prefs"
    }
}
