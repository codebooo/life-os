package com.lifeos.core.ai.engine.ollama

import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaProtocolTest {

    @Test
    fun `system prompt is prepended as a system message`() {
        val request = OllamaProtocol.buildRequest(
            model = "gemma4:12b",
            request = AiRequest(
                messages = listOf(AiMessage(AiRole.USER, "hello")),
                system = "You are LifeOS.",
            ),
        )

        assertEquals(listOf("system", "user"), request.messages.map { it.role })
        assertEquals("You are LifeOS.", request.messages.first().content)
    }

    @Test
    fun `parses streamed content chunk`() {
        val chunk = OllamaProtocol.parseChunk(
            """{"model":"gemma4:12b","message":{"role":"assistant","content":"Hel"},"done":false}""",
        )

        assertEquals("Hel", chunk?.text)
        assertFalse(chunk!!.done)
    }

    @Test
    fun `parses terminal chunk with stats fields ignored`() {
        val chunk = OllamaProtocol.parseChunk(
            """{"model":"gemma4:12b","message":{"role":"assistant","content":""},"done":true,"total_duration":123}""",
        )

        assertTrue(chunk!!.done)
    }

    @Test
    fun `blank keep-alive lines are skipped`() {
        assertNull(OllamaProtocol.parseChunk("  "))
    }

    @Test
    fun `in-band errors throw`() {
        assertThrows(OllamaException::class.java) {
            OllamaProtocol.parseChunk("""{"error":"model not found"}""")
        }
    }
}
