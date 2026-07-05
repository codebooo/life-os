package com.lifeos.core.database.dhl

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A tracked shipment (§Module 15). */
@Entity(
    tableName = "packages",
    indices = [Index(value = ["trackingNumber"], unique = true)],
)
data class PackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackingNumber: String,
    val label: String?,
    val status: String,
    val statusDescription: String?,
    val estimatedDeliveryAt: Long?,
    val reminderId: Long?,
    val lastRefreshedAt: Long?,
    val sourceModule: String?,
    val sourceEntityId: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "tracking_events",
    foreignKeys = [
        ForeignKey(
            entity = PackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["packageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packageId")],
)
data class TrackingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageId: Long,
    val status: String,
    val description: String?,
    val location: String?,
    val at: Long,
)

@Dao
interface PackageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pkg: PackageEntity): Long

    @Update
    suspend fun update(pkg: PackageEntity)

    @Query("SELECT * FROM packages WHERE id = :id")
    suspend fun getById(id: Long): PackageEntity?

    @Query("SELECT * FROM packages WHERE trackingNumber = :trackingNumber")
    suspend fun getByTrackingNumber(trackingNumber: String): PackageEntity?

    @Query("SELECT * FROM packages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE status NOT IN ('DELIVERED', 'ARCHIVED')")
    suspend fun activePackages(): List<PackageEntity>

    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertEvents(events: List<TrackingEventEntity>)

    @Query("DELETE FROM tracking_events WHERE packageId = :packageId")
    suspend fun deleteEventsOf(packageId: Long)

    @Query("SELECT * FROM tracking_events WHERE packageId = :packageId ORDER BY at DESC")
    fun observeEvents(packageId: Long): Flow<List<TrackingEventEntity>>
}
