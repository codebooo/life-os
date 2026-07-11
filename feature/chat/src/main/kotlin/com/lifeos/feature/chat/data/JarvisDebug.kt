package com.lifeos.feature.chat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory ring of the last Jarvis turn's internals (§Developer Options).
 * Captures the system snapshot, the raw model output, every parsed tool call
 * and its result, and any errors — surfaced in the chat when the "Jarvis
 * Debugging" developer toggle is on, and copyable back for bug reports.
 */
@Singleton
class JarvisDebug @Inject constructor() {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    fun beginTurn(userText: String) {
        _log.value = listOf("=== Jarvis turn ===", "USER: ${userText.take(400)}")
    }

    fun add(tag: String, message: String) {
        _log.value = (_log.value + "[$tag] ${message.take(2000)}").takeLast(60)
    }

    fun snapshot(): String = _log.value.joinToString("\n")

    fun clear() { _log.value = emptyList() }
}
