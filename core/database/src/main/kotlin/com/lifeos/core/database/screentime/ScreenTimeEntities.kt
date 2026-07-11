package com.lifeos.core.database.screentime

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One day of digital-wellbeing data (§Module Screen Time). Persisted forever so
 * it survives Samsung purging its own stats after ~a month. [date] is
 * yyyy-MM-dd in the device's local zone.
 */
@Entity(tableName = "screen_time_days")
data class ScreenTimeDayEntity(
    @androidx.room.PrimaryKey val date: String,
    val totalForegroundMs: Long,
    val unlocks: Int,
    val notifications: Int,
    val capturedAt: Long,
)

/** Per-app foreground time for one day. */
@Entity(tableName = "screen_time_apps", primaryKeys = ["date", "packageName"])
data class AppUsageEntity(
    val date: String,
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
)

@Dao
interface ScreenTimeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: ScreenTimeDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<AppUsageEntity>)

    @Query("SELECT * FROM screen_time_days ORDER BY date DESC")
    fun observeDays(): Flow<List<ScreenTimeDayEntity>>

    @Query("SELECT * FROM screen_time_days ORDER BY date DESC")
    suspend fun allDays(): List<ScreenTimeDayEntity>

    @Query("SELECT * FROM screen_time_apps WHERE date BETWEEN :from AND :to ORDER BY foregroundMs DESC")
    suspend fun appsBetween(from: String, to: String): List<AppUsageEntity>

    @Query("SELECT * FROM screen_time_apps")
    suspend fun allApps(): List<AppUsageEntity>

    @Query("SELECT date FROM screen_time_days")
    suspend fun capturedDates(): List<String>
}
