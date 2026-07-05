package com.lifeos.core.ai.di

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.engine.gemma.GemmaEngine
import com.lifeos.core.ai.engine.ollama.OllamaEngine
import com.lifeos.core.ai.rag.HashingTextEmbedder
import com.lifeos.core.ai.rag.TextEmbedder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AiModule {

    @Provides
    @Singleton
    fun provideAiRouter(
        gemmaEngine: GemmaEngine,
        ollamaEngine: OllamaEngine,
    ): AiRouter = AiRouter(onDevice = gemmaEngine, nas = ollamaEngine)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AiBindsModule {

    @Binds
    @Singleton
    abstract fun bindTextEmbedder(impl: HashingTextEmbedder): TextEmbedder
}
