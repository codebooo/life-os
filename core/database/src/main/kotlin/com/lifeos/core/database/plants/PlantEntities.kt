package com.lifeos.core.database.plants

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A plant the user owns (§Module Plants). Species care data lives in the
 * feature's bundled atlas; this row binds a named plant to a species and its
 * recurring watering reminder.
 */
@Entity(tableName = "my_plants")
data class MyPlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Key into the bundled species atlas. */
    val speciesId: String,
    /** Days between waterings (species default, user-overridable). */
    val waterEveryDays: Int,
    val lastWateredAt: Long?,
    /** Reminder row backing the watering notification, if created. */
    val reminderId: Long?,
    val createdAt: Long,
    /** Absolute path to a user-taken photo (copied into app storage). */
    val photoPath: String? = null,
    /** Free-text personal notes for this plant. */
    val notes: String? = null,
)

@Dao
interface PlantDao {

    @Insert
    suspend fun insert(plant: MyPlantEntity): Long

    @androidx.room.Update
    suspend fun update(plant: MyPlantEntity)

    @Query("SELECT * FROM my_plants ORDER BY name")
    fun observeAll(): Flow<List<MyPlantEntity>>

    @Query("UPDATE my_plants SET lastWateredAt = :at WHERE id = :id")
    suspend fun setWatered(id: Long, at: Long)

    @Query("SELECT * FROM my_plants WHERE id = :id")
    suspend fun getById(id: Long): MyPlantEntity?

    @Query("DELETE FROM my_plants WHERE id = :id")
    suspend fun delete(id: Long)
}
