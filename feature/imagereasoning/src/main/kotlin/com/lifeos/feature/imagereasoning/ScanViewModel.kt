package com.lifeos.feature.imagereasoning

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.scan.ScannedDocumentEntity
import com.lifeos.feature.imagereasoning.data.ReceiptExtraction
import com.lifeos.feature.imagereasoning.data.ScanAnalysis
import com.lifeos.feature.imagereasoning.data.ScanKind
import com.lifeos.feature.imagereasoning.data.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScanScreenMode { CAPTURE, REVIEW, HISTORY }

data class ScanUiState(
    val mode: ScanScreenMode = ScanScreenMode.CAPTURE,
    val analyzing: Boolean = false,
    val analysis: ScanAnalysis? = null,
    // Editable review fields
    val merchant: String = "",
    val total: String = "",
    val warrantyMonths: String = "",
    val boardTitle: String = "Board capture",
    val history: List<ScannedDocumentEntity> = emptyList(),
    val done: Boolean = false,
    val error: String? = null,
)

sealed interface ScanUiEvent {
    data class PhotoTaken(val uri: Uri, val hint: ScanKind?) : ScanUiEvent
    data class MerchantChanged(val value: String) : ScanUiEvent
    data class TotalChanged(val value: String) : ScanUiEvent
    data class WarrantyChanged(val value: String) : ScanUiEvent
    data class BoardTitleChanged(val value: String) : ScanUiEvent
    data object ConfirmReceipt : ScanUiEvent
    data object ConfirmBoard : ScanUiEvent
    data object ShowHistory : ScanUiEvent
    data object BackToCapture : ScanUiEvent
    data class DeleteDoc(val docId: Long) : ScanUiEvent
    data object DismissError : ScanUiEvent
}

sealed interface ScanUiEffect

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
) : LifeViewModel<ScanUiState, ScanUiEvent, ScanUiEffect>(ScanUiState()) {

    init {
        viewModelScope.launch {
            scanRepository.observeHistory().collect { history ->
                updateState { it.copy(history = history) }
            }
        }
    }

    override fun onEvent(event: ScanUiEvent) {
        when (event) {
            is ScanUiEvent.PhotoTaken -> analyze(event.uri, event.hint)
            is ScanUiEvent.MerchantChanged -> updateState { it.copy(merchant = event.value) }
            is ScanUiEvent.TotalChanged -> updateState { it.copy(total = event.value) }
            is ScanUiEvent.WarrantyChanged -> updateState { it.copy(warrantyMonths = event.value) }
            is ScanUiEvent.BoardTitleChanged -> updateState { it.copy(boardTitle = event.value) }
            ScanUiEvent.ConfirmReceipt -> confirmReceipt()
            ScanUiEvent.ConfirmBoard -> confirmBoard()
            ScanUiEvent.ShowHistory -> updateState { it.copy(mode = ScanScreenMode.HISTORY) }
            ScanUiEvent.BackToCapture ->
                updateState { it.copy(mode = ScanScreenMode.CAPTURE, analysis = null, done = false) }
            is ScanUiEvent.DeleteDoc -> viewModelScope.launch { scanRepository.delete(event.docId) }
            ScanUiEvent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private fun analyze(uri: Uri, hint: ScanKind?) {
        updateState { it.copy(analyzing = true, error = null) }
        viewModelScope.launch {
            when (val result = scanRepository.analyze(uri, hint)) {
                is LifeResult.Success -> {
                    val analysis = result.value
                    updateState {
                        it.copy(
                            analyzing = false,
                            mode = ScanScreenMode.REVIEW,
                            analysis = analysis,
                            merchant = analysis.receipt?.merchant.orEmpty(),
                            total = analysis.receipt?.totalCents?.let(::formatCents).orEmpty(),
                            warrantyMonths = analysis.receipt?.warrantyMonths?.toString().orEmpty(),
                        )
                    }
                }
                is LifeResult.Failure ->
                    updateState { it.copy(analyzing = false, error = result.error.message) }
            }
        }
    }

    private fun confirmReceipt() {
        val state = uiState.value
        val analysis = state.analysis ?: return
        viewModelScope.launch {
            val edited = ReceiptExtraction(
                merchant = state.merchant.trim().ifEmpty { null },
                totalCents = parseCents(state.total),
                dateText = analysis.receipt?.dateText,
                warrantyMonths = state.warrantyMonths.trim().toIntOrNull(),
            )
            when (val result = scanRepository.confirmReceipt(analysis, edited)) {
                is LifeResult.Success -> updateState { it.copy(done = true) }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }

    private fun confirmBoard() {
        val state = uiState.value
        val analysis = state.analysis ?: return
        viewModelScope.launch {
            when (val result = scanRepository.confirmBoard(analysis, state.boardTitle.trim())) {
                is LifeResult.Success -> updateState { it.copy(done = true) }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }

    companion object {
        fun formatCents(cents: Long): String = "%d.%02d".format(cents / 100, cents % 100)

        fun parseCents(text: String): Long? {
            val trimmed = text.trim().replace(',', '.')
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split('.')
            return when (parts.size) {
                1 -> parts[0].toLongOrNull()?.times(100)
                2 -> {
                    val euros = parts[0].toLongOrNull() ?: return null
                    val cents = parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: return null
                    euros * 100 + cents
                }
                else -> null
            }
        }
    }
}
