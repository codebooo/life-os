package com.lifeos.feature.messagecenter.rules

import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingNumberRuleTest {

    @Test
    fun `extracts a 20-digit DHL number from notification text`() = runTest {
        val rule = TrackingNumberRule()
        val event = LifeEvent.NotificationPosted(
            messageId = 1,
            appPackage = "com.dhl",
            title = "Your parcel is on its way",
            text = "Sendung 00340434161094042557 kommt morgen",
        )

        assertTrue(rule.matches(event))
        val actions = rule.produce(event)

        assertEquals(1, actions.size)
        val track = actions.first() as LifeAction.TrackPackage
        assertEquals("00340434161094042557", track.trackingNumber)
    }

    @Test
    fun `is idempotent per tracking number`() = runTest {
        val rule = TrackingNumberRule()
        val event = LifeEvent.NotificationPosted(
            messageId = 1,
            appPackage = "com.dhl",
            title = null,
            text = "tracking: 1234567890123",
        )

        val first = rule.produce(event)
        val second = rule.produce(event)

        assertEquals(1, first.size)
        assertEquals(0, second.size)
    }

    @Test
    fun `plain chatter produces nothing`() = runTest {
        val actions = TrackingNumberRule().produce(
            LifeEvent.NotificationPosted(1, "com.chat", "Hey", "see you at 12:30 tomorrow?"),
        )
        assertEquals(0, actions.size)
    }
}
