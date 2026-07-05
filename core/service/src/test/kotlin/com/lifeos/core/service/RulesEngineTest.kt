package com.lifeos.core.service

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RulesEngineTest {

    private val source = SourceRef(LifeModule.SYSTEM, "test")
    private val executed = mutableListOf<LifeAction>()

    private val recordingHandler = object : LifeActionHandler {
        override fun canHandle(action: LifeAction) = true
        override suspend fun execute(action: LifeAction): LifeResult<Long?> {
            executed += action
            return LifeResult.Success(1L)
        }
    }

    private fun engine(vararg rules: CrossModuleRule) = RulesEngine(
        eventBus = LifeEventBus(),
        dispatcher = LifeActionDispatcher(setOf(recordingHandler)),
        rules = rules.toSet(),
    )

    @Test
    fun `matching rule's actions are dispatched with provenance`() = runTest {
        val rule = object : CrossModuleRule {
            override val id = "test"
            override fun matches(event: LifeEvent) = event is LifeEvent.ServiceStarted
            override suspend fun produce(event: LifeEvent) =
                listOf(LifeAction.CreateTask("from rule", source))
        }

        engine(rule).process(LifeEvent.ServiceStarted(1L))

        assertEquals(1, executed.size)
        assertEquals(LifeModule.SYSTEM, executed.first().source.module)
    }

    @Test
    fun `loop guard caps actions per event`() = runTest {
        val floodRule = object : CrossModuleRule {
            override val id = "flood"
            override fun matches(event: LifeEvent) = true
            override suspend fun produce(event: LifeEvent) =
                List(100) { LifeAction.CreateTask("task $it", source) }
        }

        engine(floodRule).process(LifeEvent.ServiceStarted(1L))

        assertEquals(8, executed.size)
    }

    @Test
    fun `a throwing rule doesn't block other rules`() = runTest {
        val badRule = object : CrossModuleRule {
            override val id = "bad"
            override fun matches(event: LifeEvent) = true
            override suspend fun produce(event: LifeEvent): List<LifeAction> = error("boom")
        }
        val goodRule = object : CrossModuleRule {
            override val id = "good"
            override fun matches(event: LifeEvent) = true
            override suspend fun produce(event: LifeEvent) =
                listOf(LifeAction.CreateTask("still works", source))
        }

        engine(badRule, goodRule).process(LifeEvent.ServiceStarted(1L))

        assertEquals(1, executed.size)
    }
}
