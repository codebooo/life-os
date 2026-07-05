package com.lifeos.core.service

import com.lifeos.core.common.log.LifeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A cross-module intelligence rule (§3): pure match + produce. Rules are
 * contributed via Hilt `@IntoSet` multibindings and must be idempotent —
 * the engine additionally caps the actions per event as a loop guard.
 */
interface CrossModuleRule {
    val id: String
    fun matches(event: LifeEvent): Boolean
    suspend fun produce(event: LifeEvent): List<LifeAction>
}

/**
 * Subscribes to the [LifeEventBus] and turns matching events into dispatched
 * [LifeAction]s (§1.5). Runs inside the foreground service's scope. Actions
 * default to concrete writes by their handlers; per-rule auto-apply vs
 * suggestion policy arrives with the rule-settings surface.
 */
@Singleton
class RulesEngine @Inject constructor(
    private val eventBus: LifeEventBus,
    private val dispatcher: LifeActionDispatcher,
    private val rules: Set<@JvmSuppressWildcards CrossModuleRule>,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            eventBus.events.collect { event -> process(event) }
        }
        LifeLogger.i(TAG, "Rules engine started with ${rules.size} rules")
    }

    suspend fun process(event: LifeEvent) {
        rules.filter { rule -> rule.matches(event) }.forEach { rule ->
            val actions = try {
                rule.produce(event).take(MAX_ACTIONS_PER_EVENT)
            } catch (t: Throwable) {
                if (t is kotlin.coroutines.cancellation.CancellationException) throw t
                LifeLogger.e(TAG, "Rule ${rule.id} failed on ${event::class.simpleName}", t)
                emptyList()
            }
            actions.forEach { action ->
                LifeLogger.d(TAG, "Rule ${rule.id}: ${event::class.simpleName} -> ${action::class.simpleName}")
                dispatcher.dispatch(action)
            }
        }
    }

    private companion object {
        const val TAG = "RulesEngine"

        /** Loop guard: one event can never fan out into an unbounded cascade. */
        const val MAX_ACTIONS_PER_EVENT = 8
    }
}
