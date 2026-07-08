package com.lifeos.feature.reminders.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.lifeos.core.common.log.LifeLogger

/**
 * The ringing engine (§Module 2), modeled on how real alarm-clock apps work:
 * a *foreground service* owns the looping ALARM-stream ringtone + vibration,
 * so sound plays even if the system suppresses the full-screen activity, in
 * silent mode, with the app swiped away, or the screen off. The full-screen
 * AlarmActivity is just the face; Done/Snooze/dismiss stop this service.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RING -> {
                val reminderId = intent.getLongExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, -1L)
                val title = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_TITLE) ?: "Reminder"
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(reminderId, title),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                startRinging()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(reminderId: Long, title: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(ReminderAlarmReceiver.LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderAlarmReceiver.CHANNEL_ID,
                "Reminder alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Exact reminder alarms — loud and on time"
                // Sound comes from this service's MediaPlayer, not the channel.
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
                lightColor = Color.CYAN
            },
        )

        val fullScreen = PendingIntent.getActivity(
            this,
            reminderId.toInt(),
            Intent(this, AlarmActivity::class.java)
                .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
                .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            reminderId.toInt(),
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, ReminderAlarmReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Reminder — tap to open")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Dismiss", stop)
            .setOngoing(true)
            .build()
    }

    private fun startRinging() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { LifeLogger.e(TAG, "Alarm sound failed", it) }
        runCatching {
            vibrator = getSystemService(VibratorManager::class.java).defaultVibrator.also {
                it.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 400, 500, 400, 800), 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            }
        }
    }

    private fun stopRinging() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.lifeos.reminders.RING"
        const val ACTION_STOP = "com.lifeos.reminders.STOP_RING"
        const val NOTIFICATION_ID = 4242
        private const val TAG = "AlarmService"

        fun ring(context: Context, reminderId: Long, title: String?) {
            context.startForegroundService(
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_RING)
                    .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
                    .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }
    }
}
