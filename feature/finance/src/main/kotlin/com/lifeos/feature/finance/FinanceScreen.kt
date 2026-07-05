package com.lifeos.feature.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.SectionHeader
import java.text.DateFormat
import java.util.Date

private fun cents(value: Long): String = "%s%d.%02d €".format(
    if (value < 0) "−" else "",
    Math.abs(value) / 100,
    Math.abs(value) % 100,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceRoute(viewModel: FinanceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(FinanceUiEvent.DismissMessage)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Finance") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryScrollableTabRow(selectedTabIndex = uiState.tab) {
                listOf("Overview", "Categorize", "Subscriptions", "Warranties", "Import")
                    .forEachIndexed { index, label ->
                        Tab(
                            selected = uiState.tab == index,
                            onClick = { viewModel.onEvent(FinanceUiEvent.SelectTab(index)) },
                            text = { Text(label) },
                        )
                    }
            }
            when (uiState.tab) {
                0 -> OverviewTab(uiState, viewModel::onEvent)
                1 -> CategorizeTab(uiState, viewModel::onEvent)
                2 -> SubscriptionsTab(uiState, viewModel::onEvent)
                3 -> WarrantiesTab(uiState)
                else -> ImportTab(uiState, viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun OverviewTab(uiState: FinanceUiState, onEvent: (FinanceUiEvent) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Spent this month", style = MaterialTheme.typography.labelLarge)
                Text(cents(uiState.spentThisMonth), style = MaterialTheme.typography.displaySmall)
                Text(
                    "${uiState.subscriptions.size} active subscriptions · " +
                        cents(uiState.subscriptions.sumOf { -it.amountCents * if (it.cadence == "YEARLY") 1 else 12 }) +
                        "/year recurring",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.newMerchant,
                onValueChange = { onEvent(FinanceUiEvent.NewMerchantChanged(it)) },
                label = { Text("Merchant") },
                singleLine = true,
                modifier = Modifier.weight(1.4f),
            )
            OutlinedTextField(
                value = uiState.newAmount,
                onValueChange = { onEvent(FinanceUiEvent.NewAmountChanged(it)) },
                label = { Text("−12.99") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onEvent(FinanceUiEvent.AddTransaction) }) { Text("Add") }
        }
        SectionHeader(title = "Recent")
        LazyColumn {
            items(uiState.transactions, key = { it.id }) { tx ->
                ListItem(
                    headlineContent = { Text(tx.merchant) },
                    supportingContent = {
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(tx.at)) +
                                " · " + tx.source.lowercase(),
                        )
                    },
                    trailingContent = {
                        Row {
                            Text(cents(tx.amountCents), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onEvent(FinanceUiEvent.DeleteTransaction(tx.id)) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CategorizeTab(uiState: FinanceUiState, onEvent: (FinanceUiEvent) -> Unit) {
    if (uiState.uncategorized.isEmpty()) {
        EmptyState(title = "All categorized", description = "New transactions land here for a one-tap sort.")
        return
    }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.newCategory,
                onValueChange = { onEvent(FinanceUiEvent.NewCategoryChanged(it)) },
                label = { Text("New category") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onEvent(FinanceUiEvent.AddCategory) }) { Text("Add") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.uncategorized, key = { it.id }) { tx ->
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${tx.merchant}  ${cents(tx.amountCents)}", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.categories, key = { it.id }) { category ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onEvent(FinanceUiEvent.Categorize(tx.id, category.id)) },
                                    label = { Text(category.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsTab(uiState: FinanceUiState, onEvent: (FinanceUiEvent) -> Unit) {
    if (uiState.subscriptions.isEmpty()) {
        EmptyState(
            title = "No recurring charges found",
            description = "Detected automatically once a merchant charges you 3+ times on a regular cadence.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.subscriptions, key = { it.id }) { sub ->
            ListItem(
                headlineContent = { Text(sub.merchant) },
                supportingContent = {
                    val yearly = -sub.amountCents * if (sub.cadence == "YEARLY") 1 else 12
                    Text("${cents(sub.amountCents)} ${sub.cadence.lowercase()} · ${cents(yearly)}/year")
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = { onEvent(FinanceUiEvent.SubscriptionStatus(sub.id, "CANCELLED")) },
                            label = { Text("Cancelled") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = { onEvent(FinanceUiEvent.SubscriptionStatus(sub.id, "IGNORED")) },
                            label = { Text("Keep") },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun WarrantiesTab(uiState: FinanceUiState) {
    if (uiState.warranties.isEmpty()) {
        EmptyState(
            title = "No warranties",
            description = "Scan a receipt that mentions a warranty — the window and a return reminder are filed automatically.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.warranties, key = { it.id }) { warranty ->
            val expiry = warranty.purchasedAt + warranty.warrantyMonths * 30L * 86_400_000L
            ListItem(
                headlineContent = { Text(warranty.productName) },
                supportingContent = {
                    Text(
                        "Bought " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(warranty.purchasedAt)) +
                            " · ${warranty.warrantyMonths} months · expires " +
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiry)),
                    )
                },
            )
        }
    }
}

@Composable
private fun ImportTab(uiState: FinanceUiState, onEvent: (FinanceUiEvent) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Paste CSV (date, merchant, amount) — Mint/bank exports. Everything stays on-device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = uiState.csvText,
            onValueChange = { onEvent(FinanceUiEvent.CsvChanged(it)) },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onEvent(FinanceUiEvent.ImportCsv) },
            enabled = uiState.csvText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import")
        }
    }
}
