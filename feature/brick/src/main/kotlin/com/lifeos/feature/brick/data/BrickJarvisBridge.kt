package com.lifeos.feature.brick.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.brick.BrickDao
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Brick as Jarvis reads it: which modes exist and what is blocking now. */
internal class BrickProvider @Inject constructor(
    private val brickDao: BrickDao,
    private val repository: BrickRepository,
) : LifeDataProvider {

    override val topic: String = "brick"
    override val description: String = "app-blocking modes, which one is running, unlocks left"

    override suspend fun read(query: String?): String {
        val profiles = brickDao.allProfiles()
        val active = repository.active.value
        return buildString {
            if (profiles.isEmpty()) {
                appendLine("Brick: no modes set up yet.")
            } else {
                appendLine("Brick modes:")
                profiles.forEach { profile ->
                    val apps = profile.blockedPackages.lines().count { it.isNotBlank() }
                    append("- ${profile.name}: $apps app(s)")
                    if (profile.inverse) {
                        append(
                            ", inverse ${BrickPolicy.formatMinute(profile.startMinuteOfDay)}" +
                                "-${BrickPolicy.formatMinute(profile.endMinuteOfDay)}" +
                                ", ${profile.unlockMinutes} min per tap",
                        )
                    } else {
                        append(", on: ${profile.activator}, off: ${profile.deactivator}")
                    }
                    if (profile.strict) append(", strict")
                    appendLine()
                }
            }
            if (active == null) {
                appendLine("Nothing is blocking right now.")
            } else {
                appendLine("Running: \"${active.profile.name}\", ${active.blockedPackages.size} app(s) blocked.")
                if (active.profile.inverse) {
                    val left = BrickPolicy.unlocksLeft(active.rules)
                    val remaining = repository.unlockRemaining()
                    appendLine(
                        if (remaining != null) {
                            "Access is open for another ${remaining / 60_000} min."
                        } else {
                            "Blocked now; ${left ?: "unlimited"} unlock(s) left in this window."
                        },
                    )
                }
            }
        }.trim()
    }
}

/** Starting and stopping Brick modes on Jarvis's word. */
internal class BrickActionHandler @Inject constructor(
    private val brickDao: BrickDao,
    private val repository: BrickRepository,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean =
        action is LifeAction.StartBrickMode || action is LifeAction.StopBrickMode

    override suspend fun execute(action: LifeAction): LifeResult<Long?> = when (action) {
        is LifeAction.StartBrickMode -> {
            repository.refresh()
            val needle = action.modeName.trim().lowercase()
            val profiles = brickDao.allProfiles()
            val profile = profiles.firstOrNull { it.name.lowercase() == needle }
                ?: profiles.firstOrNull { needle in it.name.lowercase() }
            when {
                profile == null ->
                    LifeResult.Failure(LifeError.Validation("No Brick mode called \"${action.modeName}\""))

                repository.start(profile.id, "MANUAL") -> LifeResult.Success(profile.id)
                else -> LifeResult.Failure(LifeError.Validation("Another mode is already running"))
            }
        }

        is LifeAction.StopBrickMode -> {
            repository.refresh()
            if (repository.stop("MANUAL")) {
                LifeResult.Success(null)
            } else {
                // Strict and inverse modes refuse this on purpose.
                LifeResult.Failure(LifeError.Validation("That mode will not end early"))
            }
        }

        else -> LifeResult.Failure(LifeError.Validation("Unsupported action"))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BrickJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: BrickProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: BrickActionHandler): LifeActionHandler
}
