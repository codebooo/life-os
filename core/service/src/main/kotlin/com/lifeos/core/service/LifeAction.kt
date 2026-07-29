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

    // ---- Jarvis-facing actions (§Module 9) ---------------------------------
    // Everything Jarvis can DO in a module that is not its own lives here, so
    // the chat module never has to depend on a feature module.

    /** Creates a paste; [burner] routes it to the encrypted burn-after-read backend. */
    data class CreatePaste(
        val title: String,
        val content: String,
        val burner: Boolean,
        val password: String,
        override val source: SourceRef,
    ) : LifeAction

    /** Finds media on a page and queues the best stream into Downloads. */
    data class StartDownload(
        val url: String,
        override val source: SourceRef,
    ) : LifeAction

    /** Turns a Brick mode on; [modeName] is matched loosely against saved modes. */
    data class StartBrickMode(
        val modeName: String,
        override val source: SourceRef,
    ) : LifeAction

    /** Ends the running Brick mode, if its rules allow it. */
    data class StopBrickMode(
        override val source: SourceRef,
    ) : LifeAction

    /** Marks a plant as watered right now. */
    data class WaterPlant(
        val plantName: String,
        override val source: SourceRef,
    ) : LifeAction

    /** Adds a plant to the shelf. */
    data class AddPlant(
        val plantName: String,
        val species: String,
        val waterEveryDays: Int,
        override val source: SourceRef,
    ) : LifeAction

    /** Starts (or restarts) the Focus timer. */
    data class StartFocusTimer(
        val minutes: Int,
        override val source: SourceRef,
    ) : LifeAction

    data class StopFocusTimer(
        override val source: SourceRef,
    ) : LifeAction

    /** Writes a screen-time export into Downloads; format is JSON/CSV_DAYS/CSV_APPS. */
    data class ExportScreenTime(
        val format: String,
        val weekOnly: Boolean,
        override val source: SourceRef,
    ) : LifeAction

    /** Pulls fresh screen-time data from Android's usage stats. */
    data class SyncScreenTime(
        override val source: SourceRef,
    ) : LifeAction

    /** Runs a saved accessibility macro by name. */
    data class RunMacro(
        val macroName: String,
        override val source: SourceRef,
    ) : LifeAction
}
