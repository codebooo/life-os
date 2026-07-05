package com.lifeos.feature.messagecenter.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.messages.MessageDao
import com.lifeos.core.database.messages.UnifiedMessageEntity
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The unified-inbox feeder (§Module 7): every user-relevant notification
 * becomes a [UnifiedMessageEntity] + [LifeEvent.NotificationPosted] — the
 * primary signal source for cross-module rules (R1/R2 class).
 */
@AndroidEntryPoint
class LifeNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var messageDao: MessageDao

    @Inject
    lateinit var eventBus: LifeEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isRelevant(sbn)) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        scope.launch {
            val id = messageDao.insert(
                UnifiedMessageEntity(
                    appPackage = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = text,
                    notificationKey = sbn.key,
                    postedAt = sbn.postTime,
                ),
            )
            if (id > 0) {
                eventBus.tryPublish(
                    LifeEvent.NotificationPosted(id, sbn.packageName, title, text),
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun isRelevant(sbn: StatusBarNotification): Boolean = when {
        sbn.packageName == packageName -> false // never ingest our own
        sbn.isOngoing -> false // media/nav/foreground-service chrome
        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> false
        else -> true
    }

    private companion object {
        const val TAG = "NotificationListener"
    }
}
