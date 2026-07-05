package com.lifeos.core.database.finance

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A money movement (§Module 6). Amounts in cents; negative = expense. */
@Entity(tableName = "transactions", indices = [Index("categoryId"), Index("at")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amountCents: Long,
    val categoryId: Long?,
    val at: Long,
    /** MANUAL, RECEIPT, IMPORT */
    val source: String,
    val sourceDocId: Long? = null,
    val notes: String? = null,
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Detected recurring charge ([src 26]). */
@Entity(tableName = "subscriptions", indices = [Index(value = ["merchant"], unique = true)])
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amountCents: Long,
    /** MONTHLY, YEARLY */
    val cadence: String,
    val lastChargedAt: Long,
    /** ACTIVE, CANCELLED, IGNORED */
    val status: String = "ACTIVE",
    val cancelUrl: String? = null,
)

/** Purchase warranty window ([src 25]). */
@Entity(tableName = "warranties")
data class WarrantyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val purchaseTxId: Long?,
    val purchasedAt: Long,
    val warrantyMonths: Int,
    val reminderId: Long?,
    val docId: Long?,
)

@Dao
interface FinanceDao {

    @Insert
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(tx: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY at DESC LIMIT 500")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL ORDER BY at DESC")
    fun observeUncategorized(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE merchant = :merchant AND ABS(amountCents - :amountCents) <= :tolerance ORDER BY at")
    suspend fun similarTransactions(merchant: String, amountCents: Long, tolerance: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE merchant = :merchant AND amountCents = :amountCents AND at BETWEEN :from AND :to")
    suspend fun countDuplicates(merchant: String, amountCents: Long, from: Long, to: Long): Int

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE at >= :from AND amountCents < 0")
    fun observeSpentSince(from: Long): Flow<Long>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :txId")
    suspend fun setCategory(txId: Long, categoryId: Long?)

    @Query("DELETE FROM transactions WHERE id = :txId")
    suspend fun deleteTransaction(txId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(subscription: SubscriptionEntity): Long

    @Query("SELECT * FROM subscriptions WHERE merchant = :merchant")
    suspend fun subscriptionFor(merchant: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE status = 'ACTIVE' ORDER BY amountCents DESC")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("UPDATE subscriptions SET status = :status WHERE id = :id")
    suspend fun setSubscriptionStatus(id: Long, status: String)

    @Insert
    suspend fun insertWarranty(warranty: WarrantyEntity): Long

    @Query("SELECT * FROM warranties ORDER BY purchasedAt DESC")
    fun observeWarranties(): Flow<List<WarrantyEntity>>

    @Query("DELETE FROM warranties WHERE id = :id")
    suspend fun deleteWarranty(id: Long)
}
