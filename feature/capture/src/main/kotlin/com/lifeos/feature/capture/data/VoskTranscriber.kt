package com.lifeos.feature.capture.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully offline, open-source speech-to-text via Vosk (§9.3): audio never
 * leaves the device and no Google service is involved. The small English
 * model (~40 MB) is fetched once from alphacephei.com and cached in app
 * storage; recognition then works in airplane mode.
 */
@Singleton
class VoskTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private var model: Model? = null
    private val stopRequested = AtomicBoolean(false)

    private val modelRoot: File get() = File(context.getExternalFilesDir(null), "vosk-model")

    /** The unzipped model directory, or null when not downloaded yet. */
    private fun modelDir(): File? = modelRoot.listFiles()
        ?.firstOrNull { it.isDirectory && File(it, "conf").exists() }

    fun isModelReady(): Boolean = modelDir() != null

    /** Downloads + unzips the small English model; reports 0..100. */
    suspend fun downloadModel(onProgress: (Int) -> Unit): LifeResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                modelRoot.mkdirs()
                val request = Request.Builder().url(MODEL_URL).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext LifeResult.Failure(
                            LifeError.Network("Model download failed: HTTP ${response.code}"),
                        )
                    }
                    val body = response.body
                    val total = body.contentLength()
                    val zipFile = File(modelRoot, "model.zip.part")
                    body.byteStream().use { input ->
                        zipFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (total > 0) onProgress((copied * 100 / total).toInt().coerceAtMost(99))
                            }
                        }
                    }
                    ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val target = File(modelRoot, entry.name)
                            // Zip-slip guard.
                            if (!target.canonicalPath.startsWith(modelRoot.canonicalPath)) {
                                error("Bad zip entry ${entry.name}")
                            }
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
                            }
                            entry = zip.nextEntry
                        }
                    }
                    zipFile.delete()
                    onProgress(100)
                }
                LifeResult.Success(Unit)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                LifeResult.Failure(LifeError.Network("Model download failed: ${t.message}", t))
            }
        }

    fun stopListening() {
        stopRequested.set(true)
    }

    /**
     * Records from the mic and transcribes on-device until ~1.5s of silence
     * after speech, an explicit [stopListening], or 30s. Caller must hold
     * RECORD_AUDIO.
     */
    @SuppressLint("MissingPermission")
    suspend fun listen(): LifeResult<String> = withContext(Dispatchers.IO) {
        val dir = modelDir()
            ?: return@withContext LifeResult.Failure(LifeError.Validation("Offline voice model not downloaded"))
        try {
            val loaded = model ?: Model(dir.absolutePath).also { model = it }
            stopRequested.set(false)
            val recognizer = Recognizer(loaded, SAMPLE_RATE.toFloat())
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SAMPLE_RATE / 2),
            )
            val transcript = StringBuilder()
            try {
                recorder.startRecording()
                val buffer = ByteArray(SAMPLE_RATE / 2) // 250ms chunks
                var silentChunks = 0
                var heardSpeech = false
                val startedAt = System.currentTimeMillis()
                while (!stopRequested.get() && System.currentTimeMillis() - startedAt < MAX_LISTEN_MS) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) break
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        val text = resultText(recognizer.result)
                        if (text.isNotBlank()) {
                            transcript.append(text).append(' ')
                            heardSpeech = true
                            silentChunks = 0
                        }
                    } else {
                        val partial = json.parseToJsonElement(recognizer.partialResult)
                            .jsonObject["partial"]?.jsonPrimitive?.content.orEmpty()
                        if (partial.isBlank()) {
                            if (heardSpeech) silentChunks++
                        } else {
                            heardSpeech = true
                            silentChunks = 0
                        }
                        // ~1.5s of post-speech silence ends the utterance.
                        if (heardSpeech && silentChunks >= 6) break
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
            transcript.append(resultText(recognizer.finalResult))
            recognizer.close()
            val text = transcript.toString().trim()
            if (text.isBlank()) {
                LifeResult.Failure(LifeError.Validation("Didn't catch that — try again closer to the mic"))
            } else {
                LifeResult.Success(text)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LifeLogger.e(TAG, "Vosk transcription failed", t)
            LifeResult.Failure(LifeError.Unknown("Voice recognition failed: ${t.message}", t))
        }
    }

    private fun resultText(resultJson: String): String = try {
        json.parseToJsonElement(resultJson).jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
    } catch (_: Exception) {
        ""
    }

    private companion object {
        const val TAG = "VoskTranscriber"
        const val SAMPLE_RATE = 16_000
        const val MAX_LISTEN_MS = 30_000L
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }
}
