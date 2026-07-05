package com.lifeos.feature.finance

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.finance.CategoryEntity
import com.lifeos.core.database.finance.SubscriptionEntity
import com.lifeos.core.database.finance.TransactionEntity
import com.lifeos.core.database.finance.WarrantyEntity
import com.lifeos.feature.finance.data.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class FinanceUiState(
    val tab: Int = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    val uncategorized: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val warranties: List<WarrantyEntity> = emptyList(),
    val spentThisMonth: Long = 0,
    val newMerchant: String = "",
    val newAmount: String = "",
    val newCategory: String = "",
    val csvText: String = "",
    val message: String? = null,
)

sealed interface FinanceUiEvent {
    data class SelectTab(val index: Int) : FinanceUiEvent
    data class NewMerchantChanged(val value: String) : FinanceUiEvent
    data class NewAmountChanged(val value: String) : FinanceUiEvent
    data object AddTransaction : FinanceUiEvent
    data class DeleteTransaction(val id: Long) : FinanceUiEvent
    data class NewCategoryChanged(val value: String) : FinanceUiEvent
    data object AddCategory : FinanceUiEvent
    data class Categorize(val txId: Long, val categoryId: Long) : FinanceUiEvent
    data class SubscriptionStatus(val id: Long, val status: String) : FinanceUiEvent
    data class CsvChanged(val value: String) : FinanceUiEvent
    data object ImportCsv : FinanceUiEvent
    data object DismissMessage : FinanceUiEvent
}

sealed interface FinanceUiEffect

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
) : LifeViewModel<FinanceUiState, FinanceUiEvent, FinanceUiEffect>(FinanceUiState()) {

    init {
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        viewModelScope.launch {
            financeRepository.observeTransactions().collect { list ->
                updateState { it.copy(transactions = list) }
            }
        }
        viewModelScope.launch {
            financeRepository.observeUncategorized().collect { list ->
                updateState { it.copy(uncategorized = list) }
            }
        }
        viewModelScope.launch {
            financeRepository.observeCategories().collect { list ->
                updateState { it.copy(categories = list) }
            }
        }
        viewModelScope.launch {
            financeRepository.observeSubscriptions().collect { list ->
                updateState { it.copy(subscriptions = list) }
            }
        }
        viewModelScope.launch {
            financeRepository.observeWarranties().collect { list ->
                updateState { it.copy(warranties = list) }
            }
        }
        viewModelScope.launch {
            financeRepository.observeSpentSince(monthStart).collect { spent ->
                updateState { it.copy(spentThisMonth = spent) }
            }
        }
        viewModelScope.launch {
            listOf("Groceries", "Eating out", "Transport", "Home", "Fun", "Health").forEach {
                financeRepository.addCategory(it)
            }
        }
    }

    override fun onEvent(event: FinanceUiEvent) {
        when (event) {
            is FinanceUiEvent.SelectTab -> updateState { it.copy(tab = event.index) }
            is FinanceUiEvent.NewMerchantChanged -> updateState { it.copy(newMerchant = event.value) }
            is FinanceUiEvent.NewAmountChanged -> updateState { it.copy(newAmount = event.value) }
            FinanceUiEvent.AddTransaction -> addTransaction()
            is FinanceUiEvent.DeleteTransaction -> viewModelScope.launch {
                financeRepository.deleteTransaction(event.id)
            }
            is FinanceUiEvent.NewCategoryChanged -> updateState { it.copy(newCategory = event.value) }
            FinanceUiEvent.AddCategory -> viewModelScope.launch {
                val name = uiState.value.newCategory.trim()
                if (name.isNotEmpty()) {
                    financeRepository.addCategory(name)
                    updateState { it.copy(newCategory = "") }
                }
            }
            is FinanceUiEvent.Categorize -> viewModelScope.launch {
                financeRepository.setCategory(event.txId, event.categoryId)
            }
            is FinanceUiEvent.SubscriptionStatus -> viewModelScope.launch {
                financeRepository.setSubscriptionStatus(event.id, event.status)
            }
            is FinanceUiEvent.CsvChanged -> updateState { it.copy(csvText = event.value) }
            FinanceUiEvent.ImportCsv -> importCsv()
            FinanceUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun addTransaction() {
        val state = uiState.value
        val merchant = state.newMerchant.trim()
        val amount = state.newAmount.trim().replace(',', '.').replace("−", "-").toDoubleOrNull()
        if (merchant.isEmpty() || amount == null) return
        viewModelScope.launch {
            financeRepository.addTransaction(merchant, (amount * 100).toLong(), "MANUAL")
            updateState { it.copy(newMerchant = "", newAmount = "") }
        }
    }

    private fun importCsv() {
        viewModelScope.launch {
            when (val result = financeRepository.importCsv(uiState.value.csvText)) {
                is LifeResult.Success ->
                    updateState { it.copy(csvText = "", message = "Imported ${result.value} transactions") }
                is LifeResult.Failure -> updateState { it.copy(message = result.error.message) }
            }
        }
    }
}
