package com.lifeos.core.service

/**
 * A module's own read-side, exposed to Jarvis on demand (§Module 9).
 *
 * The always-on prompt snapshot stays small on purpose: on-device inference pays
 * for every character, in latency and in output budget. Detail lives behind this
 * interface instead, and is only fetched when the model asks for it with a
 * `[[get: topic]]` line - so a chat about poetry costs nothing extra, while a
 * question about screen time gets the real numbers.
 *
 * Features register providers with `@IntoSet`, which keeps the chat module free
 * of feature-to-feature dependencies.
 */
interface LifeDataProvider {

    /** Lower-case topic the model names, e.g. "screen_time", "plants", "sky". */
    val topic: String

    /** One-line description of what this topic contains, listed in the tool spec. */
    val description: String

    /**
     * Compact, model-readable detail. [query] is whatever followed the topic
     * (a place, a name, a number of days) or null.
     */
    suspend fun read(query: String?): String
}
