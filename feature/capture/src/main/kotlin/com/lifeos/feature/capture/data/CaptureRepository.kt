package com.lifeos.feature.capture.data

import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.capture.CaptureEntity
import com.lifeos.core.database.capture.LogEntryEntity
import com.lifeos.core.database.capture.LogFormEntity
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.model.CaptureKind
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** A capture awaiting user confirmation, with the classifier's proposal. */
data class PendingCapture(
    val captureId: Long,
    val text: String,
    val suggestion: CaptureSuggestion,
)

/**
 * The capture spine (§Module 20): persist raw input instantly (nothing is ever
 * lost), classify, and route to the confirmed destination via LifeActions.
 */
interface CaptureRepository {
    fun observeForms(): Flow<List<LogFormEntity>>
    fun observeEntries(formId: Long): Flow<List<LogEntryEntity>>
    fun observeTasks(): Flow<List<TaskEntity>>

    /** Persists the capture and returns the routing suggestion (R12). */
    suspend fun submitQuick(text: String): LifeResult<PendingCapture>

    /** Brain-dump ([src 16], R10): persist raw stream, return split items for review. */
    suspend fun submitBrainDump(text: String): LifeResult<Pair<Long, List<DumpItem>>>

    /** Writes one confirmed brain-dump item. */
    suspend fun confirmDumpItem(captureId: Long, item: DumpItem): LifeResult<Unit>

    /** Executes the (possibly user-adjusted) destination for a pending capture. */
    suspend fun confirm(pending: PendingCapture, destination: CaptureDestination): LifeResult<Unit>

    suspend fun createForm(name: String, fields: List<LogFieldSpec>): LifeResult<Long>
    suspend fun deleteForm(formId: Long)
    suspend fun addEntry(formId: Long, values: Map<String, String>): LifeResult<Long>
    suspend fun setTaskDone(taskId: Long, done: Boolean)
}

@kotlinx.serialization.Serializable
data class LogFieldSpec(
    val name: String,
    val type: String,
    val unit: String? = null,
)

