package com.lifeos.feature.clock.data

import android.os.SystemClock
import com.lifeos.core.service.TimerCommand
import com.lifeos.core.service.TimerCommandBus
import com.lifeos.core.service.TimerKind
import com.lifeos.core.service.TimerNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class StopwatchState(
    val elapsedMs: Long = 0L,
    val running: Boolean = false,
    /** Cumulative totals at each lap press. */
    val laps: List<Long> = emptyList(),
)

/**
 * Clock's stopwatch (§Module 4). Counts from an absolute start instant so tab
 * switches and backgrounding cannot lose time, and mirrors itself into an
 * ongoing notification that keeps counting on its own.
 */
@Singleton
class StopwatchController @Inject constructor(
    private val notifier: TimerNotifier,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    /** Elapsed-realtime instant the current run started from, minus prior time. */
    private var originElapsed = 0L

    private val _state = MutableStateFlow(StopwatchState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            TimerCommandBus.commands.collect { (kind, command) ->
                if (kind != TimerKind.STOPWATCH) return@collect
                when (command) {
                    TimerCommand.TOGGLE -> toggle()
                    TimerCommand.RESET -> reset()
                }
            }
        }
    }

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    fun start() {
        if (_state.value.running) return
        originElapsed = SystemClock.elapsedRealtime() - _state.value.elapsedMs
        _state.value = _state.value.copy(running = true)
        notifier.stopwatch(TimerKind.STOPWATCH, "Stopwatch", _state.value.elapsedMs, running = true)
        startTicker()
    }

    fun pause() {
        if (!_state.value.running) return
        stopTicker()
        val elapsed = elapsedFromOrigin()
        originElapsed = 0L
        _state.value = _state.value.copy(running = false, elapsedMs = elapsed)
        notifier.stopwatch(TimerKind.STOPWATCH, "Stopwatch", elapsed, running = false)
    }

    fun lap() {
        if (!_state.value.running) return
        _state.value = _state.value.copy(laps = _state.value.laps + elapsedFromOrigin())
    }

    fun reset() {
        stopTicker()
        originElapsed = 0L
        _state.value = StopwatchState()
        notifier.cancel(TimerKind.STOPWATCH)
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                _state.value = _state.value.copy(elapsedMs = elapsedFromOrigin())
                delay(37)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun elapsedFromOrigin(): Long =
        if (originElapsed <= 0L) _state.value.elapsedMs else SystemClock.elapsedRealtime() - originElapsed
}
