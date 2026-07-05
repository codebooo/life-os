package com.lifeos.feature.messagecenter.rules

import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeEvent
import java.util.Collections
import javax.inject.Inject

/**
 * Phase 4's end-to-end proof rule — R1's precursor: a shipment tracking
 * number spotted in any notification or capture becomes a provenance-tagged
 * task. Phase 5 upgrades the target to a real package entity + delivery
 * reminder.
 */
class TrackingNumberRule @Inject constructor() : CrossModuleRule {

    override val id: String = "tracking-number-to-task"

    private val seen = Collections.synchronizedSet(mutableSetOf<String>())

    override fun matches(event: LifeEvent): Boolean = textOf(event) != null

    override suspend fun produce(event: LifeEvent): List<LifeAction> {
        val (text, source) = textOf(event) ?: return emptyList()
        return extractTrackingNumbers(text)
            .filter { seen.add(it) } // idempotent: each number acts once per process
            .map { number ->
                LifeAction.CreateTask(
                    title = "Track package $number",
                    source = source,
                )
            }
    }

    private fun textOf(event: LifeEvent): Pair<String, SourceRef>? = when (event) {
        is LifeEvent.NotificationPosted -> {
            val text = listOfNotNull(event.title, event.text).joinToString(" ")
            if (text.isBlank()) null
            else text to SourceRef(LifeModule.MESSAGE_CENTER, event.messageId.toString())
        }
        is LifeEvent.CaptureCreated ->
            event.text?.let { it to SourceRef(LifeModule.CAPTURE, event.captureId.toString()) }
        else -> null
    }

    companion object {
        // DHL Germany paket numbers (12/20 digits) and DHL Express (10 digits with JD prefix
        // variants); deliberately conservative to avoid phone numbers.
        private val PATTERNS = listOf(
            Regex("\\bJJD\\d{16,18}\\b"),
            Regex("\\b(?:JD)?\\d{20}\\b"),
            Regex("\\b003\\d{9}\\b"),
            Regex("\\b(?:sendung|tracking|shipment|paket)[^\\d]{0,20}(\\d{10,20})\\b", RegexOption.IGNORE_CASE),
        )

        fun extractTrackingNumbers(text: String): List<String> =
            PATTERNS.flatMap { pattern ->
                pattern.findAll(text).map { match ->
                    match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: match.value
                }
            }.distinct()
    }
}
