package com.lifeos.feature.imagereasoning.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptExtractorTest {

    @Test
    fun `extracts merchant, total, date and warranty from a German receipt`() {
        val ocr = """
            MediaMarkt
            Beleg 4711
            Kopfhörer XY-100    89,99
            USB-Kabel            9,49
            SUMME EUR           99,48
            07.03.2026 14:31
            2 Jahre Garantie auf alle Artikel
        """.trimIndent()

        val extraction = ReceiptExtractor.extract(ocr)

        assertEquals("MediaMarkt", extraction.merchant)
        assertEquals(9948L, extraction.totalCents)
        assertEquals("07.03.2026", extraction.dateText)
        assertEquals(24, extraction.warrantyMonths)
    }

    @Test
    fun `falls back to the largest amount without a total line`() {
        val ocr = """
            Corner Café
            Cappuccino 3,80
            Croissant 2,20
            6,00
        """.trimIndent()

        assertEquals(600L, ReceiptExtractor.extract(ocr).totalCents)
    }

    @Test
    fun `grouped thousands parse correctly`() {
        assertEquals(129999L, ReceiptExtractor.parseAmount("1.299,99"))
        assertEquals(129999L, ReceiptExtractor.parseAmount("1,299.99"))
        assertNull(ReceiptExtractor.parseAmount("12,3"))
    }

    @Test
    fun `warranty in months wins when stated in months`() {
        val extraction = ReceiptExtractor.extract("Garantie: 6 Monate Garantie ab Kaufdatum")
        assertEquals(6, extraction.warrantyMonths)
    }
}
