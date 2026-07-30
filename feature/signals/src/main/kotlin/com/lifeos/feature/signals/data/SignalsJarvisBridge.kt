package com.lifeos.feature.signals.data

import com.lifeos.core.database.signals.SignalDao
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the "what did I miss" digest, shared by the screen and by Jarvis. */
@Singleton
class SignalDigest @Inject constructor(
    private val signalDao: SignalDao,
) {

    suspend fun build(hours: Int = 12, limit: Int = 40): String {
        val since = System.currentTimeMillis() - hours * 3_600_000L
        val signals = signalDao.since(since)
        if (signals.isEmpty()) return "Nothing captured in the last $hours hours."
        val byApp = signals.groupBy { it.appLabel }
        return buildString {
            appendLine("${signals.size} notification(s) in the last $hours hours, ${byApp.size} app(s):")
            byApp.entries
                .sortedByDescending { it.value.size }
                .take(8)
                .forEach { (app, rows) ->
                    appendLine("$app (${rows.size}):")
                    rows.take(4).forEach { row ->
                        val line = listOf(row.title, row.text).filter { it.isNotBlank() }.joinToString(" - ")
                        appendLine("  - ${line.take(120)}")
                    }
                }
            val codes = signals.filter { it.extracted == "CODE" }
            if (codes.isNotEmpty()) {
                appendLine("Codes: " + codes.take(3).joinToString("; ") { "${it.appLabel}: ${it.text.take(60)}" })
            }
            val parcels = signals.filter { it.extracted == "PARCEL" }
            if (parcels.isNotEmpty()) {
                appendLine("Parcel updates: ${parcels.size}")
            }
        }.trim().take(limit * 60)
    }
}

/** Signals as Jarvis reads them. */
internal class SignalsProvider @Inject constructor(
    private val digest: SignalDigest,
    private val signalDao: SignalDao,
) : LifeDataProvider {

    override val topic: String = "signals"
    override val description: String = "notifications you missed, grouped by app"

    override suspend fun read(query: String?): String {
        val hours = query?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(1, 72) ?: 12
        val body = digest.build(hours)
        if (query.isNullOrBlank()) return body
        val needle = query.trim()
        val matches = signalDao.since(System.currentTimeMillis() - 72 * 3_600_000L)
            .filter {
                it.appLabel.contains(needle, ignoreCase = true) ||
                    it.title.contains(needle, ignoreCase = true) ||
                    it.text.contains(needle, ignoreCase = true)
            }
        if (matches.isEmpty()) return body
        return buildString {
            appendLine("Matching \"$needle\":")
            matches.take(10).forEach {
                appendLine("- ${it.appLabel}: ${it.title} ${it.text}".take(160))
            }
        }.trim()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SignalsJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: SignalsProvider): LifeDataProvider
}
