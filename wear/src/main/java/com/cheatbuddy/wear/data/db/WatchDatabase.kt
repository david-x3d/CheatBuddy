package com.cheatbuddy.wear.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A sheet as stored on the watch. Blocks are kept as JSON produced by the shared codec. */
@Entity(tableName = "sheets")
data class SheetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long,
    val position: Int,
    val blocksJson: String,
    /** Precomputed for the home list so it never has to decode blocks. */
    val blockCount: Int,
    val firstImageId: String?,
    val previewText: String?,
)

/** An image file received from the phone and stored under `filesDir/images`. */
@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val receivedAt: Long,
)

@Dao
interface SheetDao {
    @Query("SELECT * FROM sheets ORDER BY position ASC, title ASC")
    fun observeAll(): Flow<List<SheetEntity>>

    @Query("SELECT * FROM sheets WHERE id = :id")
    fun observe(id: String): Flow<SheetEntity?>

    @Query("SELECT * FROM sheets WHERE id = :id")
    suspend fun get(id: String): SheetEntity?

    @Query("SELECT * FROM sheets")
    suspend fun getAll(): List<SheetEntity>

    @Query("SELECT COUNT(*) FROM sheets")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(sheets: List<SheetEntity>)

    @Query("DELETE FROM sheets WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<String>)

    @Query("UPDATE sheets SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    /** Applies a sync result atomically, so the UI never sees a half-applied state. */
    @Transaction
    suspend fun apply(upserts: List<SheetEntity>, deletes: Collection<String>, order: List<String>) {
        if (deletes.isNotEmpty()) delete(deletes)
        if (upserts.isNotEmpty()) upsert(upserts)
        order.forEachIndexed { index, id -> updatePosition(id, index) }
    }
}

@Dao
interface ImageDao {
    @Query("SELECT id FROM images")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT id FROM images")
    suspend fun ids(): List<String>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM images")
    fun observeTotalBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ImageEntity)

    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<String>)
}

@Database(entities = [SheetEntity::class, ImageEntity::class], version = 1, exportSchema = true)
abstract class WatchDatabase : RoomDatabase() {
    abstract fun sheetDao(): SheetDao
    abstract fun imageDao(): ImageDao

    companion object {
        const val NAME = "cheatbuddy-watch.db"
    }
}
