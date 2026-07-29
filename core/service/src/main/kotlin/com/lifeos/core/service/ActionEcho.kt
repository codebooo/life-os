package com.lifeos.core.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * A one-slot mailbox for action results that are worth quoting back (§1.5).
 *
 * [LifeActionHandler] only returns a row id, but some actions produce something
 * the user actually needs - a paste URL, a saved file name. Handlers drop it
 * here and the caller (Jarvis, the UI) reads it right after dispatching, which
 * avoids widening the action contract for every one-off payload.
 */
@Singleton
class ActionEcho @Inject constructor() {

    @Volatile
    var lastUrl: String? = null
        private set

    @Volatile
    var lastFileName: String? = null
        private set

    fun url(value: String?) {
        lastUrl = value
    }

    fun fileName(value: String?) {
        lastFileName = value
    }
}
