package com.lifeos.feature.dhl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DhlApiTest {

    @Test
    fun `parses a unified tracking response`() {
        val body = """
            {
              "shipments": [{
                "id": "00340434161094042557",
                "status": {"statusCode": "transit", "status": "IN TRANSIT", "description": "Parcel centre"},
                "estimatedTimeOfDelivery": "2026-07-07",
                "events": [
                  {"timestamp": "2026-07-05T08:15:00+02:00", "statusCode": "transit", "description": "Processed", "location": {"address": {"addressLocality": "Hamburg"}}},
                  {"timestamp": "2026-07-04T19:00:00+02:00", "statusCode": "pre-transit", "description": "Announced"}
                ]
              }]
            }
        """.trimIndent()

        val shipment = DhlApi.parse(body)

        assertEquals("TRANSIT", shipment.status)
        assertEquals("Parcel centre", shipment.statusDescription)
        assertNotNull(shipment.estimatedDeliveryAt)
        assertEquals(2, shipment.events.size)
        assertEquals("Hamburg", shipment.events.first().location)
    }

    @Test
    fun `unknown fields and empty shipments do not crash`() {
        val shipment = DhlApi.parse("""{"shipments": [], "surprise": 42}""")
        assertEquals("UNKNOWN", shipment.status)
    }
}
