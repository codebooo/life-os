package com.lifeos.core.service

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeEventBusTest {

    @Test
    fun `published events reach subscribers`() = runTest {
        val bus = LifeEventBus()

        bus.events.test {
            val event = LifeEvent.ServiceStarted(startedAt = 42L)
            bus.publish(event)
            assertEquals(event, awaitItem())
        }
    }

    @Test
    fun `tryPublish buffers events without subscribers`() {
        val bus = LifeEventBus()

        assertTrue(bus.tryPublish(LifeEvent.ServiceStarted(startedAt = 1L)))
    }
}
