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
}
