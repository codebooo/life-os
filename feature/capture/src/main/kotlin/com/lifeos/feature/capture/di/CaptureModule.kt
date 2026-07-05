package com.lifeos.feature.capture.di

import com.lifeos.feature.capture.data.CaptureRepository
import com.lifeos.feature.capture.data.DefaultCaptureRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CaptureModule {

    @Binds
    @Singleton
    abstract fun bindCaptureRepository(impl: DefaultCaptureRepository): CaptureRepository
}
