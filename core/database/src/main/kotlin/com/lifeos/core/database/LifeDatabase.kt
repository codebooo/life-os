package com.lifeos.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.capture.CaptureEntity
import com.lifeos.core.database.capture.LogEntryEntity
import com.lifeos.core.database.capture.LogFormEntity
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.database.finance.CategoryEntity
import com.lifeos.core.database.finance.FinanceDao
import com.lifeos.core.database.finance.SubscriptionEntity
import com.lifeos.core.database.finance.TransactionEntity
import com.lifeos.core.database.finance.WarrantyEntity
import com.lifeos.core.database.books.BookDao
import com.lifeos.core.database.books.BookEntity
import com.lifeos.core.database.route.RouteDao
import com.lifeos.core.database.route.SavedPlaceEntity
import com.lifeos.core.database.email.EmailDao
import com.lifeos.core.database.email.EmailMessageEntity
import com.lifeos.core.database.scan.ScanDao
import com.lifeos.core.database.scan.ScannedDocumentEntity
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.database.reminders.ReminderEntity
import com.lifeos.core.database.todo.TaskListEntity
import com.lifeos.core.database.todo.TodoDao
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.database.dhl.PackageEntity
import com.lifeos.core.database.dhl.TrackingEventEntity
import com.lifeos.core.database.chat.AiConversationEntity
import com.lifeos.core.database.chat.AiMessageEntity
import com.lifeos.core.database.chat.ChatDao
import com.lifeos.core.database.messages.MessageDao
import com.lifeos.core.database.messages.UnifiedMessageEntity
import com.lifeos.core.database.notes.NoteDao
import com.lifeos.core.database.notes.NoteEmbeddingEntity
import com.lifeos.core.database.notes.NoteEntity
import com.lifeos.core.database.notes.NoteLinkEntity
import com.lifeos.core.database.vault.VaultBlobDao
import com.lifeos.core.database.vault.VaultBlobEntity
import com.lifeos.core.database.memex.ArchiveItemEntity
import com.lifeos.core.database.memex.MemexDao
import com.lifeos.core.database.agentic.MacroDao
import com.lifeos.core.database.agentic.MacroEntity
import com.lifeos.core.database.adhd.FocusDao
import com.lifeos.core.database.adhd.FocusSessionEntity
import com.lifeos.core.database.evolution.EvolutionDao
import com.lifeos.core.database.evolution.InteractionLogEntity

/**
 * The single app database (§1.7), feature-partitioned by package with
 * centralized migrations. Entities land here module-by-module as phases ship.
 */
@Database(
    entities = [
        VaultBlobEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
        CaptureEntity::class,
        LogFormEntity::class,
        LogEntryEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        NoteLinkEntity::class,
        NoteEmbeddingEntity::class,
        ReminderEntity::class,
        TaskListEntity::class,
        CalendarEventEntity::class,
        UnifiedMessageEntity::class,
        PackageEntity::class,
        TrackingEventEntity::class,
        ScannedDocumentEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        SubscriptionEntity::class,
        WarrantyEntity::class,
        EmailMessageEntity::class,
        BookEntity::class,
        SavedPlaceEntity::class,
        ArchiveItemEntity::class,
        MacroEntity::class,
        FocusSessionEntity::class,
        InteractionLogEntity::class,
    ],
    version = 11,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ],
)
abstract class LifeDatabase : RoomDatabase() {
    abstract fun vaultBlobDao(): VaultBlobDao
    abstract fun chatDao(): ChatDao
    abstract fun captureDao(): CaptureDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun todoDao(): TodoDao
    abstract fun calendarDao(): CalendarDao
    abstract fun messageDao(): MessageDao
    abstract fun packageDao(): PackageDao
    abstract fun scanDao(): ScanDao
    abstract fun financeDao(): FinanceDao
    abstract fun emailDao(): EmailDao
    abstract fun bookDao(): BookDao
    abstract fun routeDao(): RouteDao
    abstract fun memexDao(): MemexDao
    abstract fun macroDao(): MacroDao
    abstract fun focusDao(): FocusDao
    abstract fun evolutionDao(): EvolutionDao

    companion object {
        const val NAME = "life-os.db"
    }
}
