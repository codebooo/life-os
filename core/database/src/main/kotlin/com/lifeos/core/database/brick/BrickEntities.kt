package com.lifeos.core.database.brick

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A blocking mode (§Module Brick). Holds which apps are blocked, how the mode
 * turns on, and what it takes to turn off again.
 *
 * [activator]/[deactivator]: NFC, TIME, MANUAL.
 * [nfcTagId] is the paired tag's hardware id (hex) — the tap that flips the mode.
 * [startMinuteOfDay]/[endMinuteOfDay] drive TIME activation (minutes since midnight).
 * [strict] blocks the in-app "stop" button, so only the real condition ends it.
 */
@Entity(tableName = "brick_profiles")
data class BrickProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Package names, newline-separated. */
    val blockedPackages: String,
    val activator: String = "MANUAL",
    val deactivator: String = "MANUAL",
    val nfcTagId: String? = null,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val strict: Boolean = false,
    val createdAt: Long,
)

/** Optional per-app daily allowance inside a profile (minutes; 0 = fully blocked). */
@Entity(tableName = "brick_app_limits", primaryKeys = ["profileId", "packageName"])
data class BrickAppLimitEntity(
    val profileId: Long,
    val packageName: String,
    val dailyMinutes: Int,
)

/** One run of a profile. [endedAt] null while the mode is live. */
@Entity(tableName = "brick_sessions")
data class BrickSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** How it started: NFC / TIME / MANUAL. */
    val startedBy: String,
    /** Attempts to open a blocked app while the mode was live. */
    val blockedAttempts: Int = 0,
)

/** Foreground minutes per app per day — powers the daily-allowance limits. */
@Entity(tableName = "brick_usage", primaryKeys = ["date", "packageName"])
data class BrickUsageEntity(
    /** yyyy-MM-dd, local zone. */
    val date: String,
    val packageName: String,
    val secondsUsed: Long,
)

@Dao
interface BrickDao {

    // ---- profiles ---------------------------------------------------------
    @Insert
    suspend fun insertProfile(profile: BrickProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: BrickProfileEntity)

    @Query("SELECT * FROM brick_profiles ORDER BY createdAt")
    fun observeProfiles(): Flow<List<BrickProfileEntity>>

    @Query("SELECT * FROM brick_profiles")
    suspend fun allProfiles(): List<BrickProfileEntity>

    @Query("SELECT * FROM brick_profiles WHERE id = :id")
    suspend fun profile(id: Long): BrickProfileEntity?

    @Query("SELECT * FROM brick_profiles WHERE UPPER(TRIM(nfcTagId)) = UPPER(TRIM(:tagId)) LIMIT 1")
    suspend fun profileByTag(tagId: String): BrickProfileEntity?

    @Query("DELETE FROM brick_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    // ---- limits ----------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimit(limit: BrickAppLimitEntity)

    @Query("SELECT * FROM brick_app_limits WHERE profileId = :profileId")
    suspend fun limitsFor(profileId: Long): List<BrickAppLimitEntity>

    @Query("SELECT * FROM brick_app_limits WHERE profileId = :profileId")
    fun observeLimits(profileId: Long): Flow<List<BrickAppLimitEntity>>

    @Query("DELETE FROM brick_app_limits WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun deleteLimit(profileId: Long, packageName: String)

    // ---- sessions --------------------------------------------------------
    @Insert
    suspend fun insertSession(session: BrickSessionEntity): Long

    @Update
    suspend fun updateSession(session: BrickSessionEntity)

    @Query("SELECT * FROM brick_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeSession(): BrickSessionEntity?

    @Query("SELECT * FROM brick_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<BrickSessionEntity?>

    @Query("SELECT * FROM brick_sessions ORDER BY startedAt DESC LIMIT 50")
    fun observeRecentSessions(): Flow<List<BrickSessionEntity>>

    @Query("UPDATE brick_sessions SET blockedAttempts = blockedAttempts + 1 WHERE id = :sessionId")
    suspend fun incrementAttempts(sessionId: Long)

    @Query("UPDATE brick_sessions SET endedAt = :endedAt WHERE endedAt IS NULL")
    suspend fun endAllSessions(endedAt: Long)

    // ---- usage -----------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(usage: BrickUsageEntity)

    @Query("SELECT * FROM brick_usage WHERE date = :date AND packageName = :packageName")
    suspend fun usage(date: String, packageName: String): BrickUsageEntity?

    @Query("SELECT * FROM brick_usage WHERE date = :date")
    fun observeUsage(date: String): Flow<List<BrickUsageEntity>>
}
