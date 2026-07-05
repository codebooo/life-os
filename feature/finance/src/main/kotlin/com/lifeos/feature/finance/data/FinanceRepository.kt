package com.lifeos.feature.finance.data

import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.getOrNull
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.finance.CategoryEntity
import com.lifeos.core.database.finance.FinanceDao
import com.lifeos.core.database.finance.SubscriptionEntity
import com.lifeos.core.database.finance.TransactionEntity
import com.lifeos.core.database.finance.WarrantyEntity
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface FinanceRepository {
    fun observeTransactions(): Flow<List<TransactionEntity>>
    fun observeUncategorized(): Flow<List<TransactionEntity>>
    fun observeCategories(): Flow<List<CategoryEntity>>
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>
    fun observeWarranties(): Flow<List<WarrantyEntity>>
    fun observeSpentSince(from: Long): Flow<Long>

    suspend fun addTransaction(merchant: String, amountCents: Long, source: String, docId: Long? = null): LifeResult<Long>
    suspend fun setCategory(txId: Long, categoryId: Long?)
    suspend fun addCategory(name: String): LifeResult<Long>
    suspend fun deleteTransaction(txId: Long)
    suspend fun setSubscriptionStatus(id: Long, status: String)
    suspend fun importCsv(csv: String): LifeResult<Int>

    /** R8: receipt into transaction (+ warranty + return-window reminder). Deduped. */
    suspend fun recordReceipt(action: LifeAction.RecordReceipt): LifeResult<Long?>
}

@Singleton
internal class DefaultFinanceRepository @Inject constructor(
    private val financeDao: FinanceDao,
    private val actionDispatcher: dagger.Lazy<LifeActionDispatcher>,
    private val dispatchers: DispatcherProvider,
) : FinanceRepository {

    override fun observeTransactions() = financeDao.observeTransactions()
    override fun observeUncategorized() = financeDao.observeUncategorized()
    override fun observeCategories() = financeDao.observeCategories()
    override fun observeSubscriptions() = financeDao.observeSubscriptions()
    override fun observeWarranties() = financeDao.observeWarranties()
    override fun observeSpentSince(from: Long) = financeDao.observeSpentSince(from)

    override suspend fun addTransaction(
        merchant: String,
        amountCents: Long,
        source: String,
        docId: Long?,
    ): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            val id = financeDao.insertTransaction(
                TransactionEntity(
                    merchant = merchant,
                    amountCents = amountCents,
                    categoryId = null,
                    at = System.currentTimeMillis(),
                    source = source,
                    sourceDocId = docId,
                ),
            )
            detectSubscription(merchant, amountCents)
            id
        }
    }

    override suspend fun setCategory(txId: Long, categoryId: Long?) = withContext(dispatchers.io) {
        financeDao.setCategory(txId, categoryId)
    }

    override suspend fun addCategory(name: String): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife { financeDao.insertCategory(CategoryEntity(name = name.trim())) }
    }

    override suspend fun deleteTransaction(txId: Long) = withContext(dispatchers.io) {
        financeDao.deleteTransaction(txId)
    }

    override suspend fun setSubscriptionStatus(id: Long, status: String) =
        withContext(dispatchers.io) { financeDao.setSubscriptionStatus(id, status) }

    /** Lines: date,merchant,amount — Mint/bank-CSV column order configurable later. */
    override suspend fun importCsv(csv: String): LifeResult<Int> = withContext(dispatchers.io) {
        runCatchingLife {
            var imported = 0
            csv.lineSequence().drop(if (csv.startsWith("date", true)) 1 else 0).forEach { line ->
                val cols = line.split(',', ';').map { it.trim().trim('"') }
                if (cols.size < 3) return@forEach
                val amount = cols.last().replace(',', '.').toDoubleOrNull() ?: return@forEach
                val merchant = cols[1].ifBlank { return@forEach }
                financeDao.insertTransaction(
                    TransactionEntity(
                        merchant = merchant,
                        amountCents = (amount * 100).toLong(),
                        categoryId = null,
                        at = System.currentTimeMillis(),
                        source = "IMPORT",
                    ),
                )
                detectSubscription(merchant, (amount * 100).toLong())
                imported++
            }
            imported
        }
    }

    override suspend fun recordReceipt(action: LifeAction.RecordReceipt): LifeResult<Long?> =
        withContext(dispatchers.io) {
            runCatchingLife {
                val merchant = action.merchant ?: "Receipt"
                val total = action.totalCents ?: 0L
                val now = System.currentTimeMillis()

                // R8 dedupe: same merchant+total within 2 days.
                val duplicates = financeDao.countDuplicates(
                    merchant, -total, now - TimeUnit.DAYS.toMillis(2), now + TimeUnit.DAYS.toMillis(1),
                )
                if (duplicates > 0) return@runCatchingLife null

                val txId = financeDao.insertTransaction(
                    TransactionEntity(
                        merchant = merchant,
                        amountCents = -total,
                        categoryId = null,
                        at = now,
                        source = "RECEIPT",
                        sourceDocId = action.docId,
                    ),
                )

                val warrantyMonths = action.warrantyMonths
                if (warrantyMonths != null && warrantyMonths > 0) {
                    val expiry = now + TimeUnit.DAYS.toMillis(30L * warrantyMonths)
                    val reminderId = actionDispatcher.get().dispatch(
                        LifeAction.CreateReminder(
                            title = "Warranty for $merchant purchase expires soon",
                            at = expiry - TimeUnit.DAYS.toMillis(14),
                            source = SourceRef(LifeModule.FINANCE, txId.toString()),
                        ),
                    ).getOrNull()
                    financeDao.insertWarranty(
                        WarrantyEntity(
                            productName = merchant,
                            purchaseTxId = txId,
                            purchasedAt = now,
                            warrantyMonths = warrantyMonths,
                            reminderId = reminderId,
                            docId = action.docId,
                        ),
                    )
                }
                detectSubscription(merchant, -total)
                txId
            }
        }

    /** R9: on each charge, re-check merchant history; upsert active subscription. */
    private suspend fun detectSubscription(merchant: String, amountCents: Long) {
        if (amountCents >= 0) return
        val history = financeDao.similarTransactions(
            merchant, amountCents, tolerance = maxOf(100, -amountCents / 20),
        )
        val detection = SubscriptionDetector.detect(history) ?: return
        val existing = financeDao.subscriptionFor(merchant)
        if (existing?.status == "IGNORED" || existing?.status == "CANCELLED") return
        financeDao.upsertSubscription(
            SubscriptionEntity(
                id = existing?.id ?: 0,
                merchant = merchant,
                amountCents = detection.amountCents,
                cadence = detection.cadence,
                lastChargedAt = detection.lastChargedAt,
                cancelUrl = existing?.cancelUrl,
            ),
        )
    }
}
