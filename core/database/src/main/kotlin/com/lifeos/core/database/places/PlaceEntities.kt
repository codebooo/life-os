package com.lifeos.core.database.places

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A place LifeOS recognises (§Module Places).
 *
 * Without Play Services there is no system geofence, so a place is matched by
 * whichever signals it has: coordinates with a radius, the Wi-Fi network it
 * belongs to, or both. Wi-Fi alone is the cheapest and the most reliable indoors.
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Match radius in metres; ignored when there are no coordinates. */
    val radiusMeters: Int = 150,
    /** Wi-Fi network name that means "here"; matched case-insensitively. */
    val wifiSsid: String? = null,
    val createdAt: Long,
)

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY name")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY name")
    suspend fun all(): List<PlaceEntity>

    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Update
    suspend fun update(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)
}
