package com.lifeos.feature.imagereasoning.di

import com.lifeos.feature.imagereasoning.data.DefaultScanRepository
import com.lifeos.feature.imagereasoning.data.ScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ScanModule {

    @Binds
    @Singleton
    abstract fun bindScanRepository(impl: DefaultScanRepository): ScanRepository
}
