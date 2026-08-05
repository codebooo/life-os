package com.lifeos.feature.clock.data

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import com.lifeos.core.service.TimerCommand
import com.lifeos.core.service.TimerCommandBus
import com.lifeos.core.service.TimerKind
import com.lifeos.core.service.TimerNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
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

/** The Clock module's countdown, as the whole app sees it. */
data class ClockTimerState(
    val hours: Int = 0,
    val minutes: Int = 5,
    val seconds: Int = 0,
    /** 0 when the timer has never been armed; otherwise what is left. */
    val remainingSeconds: Long = 0,
    val running: Boolean = false,
) {
    val configuredSeconds: Long get() = hours * 3600L + minutes * 60L + seconds
    val armed: Boolean get() = running || remainingSeconds > 0
}

/**
 * Clock's timer, lifted out of the composable (§Module 4).
 *
 * Like Focus's timer it counts from an absolute deadline, so leaving the tab,
 * the app or the screen changes nothing, and it posts an ongoing notification
 * whose countdown the system itself renders.
 */
@Singleton
class ClockTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: TimerNotifier,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var deadlineElapsed = 0L

    private val _state = MutableStateFlow(ClockTimerState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            TimerCommandBus.commands.collect { (kind, command) ->
                if (kind != TimerKind.CLOCK_TIMER) return@collect
                when (command) {
                    TimerCommand.TOGGLE -> toggle()
                    TimerCommand.RESET -> reset()
                }
            }
        }
    }

    fun setHours(value: Int) = editConfig { it.copy(hours = value.coerceIn(0, 99)) }
    fun setMinutes(value: Int) = editConfig { it.copy(minutes = value.coerceIn(0, 59)) }
    fun setSeconds(value: Int) = editConfig { it.copy(seconds = value.coerceIn(0, 59)) }

    /** Preset chips: set the length and clear anything already counting. */
    fun setPresetMinutes(value: Int) {
        stopTicker()
        deadlineElapsed = 0L
        notifier.cancel(TimerKind.CLOCK_TIMER)
        _state.value = _state.value.copy(hours = 0, minutes = value, seconds = 0, remainingSeconds = 0, running = false)
    }

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    fun start() {
        val current = _state.value
        if (current.running) return
        val remaining = if (current.remainingSeconds > 0) current.remainingSeconds else current.configuredSeconds
        if (remaining <= 0) return
        deadlineElapsed = SystemClock.elapsedRealtime() + remaining * 1000L
        _state.value = current.copy(remainingSeconds = remaining, running = true)
        notifier.countdown(TimerKind.CLOCK_TIMER, "Timer", remaining * 1000L, running = true)
        startTicker()
    }

    fun pause() {
        if (!_state.value.running) return
        stopTicker()
        val left = remainingFromDeadline()
        deadlineElapsed = 0L
        _state.value = _state.value.copy(running = false, remainingSeconds = left)
        notifier.countdown(TimerKind.CLOCK_TIMER, "Timer", left * 1000L, running = false)
    }

    fun reset() {
        stopTicker()
        deadlineElapsed = 0L
        _state.value = _state.value.copy(running = false, remainingSeconds = 0)
        notifier.cancel(TimerKind.CLOCK_TIMER)
    }

    /** Config edits are only meaningful while nothing is counting. */
    private fun editConfig(block: (ClockTimerState) -> ClockTimerState) {
        if (_state.value.armed) return
        _state.value = block(_state.value)
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                val left = remainingFromDeadline()
                _state.value = _state.value.copy(remainingSeconds = left)
                if (left <= 0L) {
                    finish()
                    return@launch
                }
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun finish() {
        deadlineElapsed = 0L
        _state.value = _state.value.copy(running = false, remainingSeconds = 0)
        notifier.finished(TimerKind.CLOCK_TIMER, "Timer done")
        runCatching {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
        }
    }

    private fun remainingFromDeadline(): Long {
        if (deadlineElapsed <= 0L) return _state.value.remainingSeconds
        val ms = deadlineElapsed - SystemClock.elapsedRealtime()
        return ((ms + 999) / 1000).coerceAtLeast(0L)
    }
}
