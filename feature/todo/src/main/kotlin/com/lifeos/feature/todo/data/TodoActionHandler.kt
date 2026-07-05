package com.lifeos.feature.todo.data

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.database.todo.TodoDao
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import javax.inject.Inject

/** Executes [LifeAction.CreateTask] for rules and other modules; tasks land in the Inbox. */
internal class TodoActionHandler @Inject constructor(
    private val todoDao: TodoDao,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.CreateTask

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val create = action as LifeAction.CreateTask
        return runCatchingLife {
            todoDao.insertTask(
                TaskEntity(
                    title = create.title,
                    sourceModule = create.source.module.name,
                    sourceEntityId = create.source.entityId.toLongOrNull(),
                    createdAt = System.currentTimeMillis(),
                ),
            ) as Long?
        }
    }
}
