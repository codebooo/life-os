package com.lifeos.feature.downloader.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a static page scan. */
data class ExtractResult(
    val candidates: List<MediaCandidate>,
    /**
     * True when the page drives its player through obfuscated, session-bound
     * JavaScript, so only [PlayerResolver] can produce a working link.
     */
    val playerNeeded: Boolean = false,
)

/** A downloadable media stream discovered on a page. */
data class MediaCandidate(
    val url: String,
    val title: String,
    val mimeType: String,
    val kind: String, // VIDEO, AUDIO, HLS
    /** Request headers the stream needs (Referer, Cookie, User-Agent). */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Generic on-device media extractor (§Module Downloader). Given any URL it
 * finds direct media: the URL itself when it already serves video/audio, or
 * streams declared in the page - OpenGraph (`og:video`), `<video>`/`<source>`
 * tags, player JSON blobs, HLS playlists and raw media links - plus a handful
 * of site helpers for pages that hide the stream behind one extra hop
 * (Reddit, LinkedIn, Instagram/Threads embeds, Vimeo player config, kt_player
 * tube sites such as ThisVid) and one level of iframe following.
 *
 * No site-specific accounts, no third-party API - everything runs on the phone.
 */
@Singleton
class MediaExtractor @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun extract(pageUrl: String): ExtractResult {
        val url = normalize(pageUrl.trim())
        require(url.startsWith("http")) { "Enter a full http(s) URL" }

        // The URL itself may already be the media file.
        directKind(url)?.let { kind ->
            return ExtractResult(listOf(MediaCandidate(url, fileName(url), mimeFor(url), kind)))
        }

        val found = linkedMapOf<String, MediaCandidate>()
        siteHelper(url)?.let { helper -> found.putAll(helper.associateBy { it.url }) }
        if (found.size >= 4) return ExtractResult(found.values.toList())

        val page = fetch(url) ?: return ExtractResult(found.values.toList())
        if (page.mediaType.startsWith("video/") || page.mediaType.startsWith("audio/")) {
            return ExtractResult(
                listOf(
                    MediaCandidate(
                        url = url,
                        title = fileName(url),
                        mimeType = page.mediaType,
                        kind = if (page.mediaType.startsWith("audio/")) "AUDIO" else "VIDEO",
                    ),
                ),
            )
        }
        if (!page.mediaType.contains("html") && !page.mediaType.contains("json")) {
            return ExtractResult(found.values.toList())
        }

        // Scrambled player links cannot be repaired here; the page's own player
        // has to resolve them (PlayerResolver).
        val playerNeeded = KVS_MARKER.containsMatchIn(page.body)

        found.putAll(parseHtml(page.body, url).associateBy { it.url })

        // One level of iframe following: most file hosts and embeds put the real
        // player one frame deeper.
        if (found.isEmpty() && !playerNeeded) {
            IFRAME.findAll(page.body)
                .map { absolute(it.groupValues[1], url) }
                .filter { it.startsWith("http") && it != url }
                .take(3)
                .forEach { frame ->
                    val inner = fetch(frame) ?: return@forEach
                    if (inner.mediaType.contains("html") || inner.mediaType.contains("json")) {
                        found.putAll(parseHtml(inner.body, frame).associateBy { it.url })
                    }
                }
        }

        return ExtractResult(found.values.take(10).toList(), playerNeeded)
    }

    // ---- site helpers ------------------------------------------------------

    private fun siteHelper(url: String): List<MediaCandidate>? {
        val host = hostOf(url)
        return when {
            host.endsWith("reddit.com") -> reddit(url)
            host.endsWith("linkedin.com") -> viaPage(url) { html, base -> linkedIn(html, base) }
            host.endsWith("instagram.com") || host.endsWith("threads.net") -> instagramEmbed(url)
            host.endsWith("vimeo.com") -> vimeo(url)
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }

    /** Reddit exposes the real streams in the post JSON. */
    private fun reddit(url: String): List<MediaCandidate> {
        val jsonUrl = url.substringBefore('?').trimEnd('/') + "/.json"
        val body = fetch(jsonUrl)?.body ?: return emptyList()
        val title = Regex("\"title\":\"(.*?)\"").find(body)?.groupValues?.get(1)?.let(::unescape)?.take(80)
            ?: "reddit"
        val found = linkedMapOf<String, MediaCandidate>()
        Regex("\"fallback_url\":\"(.*?)\"").findAll(body).forEach { match ->
            val stream = unescape(match.groupValues[1]).substringBefore("?source")
            found.putIfAbsent(stream, MediaCandidate(stream, title, "video/mp4", "VIDEO"))
        }
        Regex("\"hls_url\":\"(.*?)\"").findAll(body).forEach { match ->
            val stream = unescape(match.groupValues[1])
            found.putIfAbsent(stream, MediaCandidate(stream, title, "video/mp2t", "HLS"))
        }
        return found.values.toList()
    }

    /** LinkedIn post pages carry progressive mp4 URLs in an embedded JSON blob. */
    private fun linkedIn(html: String, base: String): List<MediaCandidate> {
        val title = TITLE.find(html)?.groupValues?.get(1)?.trim()?.take(80) ?: fileName(base)
        val found = linkedMapOf<String, MediaCandidate>()
        listOf(
            Regex("\"progressiveUrl\":\"(.*?)\""),
            Regex("\"progressiveStreams\".*?\"streamingLocations\":\\[\\{\"url\":\"(.*?)\""),
            Regex("(https://dms\\.licdn\\.com/playlist/[^\"\\\\]+)"),
        ).forEach { regex ->
            regex.findAll(html).forEach { match ->
                val stream = unescape(match.groupValues.last())
                val kind = if (stream.contains(".m3u8")) "HLS" else "VIDEO"
                found.putIfAbsent(stream, MediaCandidate(stream, title, mimeFor(stream), kind))
            }
        }
        return found.values.toList()
    }

    /** The embed page is public even where the post page is script-rendered. */
    private fun instagramEmbed(url: String): List<MediaCandidate> {
        val base = url.substringBefore('?').trimEnd('/')
        val embed = "$base/embed/captioned/"
        val page = fetch(embed) ?: return emptyList()
        return parseHtml(page.body, embed)
    }

    /** Vimeo's player config lists every progressive rendition. */
    private fun vimeo(url: String): List<MediaCandidate> {
        val id = Regex("""vimeo\.com/(?:video/)?(\d+)""").find(url)?.groupValues?.get(1) ?: return emptyList()
        val config = fetch("https://player.vimeo.com/video/$id/config")?.body ?: return emptyList()
        val title = Regex("\"title\":\"(.*?)\"").find(config)?.groupValues?.get(1)?.let(::unescape)?.take(80)
            ?: "vimeo-$id"
        val found = linkedMapOf<String, MediaCandidate>()
        Regex("\"url\":\"(https:[^\"]+?\\.mp4[^\"]*)\"").findAll(config).forEach { match ->
            val stream = unescape(match.groupValues[1])
            found.putIfAbsent(stream, MediaCandidate(stream, title, "video/mp4", "VIDEO"))
        }
        Regex("\"hls\".*?\"url\":\"(https:[^\"]+?\\.m3u8[^\"]*)\"", RegexOption.DOT_MATCHES_ALL)
            .find(config)?.let { match ->
                val stream = unescape(match.groupValues[1])
                found.putIfAbsent(stream, MediaCandidate(stream, title, "video/mp2t", "HLS"))
            }
        return found.values.toList()
    }

    private fun viaPage(url: String, block: (String, String) -> List<MediaCandidate>): List<MediaCandidate> {
        val page = fetch(url) ?: return emptyList()
        if (!page.mediaType.contains("html")) return emptyList()
        return block(page.body, url)
    }

    // ---- generic parsing ---------------------------------------------------

    internal fun parseHtml(html: String, baseUrl: String): List<MediaCandidate> {
        val title = TITLE.find(html)?.groupValues?.get(1)?.trim()
            ?.replace(Regex("\\s+"), " ")?.take(80)
            ?: fileName(baseUrl)
        val found = linkedMapOf<String, MediaCandidate>()

        fun add(raw: String?, label: String = title) {
            var candidate = raw?.trim()?.let(::unescape) ?: return
            if (candidate.startsWith("//")) candidate = "https:$candidate"
            if (candidate.startsWith("/")) candidate = absolute(candidate, baseUrl)
            if (!candidate.startsWith("http")) return
            if (isJunk(candidate)) return
            val kind = directKind(candidate) ?: return
            found.putIfAbsent(candidate, MediaCandidate(candidate, label, mimeFor(candidate), kind))
        }

        // OpenGraph / Twitter cards - the widest net across social sites.
        OG_MEDIA.findAll(html).forEach { add(it.groupValues[1]) }
        // <video src> and nested <source src>.
        VIDEO_SRC.findAll(html).forEach { add(it.groupValues[1]) }
        // Player JSON keys used across platforms and file hosts.
        JSON_MEDIA.findAll(html).forEach { add(it.groupValues[1]) }
        // Raw media links anywhere in the document (players, JSON blobs).
        RAW_MEDIA.findAll(html).forEach { add(unescape(it.value)) }

        return found.values.take(10).toList()
    }

    private data class Page(val body: String, val mediaType: String)

    private fun fetch(url: String): Page? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            val contentType = response.header("Content-Type").orEmpty().substringBefore(';').ifBlank { "text/html" }
            if (contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                return@use Page("", contentType)
            }
            // Bounded read: 3 MB of HTML/JSON is plenty for meta tags + players.
            val body = response.body?.byteStream()?.readNBytes(3 * 1024 * 1024)?.decodeToString().orEmpty()
            Page(body, contentType)
        }
    }.getOrNull()

    /** Rewrites known short/mirror hosts to the page the extractor can read. */
    private fun normalize(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
            .replace("//twitter.com/", "//x.com/")
            .replace("//m.youtube.com/", "//www.youtube.com/")
            .replace("//mobile.twitter.com/", "//x.com/")
        url.startsWith("www.") -> "https://$url"
        url.contains('.') && !url.contains(' ') -> "https://$url"
        else -> url
    }

    /** Skips teasers, sprites and ad creatives that are not the real stream. */
    private fun isJunk(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return JUNK.any { it in clean }
    }

    private fun directKind(url: String): String? {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".m3u8") || clean.contains(".m3u8") -> "HLS"
            clean.endsWith(".mpd") -> "HLS"
            VIDEO_EXT.any { clean.endsWith(it) } -> "VIDEO"
            AUDIO_EXT.any { clean.endsWith(it) } -> "AUDIO"
            clean.contains("/get_file/") -> "VIDEO"
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
            "wav", "flac" -> "audio/wav"
            "m3u8", "mpd" -> "video/mp2t"
            else -> "video/mp4"
        }
    }

    private fun fileName(url: String): String =
        url.substringBefore('?').substringAfterLast('/').ifBlank { "media" }.take(60)

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    private fun absolute(path: String, baseUrl: String): String = runCatching {
        java.net.URI(baseUrl).resolve(path).toString()
    }.getOrDefault(path)

    private fun unescape(value: String): String = value
        .replace("\\/", "/")
        .replace("&amp;", "&")
        .replace("\\u0026", "&")
        .replace("\\u002F", "/")
        .trim()

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) LifeOS/0.1"
        val TITLE = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
        val OG_MEDIA = Regex(
            "<meta[^>]+(?:property|name)=[\"'](?:og:video(?::(?:secure_)?url)?|og:audio(?::(?:secure_)?url)?|twitter:player:stream)[\"'][^>]+content=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE,
        )
        val VIDEO_SRC = Regex("<(?:video|source|audio)[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val IFRAME = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val JSON_MEDIA = Regex(
            "\"(?:contentUrl|playbackUrl|progressiveUrl|hlsUrl|hlsManifestUrl|videoUrl|video_url|media_url|streamUrl|file|src)\"\\s*:\\s*\"([^\"]+)\"",
            RegexOption.IGNORE_CASE,
        )
        val JUNK = listOf("preview", "thumb", "sprite", "timeline", "trailer", "advert", "/ads/")
        val KVS_MARKER = Regex("license_code\\s*:|function/0/", RegexOption.IGNORE_CASE)
        val RAW_MEDIA = Regex("https?://[^\\s\"'<>\\\\]+?\\.(?:mp4|webm|m4v|mp3|m4a|flac|m3u8)(?:\\?[^\\s\"'<>\\\\]*)?")
        val VIDEO_EXT = listOf(".mp4", ".webm", ".m4v", ".mov", ".mkv")
        val AUDIO_EXT = listOf(".mp3", ".m4a", ".ogg", ".oga", ".wav", ".flac")
    }
}
