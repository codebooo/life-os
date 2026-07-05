package com.lifeos.core.service

import com.lifeos.core.model.CaptureKind

/**
 * Cross-module event contract (§1.5). Events are appended here as their
 * producing modules land; Phase 0 ships the bus with the service lifecycle
 * events so the end-to-end path is provable from day one.
 */
sealed interface LifeEvent {

    /** Emitted when the coordination service comes up (used by Phase 0 smoke checks). */
    data class ServiceStarted(val startedAt: Long) : LifeEvent

    // v3 contract — implemented as their producing phases land:
    data class CaptureCreated(val captureId: Long, val kind: CaptureKind, val text: String?) : LifeEvent
    data class NoteSaved(val noteId: Long, val title: String) : LifeEvent
    data class HomeStateChanged(val entityId: String, val state: String) : LifeEvent
    data class ReminderFired(val reminderId: Long, val title: String) : LifeEvent
    data class CalendarEventChanged(val eventId: Long, val title: String, val startsAt: Long) : LifeEvent

    /** A receipt was scanned and extracted (§Module 11, feeds R8). */
    data class ReceiptScanned(
        val docId: Long,
        val merchant: String?,
        val totalCents: Long?,
        val warrantyMonths: Int?,
    ) : LifeEvent

    /** A notification captured by the Message Center listener (§Module 7). */
    data class NotificationPosted(
        val messageId: Long,
        val appPackage: String,
        val title: String?,
        val text: String?,
    ) : LifeEvent
}
