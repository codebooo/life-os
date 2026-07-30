package com.lifeos.core.database.triggers

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * One automation rule (§Module Triggers): when something happens, and the
 * conditions hold, do something.
 *
 * Trigger and action arguments are stored as plain strings rather than a schema
 * per type: the rule engine owns their meaning, and a rule that cannot be parsed
 * is shown as broken instead of silently doing nothing.
 */
@Entity(tableName = "trigger_rules")
data class TriggerRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** TIME, PLACE_ENTER, PLACE_LEAVE, WIFI, NFC_TAG, SIGNAL, SCREEN_TIME, BATTERY, REMINDER_FIRED. */
    val triggerType: String,
    /** Minute of day, place id, SSID, tag id, package or keyword, threshold. */
    val triggerArg: String = "",
    /** Comma-separated day numbers (1=Mon..7=Sun); empty = every day. */
    val days: String = "",
    /** Only fire inside this window, as "start-end" minutes of day; empty = always. */
    val window: String = "",
    /** TASK, NOTE, REMINDER, TIMER, FOCUS, BRICK_ON, BRICK_OFF, MACRO, PASTE, DOWNLOAD, SCREEN_TIME_EXPORT, WATER_PLANT. */
    val actionType: String,
    val actionArg: String = "",
    val enabled: Boolean = true,
    /** Rules can be run by hand from the list, which is also the dry run. */
    val lastFiredAt: Long? = null,
    val fireCount: Int = 0,
    val createdAt: Long,
)

/** Audit trail: what fired, when, and whether the action worked. */
@Entity(tableName = "trigger_fires")
data class TriggerFireEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val ruleName: String,
    val at: Long,
    val outcome: String,
    val detail: String = "",
)

@Dao
interface TriggerDao {

    @Query("SELECT * FROM trigger_rules ORDER BY name")
    fun observeRules(): Flow<List<TriggerRuleEntity>>

    @Query("SELECT * FROM trigger_rules ORDER BY name")
    suspend fun allRules(): List<TriggerRuleEntity>

    @Query("SELECT * FROM trigger_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<TriggerRuleEntity>

    @Query("SELECT * FROM trigger_rules WHERE id = :id")
    suspend fun rule(id: Long): TriggerRuleEntity?

    @Insert
    suspend fun insertRule(rule: TriggerRuleEntity): Long

    @Update
    suspend fun updateRule(rule: TriggerRuleEntity)

    @Query("DELETE FROM trigger_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Insert
    suspend fun insertFire(fire: TriggerFireEntity): Long

    @Query("SELECT * FROM trigger_fires ORDER BY at DESC LIMIT 60")
    fun observeFires(): Flow<List<TriggerFireEntity>>

    @Query("SELECT * FROM trigger_fires ORDER BY at DESC LIMIT :limit")
    suspend fun recentFires(limit: Int): List<TriggerFireEntity>

    @Query("DELETE FROM trigger_fires WHERE at < :before")
    suspend fun trimFires(before: Long)
}
