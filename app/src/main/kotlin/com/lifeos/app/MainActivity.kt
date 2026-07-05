package com.lifeos.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifeos.app.ui.LifeOsApp
import com.lifeos.core.designsystem.theme.LifeOsTheme
import com.lifeos.core.service.LifeOsForegroundService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Bumped each time the assistant gesture asks for quick capture. */
    private val captureRequests = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LifeOsForegroundService.start(this)
        if (intent?.action == ACTION_QUICK_CAPTURE) captureRequests.value++

        setContent {
            LifeOsTheme {
                LifeOsApp(captureRequests = captureRequests.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_QUICK_CAPTURE) captureRequests.value++
    }

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.lifeos.action.QUICK_CAPTURE"
    }
}
