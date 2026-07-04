package com.lifeos.core.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory publish/subscribe hub shared by every module (§1.5). Single-process
 * by design; Room remains the source of truth, the bus only carries signals.
 */
@Singleton
class LifeEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<LifeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    val events: SharedFlow<LifeEvent> = _events.asSharedFlow()

    suspend fun publish(event: LifeEvent) {
        _events.emit(event)
    }

    /** Non-suspending publish for callers without a coroutine context; drops on overflow. */
    fun tryPublish(event: LifeEvent): Boolean = _events.tryEmit(event)
}
