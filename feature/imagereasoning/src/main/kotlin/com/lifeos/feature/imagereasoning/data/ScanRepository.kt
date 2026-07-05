package com.lifeos.feature.imagereasoning.data

import android.net.Uri
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.getOrNull
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.scan.ScanDao
import com.lifeos.core.database.scan.ScannedDocumentEntity
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class ScanKind { RECEIPT, WHITEBOARD, OTHER }

/** A completed analysis, ready for the user's review before anything routes. */
data class ScanAnalysis(
    val docId: Long,
    val kind: ScanKind,
    val ocrText: String,
    val receipt: ReceiptExtraction?,
    val board: BoardParser.Board?,
    val barcodes: List<String>,
)

interface ScanRepository {
    fun observeHistory(): Flow<List<ScannedDocumentEntity>>

    /** OCR + classify + extract; persists the document, routes nothing yet. */
    suspend fun analyze(imageUri: Uri, hint: ScanKind?): LifeResult<ScanAnalysis>

    /** Confirms a receipt: publishes [LifeEvent.ReceiptScanned] (R8's trigger). */
    suspend fun confirmReceipt(analysis: ScanAnalysis, edited: ReceiptExtraction): LifeResult<Unit>

    /** Confirms a board: writes the Markdown board to Notes ([src 15]). */
    suspend fun confirmBoard(analysis: ScanAnalysis, title: String): LifeResult<Long?>

    suspend fun delete(docId: Long)
}

@Singleton
internal class DefaultScanRepository @Inject constructor(
    private val scanDao: ScanDao,
    private val ocrService: OcrService,
    private val actionDispatcher: dagger.Lazy<LifeActionDispatcher>,
    private val eventBus: LifeEventBus,
    private val dispatchers: DispatcherProvider,
) : ScanRepository {

    override fun observeHistory(): Flow<List<ScannedDocumentEntity>> = scanDao.observeAll()

    override suspend fun analyze(imageUri: Uri, hint: ScanKind?): LifeResult<ScanAnalysis> =
        withContext(dispatchers.io) {
            runCatchingLife {
                val ocr = ocrService.analyze(imageUri)
                val kind = hint ?: classify(ocr)
                val receipt = if (kind == ScanKind.RECEIPT) ReceiptExtractor.extract(ocr.fullText) else null
                val board = if (kind == ScanKind.WHITEBOARD) BoardParser.parse(ocr.blocks) else null

                val docId = scanDao.insert(
                    ScannedDocumentEntity(
                        kind = kind.name,
                        imagePath = imageUri.path,
                        ocrText = ocr.fullText,
                        extractedJson = receipt?.let { json.encodeToString(it) },
                        linkedModule = null,
                        linkedEntityId = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                ScanAnalysis(docId, kind, ocr.fullText, receipt, board, ocr.barcodes)
            }
        }

    override suspend fun confirmReceipt(
        analysis: ScanAnalysis,
        edited: ReceiptExtraction,
    ): LifeResult<Unit> = withContext(dispatchers.io) {
        runCatchingLife {
            scanDao.getById(analysis.docId)?.let {
                scanDao.update(it.copy(extractedJson = json.encodeToString(edited)))
            }
            eventBus.tryPublish(
                LifeEvent.ReceiptScanned(
                    docId = analysis.docId,
                    merchant = edited.merchant,
                    totalCents = edited.totalCents,
                    warrantyMonths = edited.warrantyMonths,
                ),
            )
            Unit
        }
    }

    override suspend fun confirmBoard(analysis: ScanAnalysis, title: String): LifeResult<Long?> =
        withContext(dispatchers.io) {
            val board = analysis.board
                ?: return@withContext LifeResult.Failure(
                    com.lifeos.core.common.result.LifeError.Validation("No board detected"),
                )
            val result = actionDispatcher.get().dispatch(
                LifeAction.CreateNote(
                    title = title,
                    body = BoardParser.toMarkdown(board),
                    source = SourceRef(LifeModule.IMAGE_REASONING, analysis.docId.toString()),
                ),
            )
            result.getOrNull()?.let { noteId ->
                scanDao.getById(analysis.docId)?.let {
                    scanDao.update(it.copy(linkedModule = LifeModule.NOTES.name, linkedEntityId = noteId))
                }
            }
            result
        }

    override suspend fun delete(docId: Long) = withContext(dispatchers.io) {
        scanDao.delete(docId)
    }

    private fun classify(ocr: OcrResult): ScanKind {
        val text = ocr.fullText.lowercase()
        val receiptSignals = listOf("summe", "total", "mwst", "vat", "eur", "€", "kasse", "receipt", "beleg")
            .count { text.contains(it) }
        val amountLines = ocr.fullText.lines().count { it.contains(Regex("\\d+[.,]\\d{2}")) }
        return when {
            receiptSignals >= 2 || amountLines >= 4 -> ScanKind.RECEIPT
            ocr.blocks.size >= 4 -> ScanKind.WHITEBOARD
            else -> ScanKind.OTHER
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
