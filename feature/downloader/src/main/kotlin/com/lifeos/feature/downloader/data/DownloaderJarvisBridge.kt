package com.lifeos.feature.downloader.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.downloads.DownloadDao
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Downloads as Jarvis reads them: queue, progress, what landed. */
internal class DownloaderProvider @Inject constructor(
    private val downloadDao: DownloadDao,
) : LifeDataProvider {

    override val topic: String = "downloads"
    override val description: String = "download queue and finished files, plus supported sites"

    override suspend fun read(query: String?): String {
        if (query != null && query.isNotBlank()) {
            val matches = SiteCatalog.search(query).take(8)
            if (matches.isNotEmpty()) {
                return "Supported sites matching \"$query\": " +
                    matches.joinToString("; ") { "${it.name} (${it.host})" }
            }
        }
        val rows = downloadDao.observeAll().first()
        if (rows.isEmpty()) {
            return "Downloads: nothing yet. ${SiteCatalog.sites.size} sites are supported."
        }
        return buildString {
            appendLine("Downloads (${rows.size}):")
            rows.take(8).forEach { row ->
                val state = when (row.status) {
                    "DONE" -> "done, ${row.sizeBytes / 1_048_576} MB"
                    "FAILED" -> "failed: ${row.error ?: "unknown"}"
                    else -> "${row.status.lowercase()} ${row.progressPercent}%"
                }
                appendLine("- ${row.title.take(50)}: $state")
            }
        }.trim()
    }
}

/** Queues a download for a pasted page or file URL. */
internal class DownloaderActionHandler @Inject constructor(
    private val resolver: MediaResolver,
    private val engine: DownloadEngine,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.StartDownload

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val url = (action as LifeAction.StartDownload).url.trim()
        if (!url.startsWith("http")) {
            return LifeResult.Failure(LifeError.Validation("That is not a full http(s) URL"))
        }
        val outcome = runCatching { resolver.resolve(url) }.getOrElse {
            return LifeResult.Failure(LifeError.Unknown(it.message ?: "Could not reach that URL"))
        }
        val best = outcome.candidates.firstOrNull()
            ?: return LifeResult.Failure(LifeError.Validation("No downloadable media on that page"))
        engine.enqueue(best, url)
        return LifeResult.Success(null)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DownloaderJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: DownloaderProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: DownloaderActionHandler): LifeActionHandler
}
