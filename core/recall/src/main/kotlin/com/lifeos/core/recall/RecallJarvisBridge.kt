package com.lifeos.core.recall

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * Semantic search as Jarvis reads it (§Module Recall). This is the topic he
 * should reach for when a question is about something the user wrote but does
 * not name exactly - keyword search is what fails there.
 */
internal class RecallProvider @Inject constructor(
    private val index: RecallIndex,
) : LifeDataProvider {

    override val topic: String = "recall"
    override val description: String = "semantic search over notes, clips, captures, chats and /LifeOS files"

    override suspend fun read(query: String?): String {
        if (query.isNullOrBlank()) {
            return "Recall holds ${index.size()} chunks. Ask with a query: [[get: recall | what you remember]]"
        }
        val hits = index.search(query)
        if (hits.isEmpty()) return "Recall found nothing for \"$query\" in ${index.size()} chunks."
        return buildString {
            appendLine("Recall for \"$query\":")
            hits.forEach { hit ->
                appendLine("- [${hit.module}] ${hit.title}: ${hit.text.replace('\n', ' ').take(240)}")
            }
        }.trim()
    }
}

internal class RecallActionHandler @Inject constructor(
    private val index: RecallIndex,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.ReindexRecall

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val indexed = index.reindex(force = false)
        return LifeResult.Success(indexed.toLong())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecallJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: RecallProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: RecallActionHandler): LifeActionHandler
}
