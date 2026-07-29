package com.lifeos.feature.adhd.data

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import com.lifeos.core.database.adhd.FocusDao
import com.lifeos.core.database.adhd.FocusSessionEntity
import com.lifeos.feature.adhd.overlay.TimerOverlayState
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

/** The focus timer as the whole app sees it. */
data class FocusTimerState(
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val running: Boolean = false,
    val overlayVisible: Boolean = false,
)

/**
 * Focus timer that keeps running (§Module 5).
 *
 * The state lives in a singleton rather than in a composable, and the countdown
 * is derived from an absolute deadline, so switching tabs, leaving for Home or
 * letting the process idle no longer resets anything. The overlay reads the
 * same deadline, which is why the two never drift apart.
 */
@Singleton
class FocusTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusDao: FocusDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    /** Absolute end of the running stretch, on the elapsed-realtime clock. */
    private var deadlineElapsed: Long = 0L

    private val _state = MutableStateFlow(FocusTimerState())
    val state = _state.asStateFlow()

    init {
        // The overlay's own close button hides the window; mirror that here so
        // the in-app button label stays truthful.
        scope.launch {
            TimerOverlayState.visible.collect { visible ->
                _state.value = _state.value.copy(overlayVisible = visible)
            }
        }
    }

    fun setTotal(seconds: Int) {
        val total = seconds.coerceIn(1, 24 * 3600)
        stopTicker()
        deadlineElapsed = 0L
        _state.value = _state.value.copy(totalSeconds = total, remainingSeconds = total, running = false)
        pushOverlay()
    }

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    fun start() {
        val current = _state.value
        if (current.remainingSeconds <= 0 || current.running) return
        deadlineElapsed = SystemClock.elapsedRealtime() + current.remainingSeconds * 1000L
        _state.value = current.copy(running = true)
        pushOverlay()
        startTicker()
    }

    fun pause() {
        if (!_state.value.running) return
        stopTicker()
        _state.value = _state.value.copy(running = false, remainingSeconds = remainingFromDeadline())
        deadlineElapsed = 0L
        pushOverlay()
    }

    /** Resets to the configured length; records an abandoned session if it ran. */
    fun reset() {
        val current = _state.value
        val ran = current.running || current.remainingSeconds < current.totalSeconds
        stopTicker()
        deadlineElapsed = 0L
        _state.value = current.copy(running = false, remainingSeconds = current.totalSeconds)
        if (ran) recordSession(current.totalSeconds / 60, completed = false)
        pushOverlay()
    }

    fun setOverlayVisible(visible: Boolean) {
        _state.value = _state.value.copy(overlayVisible = visible)
        if (visible) pushOverlay() else TimerOverlayState.hide(context)
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                val left = remainingFromDeadline()
                _state.value = _state.value.copy(remainingSeconds = left)
                if (left <= 0) {
                    finish()
                    return@launch
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun finish() {
        val total = _state.value.totalSeconds
        deadlineElapsed = 0L
        _state.value = _state.value.copy(running = false, remainingSeconds = 0)
        recordSession(total / 60, completed = true)
        TimerOverlayState.hide(context)
        _state.value = _state.value.copy(overlayVisible = false, remainingSeconds = total)
        runCatching {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1))
        }
    }

    private fun recordSession(minutes: Int, completed: Boolean) {
        scope.launch {
            focusDao.insert(
                FocusSessionEntity(
                    minutes = minutes,
                    startedAt = System.currentTimeMillis() - minutes * 60_000L,
                    completed = completed,
                ),
            )
        }
    }

    private fun remainingFromDeadline(): Int {
        if (deadlineElapsed <= 0L) return _state.value.remainingSeconds
        val ms = deadlineElapsed - SystemClock.elapsedRealtime()
        return ((ms + 999) / 1000).coerceAtLeast(0L).toInt()
    }

    /** Keeps the floating ring in step with whatever the timer is doing. */
    private fun pushOverlay() {
        val current = _state.value
        if (!current.overlayVisible) return
        val deadline = if (current.running) {
            deadlineElapsed
        } else {
            // Paused: hand the overlay a deadline that renders the frozen time.
            SystemClock.elapsedRealtime() + current.remainingSeconds * 1000L
        }
        TimerOverlayState.show(
            context = context,
            deadlineElapsed = deadline,
            totalMs = current.totalSeconds * 1000L,
            running = current.running,
        )
    }
}
