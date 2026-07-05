package com.lifeos.feature.smarthome.data

import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeEvent
import javax.inject.Inject

/**
 * R11 (§3): a fired reminder or calendar-event title carrying an
 * `@scene.<id>` tag runs that Home Assistant scene. Zone/WebSocket triggers
 * layer on later; explicit tags are the honest, predictable v1.
 */
class HomeSceneRule @Inject constructor() : CrossModuleRule {

    override val id = "tagged-trigger-to-home-scene"

    override fun matches(event: LifeEvent): Boolean = sceneOf(event) != null

    override suspend fun produce(event: LifeEvent): List<LifeAction> {
        val (sceneId, source) = sceneOf(event) ?: return emptyList()
        return listOf(LifeAction.RunHomeScene(sceneId, source))
    }

    private fun sceneOf(event: LifeEvent): Pair<String, SourceRef>? {
        val (text, source) = when (event) {
            is LifeEvent.ReminderFired ->
                event.title to SourceRef(LifeModule.REMINDERS, event.reminderId.toString())
            is LifeEvent.CalendarEventChanged ->
                event.title to SourceRef(LifeModule.CALENDAR, event.eventId.toString())
            else -> return null
        }
        val match = SCENE_TAG.find(text) ?: return null
        return "scene.${match.groupValues[1]}" to source
    }

    private companion object {
        val SCENE_TAG = Regex("@scene\\.([a-z0-9_]+)")
    }
}

internal class HaActionHandler @Inject constructor(
    private val haClient: HaClient,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction) = action is LifeAction.RunHomeScene

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val run = action as LifeAction.RunHomeScene
        return try {
            haClient.callService("scene", "turn_on", run.sceneId)
            LifeLogger.i("HaHandler", "Ran ${run.sceneId}")
            LifeResult.Success(null)
        } catch (e: Exception) {
            LifeResult.Failure(LifeError.Network(e.message ?: "Scene call failed"))
        }
    }
}
