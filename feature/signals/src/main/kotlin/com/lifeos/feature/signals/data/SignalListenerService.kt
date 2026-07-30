package com.lifeos.feature.signals.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.signals.SignalDao
import com.lifeos.core.database.signals.SignalEntity
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Captures notifications so LifeOS can answer "what did I miss?" (§Module
 * Signals).
 *
 * Deliberately conservative: ongoing and group-summary notifications are
 * skipped, LifeOS's own are ignored, an identical title/text from the same app
 * inside a minute counts once, and rows are trimmed to [KEEP_DAYS]. Everything
 * stays in the local database.
 */
@AndroidEntryPoint
class SignalListenerService : NotificationListenerService() {

    @Inject
    lateinit var signalDao: SignalDao

    @Inject
    lateinit var eventBus: LifeEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recent = mutableMapOf<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        LifeLogger.i(TAG, "Signal listener connected")
    }

    override fun onListenerDisconnected() {
        connected = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == packageName) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (
            extras?.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            )?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val key = "${sbn.packageName}|$title|$text"
        val now = System.currentTimeMillis()
        val previous = recent[key]
        if (previous != null && now - previous < DEDUPE_MS) return
        recent[key] = now
        if (recent.size > 200) recent.entries.removeIf { now - it.value > DEDUPE_MS }

        val label = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        scope.launch {
            val id = signalDao.insert(
                SignalEntity(
                    appPackage = sbn.packageName,
                    appLabel = label,
                    title = title.take(160),
                    text = text.take(600),
                    postedAt = now,
                    extracted = classify(title, text),
                ),
            )
            signalDao.trim(now - KEEP_DAYS * 86_400_000L)
            // Rules listen on the bus, so a captured notification can start a macro.
            eventBus.publish(
                LifeEvent.SignalCaptured(
                    signalId = id,
                    appPackage = sbn.packageName,
                    appLabel = label,
                    title = title,
                    text = text,
                ),
            )
        }
    }

    /** Cheap, local tagging so the digest can group things worth acting on. */
    private fun classify(title: String, text: String): String {
        val joined = "$title $text"
        return when {
            CODE.containsMatchIn(joined) -> "CODE"
            PARCEL.containsMatchIn(joined) -> "PARCEL"
            RECEIPT.containsMatchIn(joined) -> "RECEIPT"
            else -> "NONE"
        }
    }

    companion object {
        @Volatile
        var connected: Boolean = false
            private set

        private const val TAG = "SignalListener"
        private const val DEDUPE_MS = 60_000L
        private const val KEEP_DAYS = 30

        private val CODE = Regex("(?i)\\b(code|otp|verification|2fa|einmal)\\b.*?\\b(\\d{4,8})\\b")
        private val PARCEL = Regex("(?i)\\b(parcel|paket|shipment|sendung|delivery|zustellung|dhl|hermes|ups|gls)\\b")
        private val RECEIPT = Regex("(?i)\\b(receipt|invoice|rechnung|beleg|payment|zahlung|abgebucht)\\b")

        /** True when the user has granted notification access to LifeOS. */
        fun isGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            val component = ComponentName(context, SignalListenerService::class.java)
            return enabled.split(':').any {
                it.equals(component.flattenToString(), ignoreCase = true) ||
                    it.equals(component.flattenToShortString(), ignoreCase = true)
            }
        }
    }
}
