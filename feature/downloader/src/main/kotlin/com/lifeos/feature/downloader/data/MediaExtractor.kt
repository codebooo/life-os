package com.lifeos.feature.downloader.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A downloadable media stream discovered on a page. */
data class MediaCandidate(
    val url: String,
    val title: String,
    val mimeType: String,
    val kind: String, // VIDEO, AUDIO, HLS
)

/**
 * Generic on-device media extractor (§Module Downloader). Given any URL it
 * finds direct media: the URL itself when it already serves video/audio, or
 * streams declared in the page's HTML — OpenGraph (`og:video`, used by
 * Vimeo/TikTok/X/Instagram and many others), `<video>`/`<source>` tags, and
 * raw .mp4/.webm/.mp3/.m3u8 links. No site-specific scraping, no accounts,
 * no third-party API — everything runs on the phone.
 */
@Singleton
class MediaExtractor @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun extract(pageUrl: String): List<MediaCandidate> {
        val url = pageUrl.trim()
        require(url.startsWith("http")) { "Enter a full http(s) URL" }

        // The URL itself may already be the media file.
        directKind(url)?.let { kind ->
            return listOf(MediaCandidate(url, fileName(url), mimeFor(url), kind))
        }

        val head = client.newCall(
            Request.Builder().url(url).header("User-Agent", UA).build(),
        ).execute()
        head.use { response ->
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                return listOf(
                    MediaCandidate(
                        url = url,
                        title = fileName(url),
                        mimeType = contentType.substringBefore(';'),
                        kind = if (contentType.startsWith("audio/")) "AUDIO" else "VIDEO",
                    ),
                )
            }
            if (!contentType.contains("html")) return emptyList()

            // Bounded read: 2 MB of HTML is plenty for meta tags + players.
            val html = response.body.byteStream().readNBytes(2 * 1024 * 1024).decodeToString()
            return parseHtml(html, url)
        }
    }

    private fun parseHtml(html: String, baseUrl: String): List<MediaCandidate> {
        val title = TITLE.find(html)?.groupValues?.get(1)?.trim()
            ?.replace(Regex("\\s+"), " ")?.take(80)
            ?: fileName(baseUrl)
        val found = linkedMapOf<String, MediaCandidate>()

        fun add(raw: String?) {
            var u = raw?.trim()?.replace("&amp;", "&") ?: return
            if (u.startsWith("//")) u = "https:$u"
            if (!u.startsWith("http")) return
            val kind = directKind(u) ?: return
            found.putIfAbsent(u, MediaCandidate(u, title, mimeFor(u), kind))
        }

        // OpenGraph / Twitter cards — the widest net across social sites.
        OG_MEDIA.findAll(html).forEach { add(it.groupValues[1]) }
        // <video src> and nested <source src>.
        VIDEO_SRC.findAll(html).forEach { add(it.groupValues[1]) }
        // Raw media links anywhere in the document (players, JSON blobs).
        RAW_MEDIA.findAll(html).forEach { add(it.value) }

        return found.values.take(8).toList()
    }

    private fun directKind(url: String): String? {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".m3u8") -> "HLS"
            VIDEO_EXT.any { clean.endsWith(it) } -> "VIDEO"
            AUDIO_EXT.any { clean.endsWith(it) } -> "AUDIO"
            else -> null
        }
    }

    private fun mimeFor(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.').lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "m3u8" -> "video/mp2t"
            else -> "application/octet-stream"
        }
    }

    private fun fileName(url: String): String =
        url.substringBefore('?').substringAfterLast('/').ifBlank { "media" }.take(60)

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) LifeOS/0.1"
        val TITLE = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
        val OG_MEDIA = Regex(
            "<meta[^>]+(?:property|name)=[\"'](?:og:video(?::(?:secure_)?url)?|og:audio(?::(?:secure_)?url)?|twitter:player:stream)[\"'][^>]+content=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE,
        )
        val VIDEO_SRC = Regex("<(?:video|source|audio)[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val RAW_MEDIA = Regex("https?://[^\\s\"'<>\\\\]+?\\.(?:mp4|webm|m4v|mp3|m4a|m3u8)(?:\\?[^\\s\"'<>\\\\]*)?")
        val VIDEO_EXT = listOf(".mp4", ".webm", ".m4v", ".mov", ".mkv")
        val AUDIO_EXT = listOf(".mp3", ".m4a", ".ogg", ".oga", ".wav")
    }
}
