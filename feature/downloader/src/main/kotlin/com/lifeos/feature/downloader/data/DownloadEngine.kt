package com.lifeos.feature.downloader.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.downloads.DownloadDao
import com.lifeos.core.database.downloads.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams media to the public Downloads collection via MediaStore (no storage
 * permission needed, visible to every player). Direct files stream straight
 * through; HLS playlists are stitched segment-by-segment into one .ts file.
 * Progress lands in Room so the UI (and Jarvis) can watch it.
 */
@Singleton
class DownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun enqueue(candidate: MediaCandidate, sourceUrl: String) {
        scope.launch {
            val id = downloadDao.insert(
                DownloadEntity(
                    sourceUrl = sourceUrl,
                    mediaUrl = candidate.url,
                    title = candidate.title,
                    mimeType = candidate.mimeType,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            runCatching { run(id, candidate) }
                .onFailure { t ->
                    LifeLogger.e(TAG, "Download $id failed", t)
                    downloadDao.setProgress(id, "FAILED", 0, t.message?.take(120))
                }
        }
    }

    private suspend fun run(id: Long, candidate: MediaCandidate) {
        downloadDao.setProgress(id, "RUNNING", 0)
        val safeName = candidate.title.replace(Regex("[^A-Za-z0-9 ._-]"), "_").take(60)
            .ifBlank { "lifeos-download" }

        val resolver = context.contentResolver
        val fileName = if (candidate.kind == "HLS") "$safeName.ts" else safeName
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, candidate.mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore rejected the download target")

        var total = 0L
        try {
            resolver.openOutputStream(target)!!.use { out ->
                if (candidate.kind == "HLS") {
                    val segments = resolveHlsSegments(candidate.url)
                    segments.forEachIndexed { index, segmentUrl ->
                        client.newCall(Request.Builder().url(segmentUrl).header("User-Agent", UA).build())
                            .execute().use { response ->
                                check(response.isSuccessful) { "HTTP ${response.code} on segment" }
                                total += response.body.byteStream().copyTo(out)
                            }
                        downloadDao.setProgress(id, "RUNNING", ((index + 1) * 100 / segments.size))
                    }
                } else {
                    client.newCall(Request.Builder().url(candidate.url).header("User-Agent", UA).build())
                        .execute().use { response ->
                            check(response.isSuccessful) { "HTTP ${response.code}" }
                            val length = response.body.contentLength()
                            val input = response.body.byteStream()
                            val buffer = ByteArray(256 * 1024)
                            var lastPercent = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                total += read
                                if (length > 0) {
                                    val percent = (total * 100 / length).toInt()
                                    if (percent >= lastPercent + 5) {
                                        lastPercent = percent
                                        downloadDao.setProgress(id, "RUNNING", percent)
                                    }
                                }
                            }
                        }
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            downloadDao.setDone(id, total, target.toString())
        } catch (t: Throwable) {
            resolver.delete(target, null, null)
            throw t
        }
    }

    /** Master playlists pick the highest-bandwidth variant; media playlists list segments. */
    private fun resolveHlsSegments(playlistUrl: String): List<String> {
        fun fetch(u: String): String =
            client.newCall(Request.Builder().url(u).header("User-Agent", UA).build()).execute()
                .use { it.body.string() }

        fun absolute(base: String, line: String): String = when {
            line.startsWith("http") -> line
            line.startsWith("/") -> base.substringBefore("/", missingDelimiterValue = base)
                .let { Regex("(https?://[^/]+)").find(base)!!.groupValues[1] } + line
            else -> base.substringBeforeLast('/') + "/" + line
        }

        var url = playlistUrl
        var body = fetch(url)
        if ("#EXT-X-STREAM-INF" in body) {
            // Master playlist: take the variant with the highest BANDWIDTH.
            val variants = Regex("#EXT-X-STREAM-INF[^\\n]*BANDWIDTH=(\\d+)[^\\n]*\\n([^#\\n][^\\n]*)")
                .findAll(body)
                .map { it.groupValues[1].toLong() to it.groupValues[2].trim() }
                .toList()
            val best = variants.maxByOrNull { it.first } ?: error("Empty HLS master playlist")
            url = absolute(url, best.second)
            body = fetch(url)
        }
        val segments = body.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { absolute(url, it.trim()) }
        check(segments.isNotEmpty()) { "No segments in HLS playlist" }
        check(segments.size <= 4000) { "Stream too long (${segments.size} segments)" }
        return segments
    }

    private companion object {
        const val TAG = "DownloadEngine"
        const val UA = "Mozilla/5.0 (Linux; Android 14) LifeOS/0.1"
    }
}
