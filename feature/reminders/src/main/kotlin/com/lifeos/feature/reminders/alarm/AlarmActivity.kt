package com.lifeos.feature.reminders.alarm

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.designsystem.theme.LifeOsTheme
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen alarm shown over the lockscreen (§Module 2). Plays a looping
 * ringtone on the ALARM audio stream (ignores silent mode) + vibration, in
 * the activity itself — no foreground service to be refused. Done/Snooze or
 * leaving the screen stops it.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var remindersRepository: RemindersRepository

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        startRinging()

        val reminderId = intent.getLongExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_TITLE) ?: "Reminder"

        setContent {
            LifeOsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            modifier = Modifier.padding(top = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            OutlinedButton(onClick = { snooze(reminderId) }) {
                                Text("Snooze 10 min")
                            }
                            Button(onClick = { dismiss() }) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dismiss() {
        stopRinging()
        finish()
    }

    private fun snooze(reminderId: Long) {
        stopRinging()
        if (reminderId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                remindersRepository.snooze(reminderId, byMinutes = 10)
            }
        }
        finish()
    }

    private fun startRinging() {
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
                setDataSource(this@AlarmActivity, uri)
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

    private companion object {
        const val TAG = "AlarmActivity"
    }
}
