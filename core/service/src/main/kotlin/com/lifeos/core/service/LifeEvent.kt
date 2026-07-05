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
}
