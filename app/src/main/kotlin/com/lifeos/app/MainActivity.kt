package com.lifeos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.app.ui.LifeOsApp
import com.lifeos.core.datastore.SettingsRepository
import com.lifeos.core.designsystem.theme.LifeOsTheme
import com.lifeos.core.designsystem.theme.PALETTE_DYNAMIC
import com.lifeos.core.service.LifeOsForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Bumped each time the assistant gesture asks for quick capture. */
    private val captureRequests = mutableStateOf(0)

    private val notificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LifeOsForegroundService.start(this)
        requestNotificationsPermission()
        if (isCaptureIntent(intent)) captureRequests.value++

        setContent {
            val palette by settingsRepository.themePalette
                .collectAsStateWithLifecycle(initialValue = PALETTE_DYNAMIC)
            val navBarIds by settingsRepository.navBarItems
                .collectAsStateWithLifecycle(initialValue = emptyList())
            LifeOsTheme(palette = palette) {
                LifeOsApp(captureRequests = captureRequests.value, navBarIds = navBarIds)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isCaptureIntent(intent)) captureRequests.value++
    }

    private fun isCaptureIntent(intent: Intent?): Boolean =
        intent?.action == ACTION_QUICK_CAPTURE || intent?.action == Intent.ACTION_ASSIST

    /** Android 13+ drops notifications silently until this runtime grant exists. */
    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.lifeos.action.QUICK_CAPTURE"
    }
}
