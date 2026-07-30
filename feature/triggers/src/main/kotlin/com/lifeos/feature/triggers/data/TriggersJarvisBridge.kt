package com.lifeos.feature.triggers.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.triggers.TriggerDao
import com.lifeos.core.database.triggers.TriggerRuleEntity
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

/** Rules as Jarvis reads them, including why something fired. */
internal class TriggersProvider @Inject constructor(
    private val triggerDao: TriggerDao,
) : LifeDataProvider {

    override val topic: String = "triggers"
    override val description: String = "automation rules, plus the log of what fired"

    override suspend fun read(query: String?): String {
        val rules = triggerDao.allRules()
        val fires = triggerDao.recentFires(10)
        return buildString {
            if (rules.isEmpty()) {
                appendLine("No automation rules yet.")
            } else {
                appendLine("Rules (${rules.size}):")
                rules.forEach { appendLine("- ${it.describe()}") }
            }
            if (fires.isNotEmpty()) {
                appendLine("Recent fires:")
                fires.forEach {
                    appendLine("- ${AT.format(Date(it.at))} ${it.ruleName}: ${it.outcome} ${it.detail}".trimEnd())
                }
            }
            appendLine("Vocabulary - ${TriggerCatalog.promptVocabulary}")
        }.trim()
    }

    private fun TriggerRuleEntity.describe(): String = buildString {
        append(if (enabled) "" else "(off) ")
        append(name)
        append(": when ").append(triggerType)
        if (triggerArg.isNotBlank()) append(" ").append(triggerArg)
        append(" -> ").append(actionType)
        if (actionArg.isNotBlank()) append(" ").append(actionArg)
        if (days.isNotBlank()) append(" [days $days]")
        if (window.isNotBlank()) append(" [window $window]")
        if (fireCount > 0) append(" (fired $fireCount times)")
    }

    private companion object {
        val AT = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
    }
}

/** Creating, toggling and running rules on Jarvis's word. */
internal class TriggersActionHandler @Inject constructor(
    private val triggerDao: TriggerDao,
    /**
     * Lazily, on purpose: the engine dispatches actions and this handler is one
     * of them, so injecting it directly is a dependency cycle.
     */
    private val engineProvider: Provider<TriggerEngine>,
) : LifeActionHandler {

    private val engine: TriggerEngine get() = engineProvider.get()

    override fun canHandle(action: LifeAction): Boolean =
        action is LifeAction.CreateTriggerRule ||
            action is LifeAction.SetTriggerRuleEnabled ||
            action is LifeAction.RunTriggerRule

    override suspend fun execute(action: LifeAction): LifeResult<Long?> = when (action) {
        is LifeAction.CreateTriggerRule -> {
            val triggerType = action.triggerType.uppercase()
            val actionType = action.actionType.uppercase()
            when {
                triggerType !in TriggerCatalog.triggerByType ->
                    LifeResult.Failure(LifeError.Validation("Unknown trigger \"$triggerType\""))

                actionType !in TriggerCatalog.actionByType ->
                    LifeResult.Failure(LifeError.Validation("Unknown action \"$actionType\""))

                else -> {
                    val id = triggerDao.insertRule(
                        TriggerRuleEntity(
                            name = action.name.trim().ifBlank { "Rule" }.take(60),
                            triggerType = triggerType,
                            triggerArg = action.triggerArg.trim(),
                            days = action.days.trim(),
                            actionType = actionType,
                            actionArg = action.actionArg.trim(),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    engine.rearmAll()
                    LifeResult.Success(id)
                }
            }
        }

        is LifeAction.SetTriggerRuleEnabled -> {
            val rule = findRule(action.ruleName)
            if (rule == null) {
                LifeResult.Failure(LifeError.Validation("No rule called \"${action.ruleName}\""))
            } else {
                triggerDao.updateRule(rule.copy(enabled = action.enabled))
                engine.rearmAll()
                LifeResult.Success(rule.id)
            }
        }

        is LifeAction.RunTriggerRule -> {
            val rule = findRule(action.ruleName)
            if (rule == null) {
                LifeResult.Failure(LifeError.Validation("No rule called \"${action.ruleName}\""))
            } else {
                engine.run(rule, byHand = true)
                LifeResult.Success(rule.id)
            }
        }

        else -> LifeResult.Failure(LifeError.Validation("Unsupported action"))
    }

    private suspend fun findRule(name: String): TriggerRuleEntity? {
        val needle = name.trim().lowercase()
        val rules = triggerDao.allRules()
        return rules.firstOrNull { it.name.lowercase() == needle }
            ?: rules.firstOrNull { needle in it.name.lowercase() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TriggersJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: TriggersProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: TriggersActionHandler): LifeActionHandler
}
