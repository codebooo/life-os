package com.lifeos.feature.adhd.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.adhd.FocusDao
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Focus as Jarvis reads it: the live timer plus the streak. */
internal class FocusProvider @Inject constructor(
    private val focusDao: FocusDao,
    private val controller: FocusTimerController,
) : LifeDataProvider {

    override val topic: String = "focus"
    override val description: String = "focus timer state and session streak"

    override suspend fun read(query: String?): String {
        val timer = controller.state.value
        val sessions = focusDao.observeRecent().first()
        val done = sessions.count { it.completed }
        return buildString {
            appendLine(
                if (timer.running) {
                    "Focus timer: running, ${timer.remainingSeconds / 60}m ${timer.remainingSeconds % 60}s left " +
                        "of ${timer.totalSeconds / 60}m."
                } else {
                    "Focus timer: idle, set to ${timer.totalSeconds / 60}m."
                },
            )
            appendLine("Overlay is ${if (timer.overlayVisible) "showing" else "hidden"}.")
            appendLine("Sessions: $done completed of ${sessions.size} recorded.")
            sessions.take(5).forEach {
                appendLine("- ${it.minutes}m ${if (it.completed) "done" else "abandoned"}")
            }
        }.trim()
    }
}

/** Starting and stopping the focus timer on Jarvis's word. */
internal class FocusActionHandler @Inject constructor(
    private val controller: FocusTimerController,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean =
        action is LifeAction.StartFocusTimer || action is LifeAction.StopFocusTimer

    override suspend fun execute(action: LifeAction): LifeResult<Long?> = when (action) {
        is LifeAction.StartFocusTimer -> {
            val minutes = action.minutes.coerceIn(1, 24 * 60)
            controller.setTotal(minutes * 60)
            controller.start()
            LifeResult.Success(null)
        }

        is LifeAction.StopFocusTimer -> {
            controller.reset()
            LifeResult.Success(null)
        }

        else -> LifeResult.Failure(LifeError.Validation("Unsupported action"))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FocusJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: FocusProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: FocusActionHandler): LifeActionHandler
}
