package com.lifeos.feature.finance.data

import com.lifeos.core.database.finance.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubscriptionDetectorTest {

    private fun tx(daysAgo: Long, cents: Long) = TransactionEntity(
        merchant = "Streamflix",
        amountCents = cents,
        categoryId = null,
        at = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysAgo),
        source = "IMPORT",
    )

    @Test
    fun `three monthly charges detected as MONTHLY`() {
        val detection = SubscriptionDetector.detect(
            listOf(tx(60, -1299), tx(30, -1299), tx(0, -1299)),
        )
        assertEquals("MONTHLY", detection?.cadence)
        assertEquals(-1299L, detection?.amountCents)
    }

    @Test
    fun `two charges not enough`() {
        assertNull(SubscriptionDetector.detect(listOf(tx(30, -1299), tx(0, -1299))))
    }

    @Test
    fun `irregular gaps rejected`() {
        assertNull(
            SubscriptionDetector.detect(listOf(tx(200, -1299), tx(3, -1299), tx(0, -1299))),
        )
    }

    @Test
    fun `income ignored`() {
        assertNull(
            SubscriptionDetector.detect(listOf(tx(60, 5000), tx(30, 5000), tx(0, 5000))),
        )
    }
}
