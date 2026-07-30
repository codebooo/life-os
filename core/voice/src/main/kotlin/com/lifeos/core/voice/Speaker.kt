package com.lifeos.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lifeos.core.common.log.LifeLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jarvis's voice (§Module Voice).
 *
 * Uses the device's own speech engine, which on a Samsung phone is Samsung TTS
 * and runs on-device once its language is downloaded - nothing is sent anywhere.
 * The engine is created lazily on the first line spoken, because initialising it
 * eagerly costs a few hundred milliseconds of app start for a feature most turns
 * never use.
 */
@Singleton
class Speaker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var engine: TextToSpeech? = null
    private var ready = false

    private val _speaking = MutableStateFlow(false)
    val speaking = _speaking.asStateFlow()

    /** Speaks [text], replacing anything already being said. */
    fun speak(text: String) {
        val clean = text
            .replace(Regex("\\[\\[[^\\]]*]]"), " ")
            .replace(Regex("[*_#`>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_CHARS)
        if (clean.isBlank()) return
        withEngine { tts ->
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
    }

    fun stop() {
        engine?.stop()
        _speaking.value = false
    }

    /** Frees the engine; called when the app trims memory. */
    fun release() {
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
        ready = false
        _speaking.value = false
    }

    private fun withEngine(block: (TextToSpeech) -> Unit) {
        val existing = engine
        if (existing != null && ready) {
            block(existing)
            return
        }
        val pending = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            val tts = engine
            if (!ready || tts == null) {
                LifeLogger.w(TAG, "No usable speech engine on this device")
                return@TextToSpeech
            }
            runCatching { tts.language = Locale.getDefault() }
            block(tts)
        }
        pending.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
            }

            @Deprecated("Required by the platform interface", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
                _speaking.value = false
            }
        })
        engine = pending
    }

    private companion object {
        const val TAG = "Speaker"
        const val UTTERANCE_ID = "jarvis"
        /** Long replies get cut: reading a wall of text aloud is nobody's plan. */
        const val MAX_CHARS = 1200
    }
}
