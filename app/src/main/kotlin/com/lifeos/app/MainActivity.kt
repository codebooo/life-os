package com.lifeos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifeos.app.ui.LifeOsApp
import com.lifeos.core.designsystem.theme.LifeOsTheme
import com.lifeos.core.service.LifeOsForegroundService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LifeOsForegroundService.start(this)

        setContent {
            LifeOsTheme {
                LifeOsApp()
            }
        }
    }
}
