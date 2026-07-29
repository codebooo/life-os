package com.lifeos.feature.news.data

import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Headlines as Jarvis reads them (§Module News): live RSS, no cache. */
internal class NewsProvider @Inject constructor(
    private val repository: NewsRepository,
) : LifeDataProvider {

    override val topic: String = "news"
    override val description: String = "current headlines from the configured feeds"

    override suspend fun read(query: String?): String {
        val articles = runCatching { repository.latest(repository.sources.map { it.id }.toSet()) }
            .getOrElse { return "News: could not reach the feeds (${it.message})." }
        val filtered = if (query.isNullOrBlank()) {
            articles
        } else {
            articles.filter { it.title.contains(query, ignoreCase = true) }
        }
        if (filtered.isEmpty()) return "News: nothing matching right now."
        return buildString {
            appendLine("Headlines:")
            filtered.take(10).forEach { appendLine("- [${it.source}] ${it.title.take(110)}") }
        }.trim()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NewsJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: NewsProvider): LifeDataProvider
}
