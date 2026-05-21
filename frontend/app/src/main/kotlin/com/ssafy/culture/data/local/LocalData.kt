package com.ssafy.culture.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "culture_items")
data class CultureEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val imageUrl: String,
    val thumbnailUrl: String,
)

@Dao
interface CultureDao {
    @Query("SELECT * FROM culture_items ORDER BY id")
    fun observeItems(): Flow<List<CultureEntity>>

    @Query("SELECT * FROM culture_items WHERE id = :id")
    fun observeItem(id: Int): Flow<CultureEntity?>

    @Upsert
    suspend fun upsertAll(items: List<CultureEntity>)
}

@Database(
    entities = [CultureEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cultureDao(): CultureDao

    companion object {
        const val NAME = "culture.db"
    }
}

