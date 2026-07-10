package com.lifeos.feature.news.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class NewsSource(val id: String, val name: String, val feedUrl: String)

data class NewsArticle(
    val title: String,
    val link: String,
    val source: String,
    val publishedAt: Long,
    val summary: String,
)

/**
 * News (§Module News): straight RSS from credible German outlets rated
 * neutral/lean-left on Ground News — tagesschau (center), ZEIT (center-left),
 * Süddeutsche (center-left), Deutschlandfunk (center), taz (left). Plain
 * feeds, no accounts, no tracking SDKs — just HTTP + XML on-device.
 */
@Singleton
class NewsRepository @Inject constructor() {

    val sources = listOf(
        NewsSource("tagesschau", "tagesschau", "https://www.tagesschau.de/index~rss2.xml"),
        NewsSource("zeit", "ZEIT ONLINE", "https://newsfeed.zeit.de/index"),
        NewsSource("sz", "Süddeutsche", "https://rss.sueddeutsche.de/rss/Topthemen"),
        NewsSource("dlf", "Deutschlandfunk", "https://www.deutschlandfunk.de/nachrichten-100.rss"),
        NewsSource("taz", "taz", "https://taz.de/!p4608;rss/"),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** All feeds in parallel; failures degrade to whatever loaded. */
    suspend fun latest(enabledIds: Set<String>): List<NewsArticle> = coroutineScope {
        sources.filter { it.id in enabledIds }
            .map { source -> async { runCatching { fetch(source) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
            .sortedByDescending { it.publishedAt }
            .take(80)
    }

    private suspend fun fetch(source: NewsSource): List<NewsArticle> = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder().url(source.feedUrl).header("User-Agent", "LifeOS/0.1 RSS").build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            parseRss(response.body.byteStream().readNBytes(2 * 1024 * 1024).decodeToString(), source.name)
        }
    }

    private fun parseRss(xml: String, sourceName: String): List<NewsArticle> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xml.reader())
        }
        val articles = mutableListOf<NewsArticle>()
        var inItem = false
        var tag = ""
        var title = ""
        var link = ""
        var pubDate = ""
        var description = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        inItem = true; title = ""; link = ""; pubDate = ""; description = ""
                    }
                    // Atom <link href=…/>.
                    if (inItem && tag == "link" && link.isBlank()) {
                        parser.getAttributeValue(null, "href")?.let { link = it }
                    }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isEmpty()) Unit
                    else when (tag) {
                        "title" -> if (title.isBlank()) title = text
                        "link" -> if (link.isBlank()) link = text
                        "pubdate", "published", "updated", "dc:date" -> if (pubDate.isBlank()) pubDate = text
                        "description", "summary" -> if (description.isBlank()) description = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    val end = parser.name.lowercase()
                    if (end == "item" || end == "entry") {
                        inItem = false
                        if (title.isNotBlank() && link.isNotBlank()) {
                            articles += NewsArticle(
                                title = unescape(title),
                                link = link,
                                source = sourceName,
                                publishedAt = parseDate(pubDate),
                                summary = unescape(description).replace(Regex("<[^>]+>"), "").take(200),
                            )
                        }
                    }
                    if (end == tag) tag = ""
                }
            }
        }
        return articles
    }

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        DATE_FORMATS.forEach { pattern ->
            runCatching {
                return SimpleDateFormat(pattern, Locale.ENGLISH).parse(raw)!!.time
            }
        }
        return 0L
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&apos;", "'")
        .replace("&#8211;", "–").replace("&#8220;", "“").replace("&#8221;", "”")

    private companion object {
        val DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        )
    }
}
