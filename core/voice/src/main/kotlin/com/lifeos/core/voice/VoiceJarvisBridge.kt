package com.lifeos.core.voice

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Lets any module, and Jarvis himself, say something out loud. */
internal class SpeakActionHandler @Inject constructor(
    private val speaker: Speaker,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.Speak

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        speaker.speak((action as LifeAction.Speak).text)
        return LifeResult.Success(null)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VoiceModule {

    @Binds
    @IntoSet
    abstract fun bindSpeakHandler(impl: SpeakActionHandler): LifeActionHandler
}