@Singleton
internal class DefaultCaptureRepository @Inject constructor(
    private val captureDao: CaptureDao,
    private val classifier: CaptureClassifier,
    private val brainDumpOrganizer: BrainDumpOrganizer,
    private val actionDispatcher: LifeActionDispatcher,
    private val eventBus: LifeEventBus,
    private val dispatchers: DispatcherProvider,
) : CaptureRepository {

    override fun observeForms(): Flow<List<LogFormEntity>> = captureDao.observeForms()
    override fun observeEntries(formId: Long): Flow<List<LogEntryEntity>> =
        captureDao.observeEntries(formId)
    override fun observeTasks(): Flow<List<TaskEntity>> = captureDao.observeTasks()

    override suspend fun submitQuick(text: String): LifeResult<PendingCapture> =
        withContext(dispatchers.io) {
            runCatchingLife {
                val captureId = captureDao.insertCapture(
                    CaptureEntity(
                        kind = CaptureKind.QUICK.name,
                        text = text,
                        blobVaultRef = null,
                        routedTo = null,
                        routedEntityId = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                eventBus.tryPublish(
                    LifeEvent.CaptureCreated(captureId, CaptureKind.QUICK, text),
                )
                val formNames = captureDao.observeForms().first().map { it.name }
                PendingCapture(
                    captureId = captureId,
                    text = text,
                    suggestion = classifier.classify(text, formNames),
                )
            }
        }

    override suspend fun submitBrainDump(text: String): LifeResult<Pair<Long, List<DumpItem>>> =
        withContext(dispatchers.io) {
            runCatchingLife {
                val captureId = captureDao.insertCapture(
                    CaptureEntity(
                        kind = CaptureKind.BRAIN_DUMP.name,
                        text = text,
                        blobVaultRef = null,
                        routedTo = null,
                        routedEntityId = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                eventBus.tryPublish(LifeEvent.CaptureCreated(captureId, CaptureKind.BRAIN_DUMP, text))
                val forms = captureDao.observeForms().first().map { it.name }
                captureId to brainDumpOrganizer.organize(text, forms)
            }
        }

    override suspend fun confirmDumpItem(captureId: Long, item: DumpItem): LifeResult<Unit> =
        confirm(
            PendingCapture(
                captureId = captureId,
                text = item.body,
                suggestion = CaptureSuggestion(item.destination, item.title),
            ),
            item.destination,
        )

    override suspend fun confirm(
        pending: PendingCapture,
        destination: CaptureDestination,
    ): LifeResult<Unit> = withContext(dispatchers.io) {
        val source = SourceRef(LifeModule.CAPTURE, pending.captureId.toString())
        val created: LifeResult<Long?> = when (destination) {
            CaptureDestination.NOTE -> actionDispatcher.dispatch(
                LifeAction.CreateNote(
                    title = pending.suggestion.title,
                    body = pending.text,
                    source = source,
                ),
            )
            CaptureDestination.TASK -> runCatchingLife {
                captureDao.insertTask(
                    TaskEntity(
                        title = pending.suggestion.title,
                        // Time-stamped to-dos surface in the calendar too (§Module 19).
                        dueAt = pending.suggestion.at,
                        sourceModule = LifeModule.CAPTURE.name,
                        sourceEntityId = pending.captureId,
                        createdAt = System.currentTimeMillis(),
                    ),
                ) as Long?
            }
            CaptureDestination.REMINDER, CaptureDestination.TIMER -> {
                val at = pending.suggestion.at
                if (at == null) {
                    LifeResult.Failure(LifeError.Validation("No time detected for this reminder"))
                } else {
                    val title = if (destination == CaptureDestination.TIMER) {
                        pending.suggestion.title.ifBlank { "Timer" }
                    } else {
                        pending.suggestion.title
                    }
                    actionDispatcher.dispatch(LifeAction.CreateReminder(title = title, at = at, source = source))
                }
            }
            CaptureDestination.EVENT -> {
                val at = pending.suggestion.at
                if (at == null) {
                    LifeResult.Failure(LifeError.Validation("No time detected for this event"))
                } else {
                    actionDispatcher.dispatch(
                        LifeAction.CreateCalendarEvent(
                            title = pending.suggestion.title,
                            startsAt = at,
                            endsAt = at + 3_600_000L,
                            source = source,
                        ),
                    )
                }
            }
            CaptureDestination.LOG -> {
                val form = pending.suggestion.formName?.let { captureDao.getFormByName(it) }
                if (form == null) {
                    LifeResult.Failure(LifeError.Validation("Log form not found"))
                } else {
                    runCatchingLife {
                        captureDao.insertEntry(
                            LogEntryEntity(
                                formId = form.id,
                                valuesJson = buildJsonObject {
                                    put("raw", JsonPrimitive(pending.text))
                                }.toString(),
                                source = "Quick",
                                at = System.currentTimeMillis(),
                            ),
                        ) as Long?
                    }
                }
            }
        }
        when (created) {
            is LifeResult.Success -> {
                captureDao.markRouted(pending.captureId, destination.name, created.value)
                LifeResult.Success(Unit)
            }
            is LifeResult.Failure -> created
        }
    }

    override suspend fun createForm(name: String, fields: List<LogFieldSpec>): LifeResult<Long> =
        withContext(dispatchers.io) {
            runCatchingLife {
                captureDao.insertForm(
                    LogFormEntity(
                        name = name,
                        fieldsJson = json.encodeToString(fields),
                        color = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

    override suspend fun deleteForm(formId: Long) = withContext(dispatchers.io) {
        captureDao.deleteForm(formId)
    }

    override suspend fun addEntry(formId: Long, values: Map<String, String>): LifeResult<Long> =
        withContext(dispatchers.io) {
            runCatchingLife {
                captureDao.insertEntry(
                    LogEntryEntity(
                        formId = formId,
                        valuesJson = buildJsonObject {
                            values.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                        }.toString(),
                        source = "Manual",
                        at = System.currentTimeMillis(),
                    ),
                )
            }
        }

    override suspend fun setTaskDone(taskId: Long, done: Boolean) = withContext(dispatchers.io) {
        captureDao.setTaskDone(taskId, done)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun parseFields(fieldsJson: String): List<LogFieldSpec> = try {
            json.decodeFromString(fieldsJson)
        } catch (e: Exception) {
            emptyList()
        }

        fun parseValues(valuesJson: String): Map<String, String> = try {
            json.decodeFromString<Map<String, String>>(valuesJson)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
