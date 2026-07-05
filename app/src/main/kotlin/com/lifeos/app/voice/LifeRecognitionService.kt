package com.lifeos.app.voice

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Stub recognizer required for assistant-role eligibility: the framework
 * refuses to list a VoiceInteractionService whose XML has no
 * recognitionService. Actual speech input happens in the capture sheet via
 * the system recognizer, so this only reports "not supported".
 */
class LifeRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
