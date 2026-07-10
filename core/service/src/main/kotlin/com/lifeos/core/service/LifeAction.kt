package com.lifeos.core.service

import com.lifeos.core.model.SourceRef

/**
 * Cross-module action contract (§1.5). Executed via [LifeActionDispatcher];
 * grows as target modules land. Every action carries provenance.
 */
sealed interface LifeAction {
    val source: SourceRef

    data class CreateNote(
        val title: String,
        val body: String,
        override val source: SourceRef,
    ) : LifeAction

    data class CreateTask(
        val title: String,
        override val source: SourceRef,
    ) : LifeAction

    data class CreateLogEntry(
        val formName: String,
        val valuesJson: String,
        override val source: SourceRef,
    ) : LifeAction

    data class CreateReminder(
        val title: String,
        val at: Long,
        override val source: SourceRef,
        /** NONE, DAILY, WEEKLY, MONTHLY, or "DAYS:n" for every-n-days cadences. */
        val recurrence: String = "NONE",
    ) : LifeAction

    /** R1: start tracking a shipment (creates the package + delivery reminder). */
    data class TrackPackage(
        val trackingNumber: String,
        override val source: SourceRef,
    ) : LifeAction

    /** R8: file a scanned receipt into Finance (transaction + optional warranty). */
    data class RecordReceipt(
        val docId: Long,
        val merchant: String?,
        val totalCents: Long?,
        val warrantyMonths: Int?,
        override val source: SourceRef,
    ) : LifeAction

    /** R11: run a Home Assistant scene. */
    data class RunHomeScene(
        val sceneId: String,
        override val source: SourceRef,
    ) : LifeAction

    /** R7: a parsed invite becomes a calendar event. */
    data class CreateCalendarEvent(
        val title: String,
        val startsAt: Long,
        val endsAt: Long,
        override val source: SourceRef,
    ) : LifeAction
}
