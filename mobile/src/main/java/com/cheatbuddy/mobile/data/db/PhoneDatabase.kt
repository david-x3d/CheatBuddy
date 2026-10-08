package com.cheatbuddy.mobile.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A sheet on the phone (source of truth). Blocks are stored as JSON produced by the shared codec. */
@Entity(tableName = "sheets")
data class SheetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val blocksJson: String,
)

@Dao
interface SheetDao {
    @Query("SELECT * FROM sheets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SheetEntity>>

    @Query("SELECT * FROM sheets ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SheetEntity>

    @Query("SELECT * FROM sheets WHERE id = :id")
    suspend fun get(id: String): SheetEntity?

    @Upsert
    suspend fun upsert(sheet: SheetEntity)

    @Query("DELETE FROM sheets WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [SheetEntity::class], version = 1, exportSchema = true)
abstract class PhoneDatabase : RoomDatabase() {
    abstract fun sheetDao(): SheetDao

    companion object {
        const val NAME = "cheatbuddy.db"
    }
}
