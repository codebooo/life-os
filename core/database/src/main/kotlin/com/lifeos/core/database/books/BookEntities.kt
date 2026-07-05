package com.lifeos.core.database.books

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A book on a shelf (§Module 16, [src 46]). Rating in half-stars 0..10. */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val isbn: String?,
    /** WANT, READING, READ */
    val status: String,
    val ratingHalfStars: Int?,
    val notes: String?,
    val addedAt: Long,
)

@Dao
interface BookDao {

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE status = 'READ' AND ratingHalfStars >= 7")
    suspend fun favourites(): List<BookEntity>

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE books SET ratingHalfStars = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}
