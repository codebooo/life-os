package com.lifeos.core.vault.di

import com.lifeos.core.vault.DefaultVaultRepository
import com.lifeos.core.vault.VaultRepository
import com.lifeos.core.vault.crypto.AndroidKeystoreVaultKeysetProvider
import com.lifeos.core.vault.crypto.VaultKeysetProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VaultModule {

    @Binds
    @Singleton
    abstract fun bindVaultRepository(impl: DefaultVaultRepository): VaultRepository

    @Binds
    @Singleton
    abstract fun bindVaultKeysetProvider(impl: AndroidKeystoreVaultKeysetProvider): VaultKeysetProvider
}
