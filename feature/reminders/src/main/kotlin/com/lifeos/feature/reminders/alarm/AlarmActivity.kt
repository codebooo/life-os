package com.lifeos.feature.reminders.alarm

import android.os.Bundle
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
import com.lifeos.core.designsystem.theme.LifeOsTheme
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen alarm face shown over the lockscreen (§Module 2). The ringing
 * itself lives in [AlarmService] (foreground, ALARM stream — silent-mode
 * proof); this screen just presents Done/Snooze and stops the service.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var remindersRepository: RemindersRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

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
        AlarmService.stop(this)
        finish()
    }

    private fun snooze(reminderId: Long) {
        AlarmService.stop(this)
        if (reminderId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                remindersRepository.snooze(reminderId, byMinutes = 10)
            }
        }
        finish()
    }

    override fun onDestroy() {
        // Back-gesture or system dismiss should silence the alarm too.
        AlarmService.stop(this)
        super.onDestroy()
    }
}
