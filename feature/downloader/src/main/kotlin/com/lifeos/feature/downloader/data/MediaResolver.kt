package com.lifeos.feature.downloader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What a scan produced, plus how it was produced (shown in the UI). */
data class ResolveOutcome(
    val candidates: List<MediaCandidate>,
    val usedPlayer: Boolean,
)

/**
 * Chooses how to get a stream out of a page (§Module Downloader): the cheap
 * static scan first, and the offscreen-player route when the page hides its
 * link behind session-bound player JavaScript (ThisVid and the rest of the
 * kt_player family) or when the static scan comes up empty.
 */
@Singleton
class MediaResolver @Inject constructor(
    private val extractor: MediaExtractor,
    private val playerResolver: PlayerResolver,
) {

    suspend fun resolve(pageUrl: String): ResolveOutcome {
        val static = withContext(Dispatchers.IO) { extractor.extract(pageUrl) }
        if (!static.playerNeeded && static.candidates.isNotEmpty()) {
            return ResolveOutcome(static.candidates, usedPlayer = false)
        }
        val viaPlayer = playerResolver.resolve(pageUrl)
        return if (viaPlayer.isNotEmpty()) {
            ResolveOutcome(viaPlayer, usedPlayer = true)
        } else {
            ResolveOutcome(static.candidates, usedPlayer = false)
        }
    }
}
