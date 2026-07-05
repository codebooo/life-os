package com.lifeos.core.ai.macro

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One validated step of the macro intermediate representation (§Module 12,
 * [src 41]). Only these actions exist; anything else the model emits is
 * rejected at compile time, never executed.
 */
@Serializable
data class MacroStep(
    /** LAUNCH | CLICK | INPUT | BACK | HOME | WAIT */
    val action: String,
    /** LAUNCH: app name or package. CLICK: visible text to tap. */
    val target: String? = null,
    /** INPUT: text typed into the focused field. */
    val text: String? = null,
    /** WAIT: delay in milliseconds (capped at 10s). */
    val delayMs: Long? = null,
)

val SUPPORTED_MACRO_ACTIONS = setOf("LAUNCH", "CLICK", "INPUT", "BACK", "HOME", "WAIT")

/**
 * NL → validated macro IR (§5, [src 41]). The model output is parsed and
 * validated; unsupported steps fail the whole compile so the preview the
 * user confirms is exactly what will run.
 */
@Singleton
class MacroCompiler @Inject constructor(
    private val aiRouter: AiRouter,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun compile(nlPrompt: String): LifeResult<List<MacroStep>> {
        val request = AiRequest(
            system = "Compile a phone automation described in natural language into steps. " +
                "Reply with ONLY a minified JSON array of steps, no prose. Each step: " +
                """{"action":"LAUNCH"|"CLICK"|"INPUT"|"BACK"|"HOME"|"WAIT","target":string?,"text":string?,"delayMs":number?}. """ +
                "LAUNCH opens an app by name (target). CLICK taps visible text (target). " +
                "INPUT types text into the focused field (text). WAIT pauses (delayMs). " +
                "Use WAIT 1500 after every LAUNCH. Maximum 12 steps.",
            messages = listOf(AiMessage(AiRole.USER, nlPrompt)),
            localOnly = true,
        )
        val raw = when (val result = aiRouter.complete(request)) {
            is LifeResult.Success -> result.value.text
            is LifeResult.Failure -> return result
        }
        return parse(raw)
    }

    internal fun parse(raw: String): LifeResult<List<MacroStep>> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end <= start) {
            return LifeResult.Failure(LifeError.Validation("The model did not return a step list — try rephrasing"))
        }
        val steps = try {
            json.decodeFromString(ListSerializer(MacroStep.serializer()), raw.substring(start, end + 1))
        } catch (t: Throwable) {
            return LifeResult.Failure(LifeError.Validation("Could not parse the compiled steps: ${t.message}"))
        }
        if (steps.isEmpty()) return LifeResult.Failure(LifeError.Validation("The macro compiled to zero steps"))
        if (steps.size > 12) return LifeResult.Failure(LifeError.Validation("Macros are capped at 12 steps"))
        steps.forEach { step ->
            if (step.action !in SUPPORTED_MACRO_ACTIONS) {
                return LifeResult.Failure(
                    LifeError.Validation("Unsupported step '${step.action}' — this macro cannot run"),
                )
            }
        }
        return LifeResult.Success(
            steps.map { if ((it.delayMs ?: 0) > 10_000) it.copy(delayMs = 10_000) else it },
        )
    }
}
