package com.lifeos.feature.agentic.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lifeos.core.ai.macro.MacroStep
import com.lifeos.core.common.log.LifeLogger
import kotlinx.coroutines.delay

/**
 * The macro executor (§Module 12). Bound only while the user has enabled
 * "LifeOS Macros" in system accessibility settings; steps run exclusively
 * from an explicit Run tap in the app — never on events.
 */
class LifeAccessibilityService : AccessibilityService() {

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
            val failure = when (step.action) {
                "LAUNCH" -> launchApp(step.target.orEmpty())
                "CLICK" -> clickText(step.target.orEmpty())
                "INPUT" -> inputText(step.text.orEmpty())
                "BACK" -> if (performGlobalAction(GLOBAL_ACTION_BACK)) null else "BACK failed"
                "HOME" -> if (performGlobalAction(GLOBAL_ACTION_HOME)) null else "HOME failed"
                "WAIT" -> {
                    delay(step.delayMs ?: 1_000)
                    null
                }
                else -> "Unsupported action ${step.action}"
            }
            if (failure != null) return "Step ${index + 1} (${step.action}): $failure"
            delay(350)
        }
        return null
    }

    private fun launchApp(nameOrPackage: String): String? {
        val pm = packageManager
        val direct = pm.getLaunchIntentForPackage(nameOrPackage)
        val intent = direct ?: run {
            val installed = pm.getInstalledApplications(0)
            val match = installed.firstOrNull {
                pm.getApplicationLabel(it).toString().equals(nameOrPackage, ignoreCase = true)
            } ?: installed.firstOrNull {
                pm.getApplicationLabel(it).toString().contains(nameOrPackage, ignoreCase = true)
            }
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

        init {
            LifeLogger.d(TAG, "Macro engine class loaded")
        }
    }
}
