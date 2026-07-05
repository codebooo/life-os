package com.lifeos.core.ai

import com.lifeos.core.ai.engine.AiEngine
import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.getOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine(
    override val id: AiEngineId,
    private val available: Boolean = true,
    private val reply: String = "ok",
    private val fails: Boolean = false,
) : AiEngine {
    override suspend fun isAvailable(): Boolean = available

    override fun stream(request: AiRequest): Flow<AiChunk> = flow {
        if (fails) throw RuntimeException("engine down")
        emit(AiChunk(text = reply, done = true))
    }
}

class AiRouterTest {

    private val request = AiRequest(messages = listOf(AiMessage(AiRole.USER, "hi")))

    @Test
    fun `prefers NAS when reachable`() = runTest {
        val router = AiRouter(
            onDevice = FakeEngine(AiEngineId.ON_DEVICE_GEMMA, reply = "device"),
            nas = FakeEngine(AiEngineId.NAS_OLLAMA, reply = "nas"),
        )

        val result = router.complete(request).getOrNull()

        assertEquals("nas", result?.text)
        assertEquals(AiEngineId.NAS_OLLAMA, result?.engine)
    }

    @Test
    fun `falls back to on-device when NAS is unreachable`() = runTest {
        val router = AiRouter(
            onDevice = FakeEngine(AiEngineId.ON_DEVICE_GEMMA, reply = "device"),
            nas = FakeEngine(AiEngineId.NAS_OLLAMA, available = false),
        )

        val result = router.complete(request).getOrNull()

        assertEquals("device", result?.text)
        assertEquals(AiEngineId.ON_DEVICE_GEMMA, result?.engine)
    }

    @Test
    fun `local-only requests never leave the device`() = runTest {
        val router = AiRouter(
            onDevice = FakeEngine(AiEngineId.ON_DEVICE_GEMMA, available = false),
            nas = FakeEngine(AiEngineId.NAS_OLLAMA, reply = "nas"),
        )

        val routed = router.route(request.copy(localOnly = true))
        val result = router.complete(request.copy(localOnly = true))

        assertNull(routed)
        assertTrue(result is LifeResult.Failure)
    }

    @Test
    fun `mid-stream failure restarts on the other engine`() = runTest {
        val router = AiRouter(
            onDevice = FakeEngine(AiEngineId.ON_DEVICE_GEMMA, reply = "device"),
            nas = FakeEngine(AiEngineId.NAS_OLLAMA, fails = true),
        )

        val result = router.complete(request).getOrNull()

        assertEquals("device", result?.text)
        assertEquals(AiEngineId.ON_DEVICE_GEMMA, result?.engine)
    }

    @Test
    fun `fails cleanly when both engines fail`() = runTest {
        val router = AiRouter(
            onDevice = FakeEngine(AiEngineId.ON_DEVICE_GEMMA, fails = true),
            nas = FakeEngine(AiEngineId.NAS_OLLAMA, fails = true),
        )

        assertTrue(router.complete(request) is LifeResult.Failure)
    }
}
