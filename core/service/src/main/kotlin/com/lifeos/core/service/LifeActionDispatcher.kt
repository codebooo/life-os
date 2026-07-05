package com.lifeos.core.service

import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A feature-registered executor for one or more [LifeAction] types.
 * Features contribute handlers via Hilt `@IntoSet` multibindings, which keeps
 * cross-feature effects flowing through `:core:service` instead of
 * feature-to-feature edges (§1.2). The Phase 4 RulesEngine dispatches through
 * this same registry.
 */
interface LifeActionHandler {
    fun canHandle(action: LifeAction): Boolean

    /** Executes the action; returns the created entity id when there is one. */
    suspend fun execute(action: LifeAction): LifeResult<Long?>
}

@Singleton
class LifeActionDispatcher @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards LifeActionHandler>,
) {

    suspend fun dispatch(action: LifeAction): LifeResult<Long?> {
        val handler = handlers.firstOrNull { it.canHandle(action) }
            ?: return LifeResult.Failure(
                LifeError.Validation("No handler registered for ${action::class.simpleName}"),
            )
        LifeLogger.d(TAG, "Dispatching ${action::class.simpleName} from ${action.source.module}")
        return handler.execute(action)
    }

    private companion object {
        const val TAG = "ActionDispatcher"
    }
}
