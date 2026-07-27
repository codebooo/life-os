package com.lifeos.feature.brick.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.feature.brick.data.BlockDecision
import com.lifeos.feature.brick.data.BrickRepository
import com.lifeos.feature.brick.ui.BlockWallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The blocker (§Module Brick). Watches which app comes to the front and, while
 * a Brick mode is running, replaces blocked apps with a full-screen wall — the
 * same approach every no-root Android blocker uses (Family Link's supervision
 * APIs are only available to Google's own package).
 *
 * Deliberately cheap: one window-state event in, one in-memory map lookup, no
 * screen content read, nothing stored beyond foreground seconds for allowances.
 */
@AndroidEntryPoint
class BrickAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var brickRepository: BrickRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Foreground app + when it came forward, for daily-allowance accounting. */
    private var currentPackage: String? = null
    private var currentSince: Long = 0L
    private var lastWallAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope.launch { brickRepository.refresh() }
        LifeLogger.i(TAG, "Brick blocker connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        // Ignore system UI churn (notification shade, keyboards, launcher chrome).
        if (packageName == "com.android.systemui") return

        val now = System.currentTimeMillis()
        creditPreviousApp(packageName, now)

        when (val decision = brickRepository.decide(packageName)) {
            BlockDecision.Allow -> Unit
            is BlockDecision.Blocked -> wall(decision.profileName, decision.unlockHint, now)
            is BlockDecision.LimitReached -> wall(
                decision.profileName,
                "Daily limit of ${decision.dailyMinutes} min is used up",
                now,
            )
        }
    }

    /** Adds the time the previous app spent in front to its allowance tally. */
    private fun creditPreviousApp(newPackage: String, now: Long) {
        val previous = currentPackage
        if (previous != null && previous != newPackage && currentSince > 0) {
            val seconds = ((now - currentSince) / 1000L).coerceAtMost(3_600L)
            if (seconds > 0) scope.launch { brickRepository.addUsage(previous, seconds) }
        }
        if (previous != newPackage) {
            currentPackage = newPackage
            currentSince = now
        }
    }

    private fun wall(profileName: String, hint: String, now: Long) {
        // Debounce: one wall per second is plenty and avoids relaunch storms.
        if (now - lastWallAt < 1_000L) return
        lastWallAt = now
        scope.launch { brickRepository.noteBlockedAttempt() }
        // Home first so the blocked app leaves the foreground even if the wall
        // is slow to draw; then show the wall over the launcher.
        performGlobalAction(GLOBAL_ACTION_HOME)
        runCatching {
            startActivity(
                BlockWallActivity.intent(this, profileName, hint)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    ),
            )
        }.onFailure { LifeLogger.w(TAG, "Could not show the block wall", it) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: BrickAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null

        /** True when the user has enabled Brick in accessibility settings. */
        fun isEnabledInSettings(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val component = ComponentName(context, BrickAccessibilityService::class.java)
            return enabled.split(':').any {
                it.equals(component.flattenToString(), ignoreCase = true) ||
                    it.equals(component.flattenToShortString(), ignoreCase = true)
            }
        }

        private const val TAG = "BrickBlocker"
    }
}
