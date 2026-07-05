package com.lifeos.core.database.route

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A saved destination (§Module 17). */
@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Free-form query handed to the navigation app. */
    val query: String,
    val createdAt: Long,
)

@Dao
interface RouteDao {

    @Insert
    suspend fun insert(place: SavedPlaceEntity): Long

    @Query("SELECT * FROM saved_places ORDER BY name")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)
}
