package com.lifeos.core.ai.engine.gemma

import android.content.Context
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.datastore.AiConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A downloadable on-device Gemma variant (§8.2). */
data class GemmaVariant(
    val id: String,
    val label: String,
    val sizeHint: String,
    val url: String,
    val fileName: String,
)

sealed interface DownloadState {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class Done(val path: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Pulls Gemma LiteRT models straight from Hugging Face into the models dir.
 * Google's Gemma repos are license-gated: the user accepts the license on
 * huggingface.co once and pastes a read token here (Settings → AI).
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val aiConfigRepository: AiConfigRepository,
    private val dispatchers: DispatcherProvider,
) {

    val variants = listOf(
        GemmaVariant(
            id = "e2b",
            label = "Gemma E2B (fast, ~3 GB)",
            sizeHint = "~3.1 GB",
            url = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
            fileName = "gemma-e2b-int4.task",
        ),
        GemmaVariant(
            id = "e4b",
            label = "Gemma E4B (better, ~4.4 GB)",
            sizeHint = "~4.4 GB",
            url = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task",
            fileName = "gemma-e4b-int4.task",
        ),
    )

    fun installedVariantIds(): Set<String> {
        val dir = context.getExternalFilesDir("models") ?: return emptySet()
        return variants.filter { File(dir, it.fileName).exists() }.map { it.id }.toSet()
    }

    fun download(variant: GemmaVariant): Flow<DownloadState> = flow {
        val dir = context.getExternalFilesDir("models")
            ?: throw IOException("External storage unavailable")
        dir.mkdirs()
        val target = File(dir, variant.fileName)
        val partial = File(dir, variant.fileName + ".part")

        val token = aiConfigRepository.config.first().hfToken
        val request = Request.Builder().url(variant.url).apply {
            if (token.isNotBlank()) header("Authorization", "Bearer $token")
        }.build()

        okHttpClient.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 -> throw IOException(
                    "Hugging Face rejected the download (${response.code}). Accept the Gemma " +
                        "license on huggingface.co and set a read token in Settings → AI.",
                )
                !response.isSuccessful -> throw IOException("Download failed: HTTP ${response.code}")
                else -> {
                    val total = response.body.contentLength()
                    var downloaded = 0L
                    var lastEmitted = 0L
                    partial.outputStream().use { output ->
                        val source = response.body.byteStream()
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastEmitted > 8L * 1024 * 1024) {
                                lastEmitted = downloaded
                                emit(DownloadState.Progress(downloaded, total))
                            }
                        }
                    }
                    if (!partial.renameTo(target)) throw IOException("Could not finalize model file")
                    aiConfigRepository.setOnDeviceModelPath(target.absolutePath)
                    emit(DownloadState.Done(target.absolutePath))
                }
            }
        }
    }.flowOn(dispatchers.io)

    fun delete(variant: GemmaVariant) {
        val dir = context.getExternalFilesDir("models") ?: return
        File(dir, variant.fileName).delete()
        File(dir, variant.fileName + ".part").delete()
    }
}
