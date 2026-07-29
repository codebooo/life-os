package com.lifeos.feature.agentic.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lifeos.core.ai.macro.MacroStep
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.core.common.log.LifeLogger
import kotlinx.coroutines.delay

/**
 * The macro executor (§Module 12). Bound only while the user has enabled
 * "LifeOS Macros" in system accessibility settings; steps run exclusively
 * from an explicit Run tap in the app — never on events.
 */
@dagger.hilt.android.AndroidEntryPoint
class LifeAccessibilityService : AccessibilityService() {

    /** Lets LIFEOS_* steps act through the normal cross-module contract. */
    @javax.inject.Inject
    lateinit var dispatcherHolder: LifeActionDispatcher

    private val actionDispatcher: LifeActionDispatcher?
        get() = if (::dispatcherHolder.isInitialized) dispatcherHolder else null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Runs validated IR steps sequentially; returns a failure description or null. */
    suspend fun run(steps: List<MacroStep>): String? {
        steps.forEachIndexed { index, step ->
            val failure = perform(step)
            if (failure != null) return "Step ${index + 1} (${step.action}): $failure"
            delay(if (step.action == "WAIT" || step.action == "WAIT_FOR") 0 else 350)
        }
        return null
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun perform(step: MacroStep): String? = when (step.action) {
        // ---- apps and navigation ------------------------------------------
        "LAUNCH" -> launchApp(step.target.orEmpty())
        "LAUNCH_URL" -> openUri(step.target.orEmpty())
        "OPEN_SETTINGS" -> openSettings(step.target.orEmpty())
        "DIAL" -> openUri("tel:" + step.target.orEmpty().filter { it.isDigit() || it == '+' })
        "BACK" -> global(GLOBAL_ACTION_BACK, "BACK")
        "HOME" -> global(GLOBAL_ACTION_HOME, "HOME")
        "RECENTS" -> global(GLOBAL_ACTION_RECENTS, "RECENTS")
        "NOTIFICATIONS" -> global(GLOBAL_ACTION_NOTIFICATIONS, "NOTIFICATIONS")
        "QUICK_SETTINGS" -> global(GLOBAL_ACTION_QUICK_SETTINGS, "QUICK_SETTINGS")
        "POWER_DIALOG" -> global(GLOBAL_ACTION_POWER_DIALOG, "POWER_DIALOG")
        "LOCK_SCREEN" -> global(GLOBAL_ACTION_LOCK_SCREEN, "LOCK_SCREEN")
        "SCREENSHOT" -> global(GLOBAL_ACTION_TAKE_SCREENSHOT, "SCREENSHOT")
        "SPLIT_SCREEN" -> global(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, "SPLIT_SCREEN")

        // ---- touching the screen ------------------------------------------
        "CLICK" -> clickText(step.target.orEmpty())
        "CLICK_DESC" -> clickByDescription(step.target.orEmpty())
        "CLICK_ID" -> clickByViewId(step.target.orEmpty())
        "LONG_CLICK" -> longClickText(step.target.orEmpty())
        "INPUT" -> inputText(step.text.orEmpty())
        "CLEAR_INPUT" -> inputText("")
        "SCROLL_FORWARD" -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        "SCROLL_BACKWARD" -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        "SWIPE_UP" -> swipe(0f, 0.7f, 0f, 0.3f)
        "SWIPE_DOWN" -> swipe(0f, 0.3f, 0f, 0.7f)
        "SWIPE_LEFT" -> swipe(0.8f, 0.5f, 0.2f, 0.5f)
        "SWIPE_RIGHT" -> swipe(0.2f, 0.5f, 0.8f, 0.5f)

        // ---- timing --------------------------------------------------------
        "WAIT" -> {
            delay((step.delayMs ?: 1_000).coerceIn(0, 10_000))
            null
        }

        "WAIT_FOR" -> waitForText(step.target.orEmpty())

        // ---- device --------------------------------------------------------
        "MEDIA_PLAY_PAUSE" -> media(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        "MEDIA_NEXT" -> media(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
        "MEDIA_PREV" -> media(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        "VOLUME_UP" -> volume(android.media.AudioManager.ADJUST_RAISE)
        "VOLUME_DOWN" -> volume(android.media.AudioManager.ADJUST_LOWER)
        "VOLUME_MUTE" -> volume(android.media.AudioManager.ADJUST_MUTE)
        "TORCH_ON" -> torch(true)
        "TORCH_OFF" -> torch(false)
        "VIBRATE" -> {
            runCatching {
                getSystemService(android.os.VibratorManager::class.java).defaultVibrator
                    .vibrate(android.os.VibrationEffect.createOneShot(200, 180))
            }.exceptionOrNull()?.message
        }

        "CLIPBOARD" -> {
            runCatching {
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("LifeOS", step.text.orEmpty()))
            }.exceptionOrNull()?.message
        }

        "SHARE" -> shareText(step.text.orEmpty())
        "TOAST" -> {
            android.widget.Toast.makeText(this, step.text.orEmpty(), android.widget.Toast.LENGTH_SHORT).show()
            null
        }

        // ---- LifeOS itself -------------------------------------------------
        "LIFEOS_TASK" -> dispatch(
            LifeAction.CreateTask(step.text.orEmpty().take(100), SOURCE),
            "task title",
            step.text,
        )

        "LIFEOS_NOTE" -> {
            val title = step.text.orEmpty().substringBefore('|').trim()
            val body = step.text.orEmpty().substringAfter('|', "").trim()
            dispatch(LifeAction.CreateNote(title.take(60), body.ifBlank { title }, SOURCE), "note title", title)
        }

        "LIFEOS_TIMER" -> dispatch(
            LifeAction.CreateReminder(
                "Timer",
                System.currentTimeMillis() + (step.delayMs ?: 300_000L),
                SOURCE,
            ),
            "timer length",
            step.delayMs?.toString(),
        )

        "LIFEOS_FOCUS" -> dispatch(
            LifeAction.StartFocusTimer(((step.delayMs ?: 1_500_000L) / 60_000L).toInt().coerceAtLeast(1), SOURCE),
            "focus length",
            step.delayMs?.toString(),
        )

        "LIFEOS_BRICK_ON" -> dispatch(
            LifeAction.StartBrickMode(step.target.orEmpty(), SOURCE),
            "Brick mode name",
            step.target,
        )

        "LIFEOS_BRICK_OFF" -> dispatch(LifeAction.StopBrickMode(SOURCE), "", "ok")

        else -> "Unsupported action ${step.action}"
    }

    private fun global(action: Int, label: String): String? =
        if (performGlobalAction(action)) null else "$label was refused by the system"

    private suspend fun dispatch(action: LifeAction, argName: String, argValue: String?): String? {
        if (argName.isNotEmpty() && argValue.isNullOrBlank()) return "missing $argName"
        val dispatcher = actionDispatcher ?: return "LifeOS action dispatcher is not available"
        return when (val result = dispatcher.dispatch(action)) {
            is LifeResult.Failure -> result.error.message
            is LifeResult.Success -> null
        }
    }

    private fun openUri(uri: String): String? {
        if (uri.isBlank()) return "no link given"
        return runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            null
        }.getOrElse { "nothing can open \"$uri\"" }
    }

    private fun openSettings(section: String): String? {
        val action = when (section.trim().lowercase()) {
            "wifi", "wi-fi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            "nfc" -> android.provider.Settings.ACTION_NFC_SETTINGS
            "apps" -> android.provider.Settings.ACTION_APPLICATION_SETTINGS
            "battery" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            "display", "brightness" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> android.provider.Settings.ACTION_SOUND_SETTINGS
            "location" -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "dnd", "do not disturb" -> android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            "airplane" -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "" -> android.provider.Settings.ACTION_SETTINGS
            else -> android.provider.Settings.ACTION_SETTINGS
        }
        return runCatching {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }.getOrElse { "that settings page is not available" }
    }

    private fun shareText(text: String): String? = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    }.getOrElse { "sharing failed" }

    private fun media(keyCode: Int): String? = runCatching {
        val manager = getSystemService(android.media.AudioManager::class.java)
        manager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode),
        )
        manager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode),
        )
        null
    }.getOrElse { "no media session responded" }

    private fun volume(direction: Int): String? = runCatching {
        getSystemService(android.media.AudioManager::class.java).adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            direction,
            android.media.AudioManager.FLAG_SHOW_UI,
        )
        null
    }.getOrElse { "volume change was refused" }

    private fun torch(on: Boolean): String? = runCatching {
        val manager = getSystemService(android.hardware.camera2.CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "this phone has no torch"
        manager.setTorchMode(id, on)
        null
    }.getOrElse { "the torch is in use by another app" }

    private fun scroll(action: Int): String? {
        val root = rootInActiveWindow ?: return "No active window"
        val scrollable = findScrollable(root) ?: return "nothing scrollable on screen"
        return if (scrollable.performAction(action)) null else "scroll was refused"
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) {
            findScrollable(node.getChild(index))?.let { return it }
        }
        return null
    }

    /** Swipes using fractions of the screen, so it works on any display size. */
    private suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float): String? {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val path = android.graphics.Path().apply {
            moveTo(if (fromX == 0f) width / 2f else width * fromX, height * fromY)
            lineTo(if (toX == 0f) width / 2f else width * toX, height * toY)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 250))
            .build()
        return if (dispatchGesture(gesture, null, null)) {
            delay(300)
            null
        } else {
            "gesture was refused"
        }
    }

    private suspend fun waitForText(text: String): String? {
        if (text.isBlank()) return "no text to wait for"
        repeat(20) {
            val root = rootInActiveWindow
            if (root != null && !root.findAccessibilityNodeInfosByText(text).isNullOrEmpty()) return null
            delay(500)
        }
        return "\"$text\" never appeared"
    }

    private fun clickByDescription(description: String): String? {
        val root = rootInActiveWindow ?: return "No active window"
        val match = findByDescription(root, description) ?: return "nothing described as \"$description\""
        val target = match.clickableSelfOrAncestor() ?: return "\"$description\" is not tappable"
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) null else "Tap failed"
    }

    private fun findByDescription(node: AccessibilityNodeInfo?, description: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.contains(description, ignoreCase = true) == true) return node
        for (index in 0 until node.childCount) {
            findByDescription(node.getChild(index), description)?.let { return it }
        }
        return null
    }

    private fun clickByViewId(viewId: String): String? {
        val root = rootInActiveWindow ?: return "No active window"
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) return "no view with id \"$viewId\""
        val target = nodes.firstNotNullOfOrNull { it.clickableSelfOrAncestor() }
            ?: return "that view is not tappable"
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) null else "Tap failed"
    }

    private fun longClickText(text: String): String? {
        val root = rootInActiveWindow ?: return "No active window"
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) return "\"$text\" not on screen"
        val target = nodes.firstNotNullOfOrNull { it.clickableSelfOrAncestor() }
            ?: return "\"$text\" cannot be held"
        return if (target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) null else "Long press failed"
    }

    private fun launchApp(nameOrPackage: String): String? {
        if (nameOrPackage.isBlank()) return "no app named"
        val pm = packageManager
        val cleaned = nameOrPackage.trim().replace(Regex("(?i)\\s+(app|application)$"), "").trim()
        val direct = pm.getLaunchIntentForPackage(cleaned)
        val intent = direct ?: run {
            val installed = pm.getInstalledApplications(0)
            fun label(info: android.content.pm.ApplicationInfo) = pm.getApplicationLabel(info).toString()
            val match = installed.firstOrNull { label(it).equals(cleaned, ignoreCase = true) }
                ?: installed.firstOrNull { label(it).contains(cleaned, ignoreCase = true) }
                ?: installed.firstOrNull { cleaned.contains(label(it), ignoreCase = true) }
                ?: installed.firstOrNull { it.packageName.contains(cleaned.lowercase()) }
            match?.let { pm.getLaunchIntentForPackage(it.packageName) }
        } ?: return "No app matching \"$nameOrPackage\""
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return null
    }

    private fun clickText(text: String): String? {
        val root = rootInActiveWindow ?: return "No active window"
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) return "\"$text\" not on screen"
        val target = nodes.firstNotNullOfOrNull { node -> node.clickableSelfOrAncestor() }
            ?: return "\"$text\" is not tappable"
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) null else "Tap failed"
    }

    private fun AccessibilityNodeInfo.clickableSelfOrAncestor(): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = this
        var hops = 0
        while (node != null && hops < 6) {
            if (node.isClickable) return node
            node = node.parent
            hops++
        }
        return null
    }

    private fun inputText(text: String): String? {
        val root = rootInActiveWindow ?: return "No active window"
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "No focused text field"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            null
        } else {
            "Typing failed"
        }
    }

    companion object {
        @Volatile
        var instance: LifeAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null

        /**
         * Source of truth from system settings — survives process restarts and
         * catches the case where the toggle is on but the service isn't bound.
         */
        fun isEnabledInSettings(context: android.content.Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val component = android.content.ComponentName(context, LifeAccessibilityService::class.java)
            return enabled.split(':').any {
                it.equals(component.flattenToString(), ignoreCase = true) ||
                    it.equals(component.flattenToShortString(), ignoreCase = true)
            }
        }

        private const val TAG = "LifeAccessibility"
        private val SOURCE = SourceRef(LifeModule.AGENTIC, "macro")

        init {
            LifeLogger.d(TAG, "Macro engine class loaded")
        }
    }
}
