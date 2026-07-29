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
    /** One of [MacroCatalog.actions]; anything else is rejected at compile time. */
    val action: String,
    /** LAUNCH: app name or package. CLICK: visible text to tap. */
    val target: String? = null,
    /** INPUT and friends: the literal text. */
    val text: String? = null,
    /** WAIT and the LifeOS timers: milliseconds (capped at 10s for WAIT). */
    val delayMs: Long? = null,
)

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

    /**
     * @param installedApps labels of apps actually on the phone. Given to the
     *   model so it stops inventing targets like "Spotify App", and used again
     *   after parsing to normalise whatever it produced.
     */
    suspend fun compile(nlPrompt: String, installedApps: List<String> = emptyList()): LifeResult<List<MacroStep>> {
        val appHint = if (installedApps.isEmpty()) {
            ""
        } else {
            " Installed apps you may use verbatim as LAUNCH targets: " +
                installedApps.take(60).joinToString(", ") + "."
        }
        val request = AiRequest(
            system = "Compile a phone automation into steps. Reply with ONLY a minified JSON array, no prose. " +
                """Step shape: {"action":"…","target":string?,"text":string?,"delayMs":number?}. """ +
                "Allowed actions: " + MacroCatalog.promptVocabulary + ". " +
                "Rules: use the FEWEST steps that do the job - \"open Spotify\" is exactly " +
                """[{"action":"LAUNCH","target":"Spotify"},{"action":"WAIT","delayMs":1500}] and nothing more. """ +
                "LAUNCH target is the app's exact name, never with the word App appended. " +
                "Only add CLICK/INPUT steps the user actually asked for; never guess button names. " +
                "Maximum 12 steps." + appHint,
            messages = listOf(AiMessage(AiRole.USER, nlPrompt)),
            localOnly = true,
        )
        val raw = when (val result = aiRouter.complete(request)) {
            is LifeResult.Success -> result.value.text
            is LifeResult.Failure -> return result
        }
        return parse(raw, installedApps)
    }

    fun parse(raw: String, installedApps: List<String> = emptyList()): LifeResult<List<MacroStep>> {
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
            if (step.action.uppercase() !in MacroCatalog.byAction) {
                return LifeResult.Failure(
                    LifeError.Validation("Unsupported step '${step.action}' — this macro cannot run"),
                )
            }
        }
        val cleaned = steps
            .map { step ->
                var next = step.copy(action = step.action.uppercase())
                if (next.action == "WAIT" && (next.delayMs ?: 0) > 10_000) next = next.copy(delayMs = 10_000)
                if (next.action == "LAUNCH") next = next.copy(target = normalizeAppTarget(next.target, installedApps))
                next
            }
            // Small models love to repeat a step; collapse exact duplicates that
            // sit next to each other.
            .filterIndexed { index, step -> index == 0 || step != steps.getOrNull(index - 1) }
        val badLaunch = cleaned.firstOrNull { it.action == "LAUNCH" && it.target.isNullOrBlank() }
        if (badLaunch != null) {
            return LifeResult.Failure(LifeError.Validation("A LAUNCH step has no app - name the app and retry"))
        }
        return LifeResult.Success(cleaned)
    }

    /**
     * Maps whatever the model wrote onto a real app label: strips the "app"
     * suffix models like to add, then matches case-insensitively against what is
     * installed. Leaves the value alone when nothing matches, so a package name
     * still works.
     */
    private fun normalizeAppTarget(target: String?, installedApps: List<String>): String? {
        val raw = target?.trim()?.takeIf { it.isNotEmpty() } ?: return target
        val stripped = raw
            .replace(Regex("(?i)\\s+(app|application)$"), "")
            .trim()
            .trim('"', '\'')
        if (installedApps.isEmpty()) return stripped
        return installedApps.firstOrNull { it.equals(stripped, ignoreCase = true) }
            ?: installedApps.firstOrNull { it.contains(stripped, ignoreCase = true) }
            ?: installedApps.firstOrNull { stripped.contains(it, ignoreCase = true) }
            ?: stripped
    }
}
