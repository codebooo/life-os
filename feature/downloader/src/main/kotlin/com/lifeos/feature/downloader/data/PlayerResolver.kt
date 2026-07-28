package com.lifeos.feature.downloader.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lifeos.core.common.log.LifeLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Player-driven resolver (§Module Downloader).
 *
 * Some sites hand the player a scrambled link that only its own obfuscated
 * JavaScript can turn into a real file URL, and the resulting link is bound to
 * the browsing session (cookies plus a Referer check). Reimplementing each
 * site's scrambler is a losing game, so LifeOS instead lets the page's own
 * player do the work in an offscreen WebView and records the media request it
 * makes, together with the cookies needed to fetch it.
 *
 * Nothing is shown to the user and nothing leaves the phone: the WebView is
 * created, driven and destroyed inside this call.
 */
@Singleton
class PlayerResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun resolve(pageUrl: String, timeoutMs: Long = 30_000): List<MediaCandidate> =
        withTimeoutOrNull(timeoutMs) { drive(pageUrl) } ?: emptyList()

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun drive(pageUrl: String): List<MediaCandidate> {
        val seen = linkedMapOf<String, MediaCandidate>()
        var title = pageUrl.substringAfterLast('/').ifBlank { "media" }

        val webView = withContext(Dispatchers.Main) {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadsImagesAutomatically = false
                settings.userAgentString = BROWSER_UA
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (isMedia(url)) {
                            seen.putIfAbsent(url, MediaCandidate(url, title, mimeFor(url), kindFor(url)))
                        }
                        return null
                    }
                }
                loadUrl(pageUrl)
            }
        }

        try {
            // Give the page time to boot its player, then nudge it into playing
            // so the media request actually happens.
            repeat(POLLS) { round ->
                delay(POLL_MS)
                withContext(Dispatchers.Main) {
                    webView.title?.takeIf { it.isNotBlank() }?.let { title = it.take(80) }
                    if (round >= 1) webView.evaluateJavascript(NUDGE_JS) { result ->
                        result?.split("|")
                            ?.map { it.trim('"', '\\', ' ') }
                            ?.filter { isMedia(it) }
                            ?.forEach { url ->
                                seen.putIfAbsent(url, MediaCandidate(url, title, mimeFor(url), kindFor(url)))
                            }
                    }
                }
                // One extra round after the first hit picks up alternate qualities.
                if (seen.isNotEmpty() && round >= 2) return@repeat
            }
            val cookies = withContext(Dispatchers.Main) {
                CookieManager.getInstance().getCookie(pageUrl).orEmpty()
            }
            val headers = buildMap {
                put("Referer", pageUrl)
                put("User-Agent", BROWSER_UA)
                if (cookies.isNotBlank()) put("Cookie", cookies)
            }
            return seen.values
                .map { it.copy(title = title, headers = headers) }
                .sortedByDescending { qualityScore(it.url) }
                .take(6)
        } catch (t: Throwable) {
            LifeLogger.e(TAG, "Player resolve failed for $pageUrl", t)
            return emptyList()
        } finally {
            withContext(Dispatchers.Main) {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    private fun isMedia(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val clean = url.substringBefore('?').lowercase()
        if (JUNK.any { it in clean }) return false
        return MEDIA_EXT.any { clean.endsWith(it) } ||
            clean.contains(".m3u8") ||
            clean.contains("/get_file/")
    }

    private fun kindFor(url: String): String {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.contains(".m3u8") -> "HLS"
            clean.endsWith(".mp3") || clean.endsWith(".m4a") -> "AUDIO"
            else -> "VIDEO"
        }
    }

    private fun mimeFor(url: String): String = when {
        url.contains(".m3u8") -> "video/mp2t"
        url.contains(".webm") -> "video/webm"
        url.contains(".mp3") -> "audio/mpeg"
        else -> "video/mp4"
    }

    /** Rough ordering so the best rendition is offered first. */
    private fun qualityScore(url: String): Int = Regex("""(\d{3,4})p""").find(url)
        ?.groupValues?.get(1)?.toIntOrNull()
        ?: 1080

    private companion object {
        const val TAG = "PlayerResolver"
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Mobile Safari/537.36"
        const val POLL_MS = 1_200L
        const val POLLS = 18
        val MEDIA_EXT = listOf(".mp4", ".m4v", ".webm", ".mov", ".mkv", ".mp3", ".m4a")
        val JUNK = listOf(
            "preview", "thumb", "sprite", "timeline", "trailer", "/ads/", "advert",
            "logo", "watermark", ".jpg", ".png", ".gif", ".vtt", ".css", ".js",
        )

        /**
         * Reads whatever the player has already resolved and, if nothing is
         * playing yet, mutes and starts it so the stream request is issued.
         */
        val NUDGE_JS = """
            (function() {
              var out = [];
              var media = document.querySelectorAll('video, audio, source');
              for (var i = 0; i < media.length; i++) {
                if (media[i].src) out.push(media[i].src);
                if (media[i].currentSrc) out.push(media[i].currentSrc);
              }
              var players = document.querySelectorAll('video, audio');
              for (var j = 0; j < players.length; j++) {
                try { players[j].muted = true; players[j].play(); } catch (e) {}
              }
              if (players.length === 0) {
                var starters = ['.fp-ui', '.vjs-big-play-button', '.play-button', '.player',
                                '#kt_player', '#player', '[class*=play]'];
                for (var k = 0; k < starters.length; k++) {
                  var el = document.querySelector(starters[k]);
                  if (el) { try { el.click(); } catch (e) {} break; }
                }
              }
              return out.join('|');
            })();
        """.trimIndent()
    }
}
