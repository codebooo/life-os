package com.lifeos.core.network.di

import com.lifeos.core.network.AndroidConnectivityObserver
import com.lifeos.core.network.ConnectivityObserver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkProviderModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // Streamed AI responses can stay open for minutes; per-call timeouts
            // are tightened where a fast answer is required (health checks).
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindsModule {

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver
}
