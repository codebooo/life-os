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
    data class CalendarEventChanged(val eventId: Long, val title: String, val startsAt: Long, val hasLocation: Boolean = false) : LifeEvent

    /** A receipt was scanned and extracted (§Module 11, feeds R8). */
    data class ReceiptScanned(
        val docId: Long,
        val merchant: String?,
        val totalCents: Long?,
        val warrantyMonths: Int?,
    ) : LifeEvent

    /** New mail stored by the email module (§Module 1). */
    data class EmailReceived(
        val emailId: Long,
        val from: String,
        val subject: String,
        val hasInvoiceSignal: Boolean,
        val inviteStartsAt: Long?,
        val inviteTitle: String?,
    ) : LifeEvent

    /** A notification captured by the Message Center listener (§Module 7). */
    data class NotificationPosted(
        val messageId: Long,
        val appPackage: String,
        val title: String?,
        val text: String?,
    ) : LifeEvent

    /** Arrived at a saved place (§Module Places). */
    data class PlaceEntered(val placeId: Long, val name: String) : LifeEvent

    /** Left a saved place. */
    data class PlaceLeft(val placeId: Long, val name: String) : LifeEvent

    /** A notification the Signals module captured, with its app label. */
    data class SignalCaptured(
        val signalId: Long,
        val appPackage: String,
        val appLabel: String,
        val title: String,
        val text: String,
    ) : LifeEvent

    /** Screen time for today crossed a threshold the rules care about. */
    data class ScreenTimeCrossed(val minutesToday: Int) : LifeEvent
}
