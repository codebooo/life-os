package com.lifeos.feature.notes.data

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import javax.inject.Inject

/** Executes [LifeAction.CreateNote] on behalf of other modules (capture spine, R10/R12). */
internal class NotesActionHandler @Inject constructor(
    private val notesRepository: NotesRepository,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.CreateNote

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val create = action as LifeAction.CreateNote
        return when (val saved = notesRepository.save(
            noteId = null,
            title = create.title,
            body = create.body,
            sensitive = false,
        )) {
            is LifeResult.Success -> LifeResult.Success(saved.value)
            is LifeResult.Failure -> saved
        }
    }
}
