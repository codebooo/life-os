package com.lifeos.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which running clock a notification belongs to. */
enum class TimerKind(val notificationId: Int, val label: String) {
    CLOCK_TIMER(5001, "Timer"),
    STOPWATCH(5002, "Stopwatch"),
    FOCUS_TIMER(5003, "Focus timer"),
}

/** What the buttons on those notifications ask for. */
enum class TimerCommand { TOGGLE, RESET }

/**
 * Notification buttons talk to whichever module owns the clock without either
 * side depending on the other: the receiver posts here, the controller collects.
 */
object TimerCommandBus {
    private val _commands = MutableSharedFlow<Pair<TimerKind, TimerCommand>>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()

    fun send(kind: TimerKind, command: TimerCommand) {
        _commands.tryEmit(kind to command)
    }
}

/** Turns the notification's Pause/Reset taps into [TimerCommandBus] traffic. */
class TimerCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { name ->
            TimerKind.entries.firstOrNull { it.name == name }
        } ?: return
        val command = intent.getStringExtra(EXTRA_COMMAND)?.let { name ->
            TimerCommand.entries.firstOrNull { it.name == name }
        } ?: return
        TimerCommandBus.send(kind, command)
    }

    companion object {
        const val ACTION = "com.lifeos.action.TIMER_COMMAND"
        const val EXTRA_KIND = "kind"
        const val EXTRA_COMMAND = "command"
    }
}

/**
 * Ongoing notifications for anything that counts (§Module 4/§Module 5).
 *
 * The countdown and the stopwatch are drawn by the system's own chronometer
 * (`setUsesChronometer`), anchored to an absolute wall-clock instant. That means
 * the notification keeps ticking correctly with no per-second updates from the
 * app at all — the number stays right even if the process is swapped out, which
 * is exactly what a background timer has to survive.
 */
@Singleton
class TimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /** A counting-down clock; [remainingMs] is measured from now. */
    fun countdown(kind: TimerKind, title: String, remainingMs: Long, running: Boolean) {
        ensureChannel()
        val builder = base(kind, title)
        if (running) {
            builder
                .setWhen(System.currentTimeMillis() + remainingMs)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentText("Counting down")
                .addAction(0, "Pause", commandIntent(kind, TimerCommand.TOGGLE))
        } else {
            builder
                .setShowWhen(false)
                .setContentText("Paused at ${clock(remainingMs)}")
                .addAction(0, "Resume", commandIntent(kind, TimerCommand.TOGGLE))
        }
        builder.addAction(0, "Reset", commandIntent(kind, TimerCommand.RESET))
        manager.notify(kind.notificationId, builder.build())
    }

    /** A counting-up clock; [elapsedMs] is how much has already been counted. */
    fun stopwatch(kind: TimerKind, title: String, elapsedMs: Long, running: Boolean) {
        ensureChannel()
        val builder = base(kind, title)
        if (running) {
            builder
                .setWhen(System.currentTimeMillis() - elapsedMs)
                .setUsesChronometer(true)
                .setContentText("Running")
                .addAction(0, "Pause", commandIntent(kind, TimerCommand.TOGGLE))
        } else {
            builder
                .setShowWhen(false)
                .setContentText("Paused at ${clock(elapsedMs)}")
                .addAction(0, "Resume", commandIntent(kind, TimerCommand.TOGGLE))
        }
        builder.addAction(0, "Reset", commandIntent(kind, TimerCommand.RESET))
        manager.notify(kind.notificationId, builder.build())
    }

    fun cancel(kind: TimerKind) = manager.cancel(kind.notificationId)

    /** A one-shot "your timer is up" note, replacing the ongoing one. */
    fun finished(kind: TimerKind, title: String) {
        ensureChannel()
        manager.notify(
            kind.notificationId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("Time is up")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(openApp())
                .build(),
        )
    }

    private fun base(kind: TimerKind, title: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp())
            .also { it.setSubText(kind.label) }

    private fun commandIntent(kind: TimerKind, command: TimerCommand): PendingIntent {
        val intent = Intent(context, TimerCommandReceiver::class.java)
            .setAction(TimerCommandReceiver.ACTION)
            .putExtra(TimerCommandReceiver.EXTRA_KIND, kind.name)
            .putExtra(TimerCommandReceiver.EXTRA_COMMAND, command.name)
        return PendingIntent.getBroadcast(
            context,
            kind.notificationId * 10 + command.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdowns and stopwatches from Clock and Focus"
                setShowBadge(false)
                enableVibration(false)
            },
        )
    }

    private fun clock(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    private companion object {
        const val CHANNEL_ID = "lifeos_timers"
    }
}
