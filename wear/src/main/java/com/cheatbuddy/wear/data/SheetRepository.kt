package com.cheatbuddy.wear.data

import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.wear.data.db.ImageDao
import com.cheatbuddy.wear.data.db.SheetDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Read-only access to synced content. Everything here comes from the local database and files and never
 * touches the Data Layer, so it works with the phone switched off.
 */
class SheetRepository(
    private val sheetDao: SheetDao,
    private val imageDao: ImageDao,
    private val imageStore: ImageStore,
    private val preferences: WatchPreferences,
) {
    fun observeSummaries(): Flow<List<SheetSummary>> =
        sheetDao.observeAll().map { list -> list.map { it.toSummary() } }

    fun observeSheet(id: String): Flow<Sheet?> =
        sheetDao.observe(id).distinctUntilChanged().map { it?.toSheet() }

    suspend fun getSheet(id: String): Sheet? = sheetDao.get(id)?.toSheet()

    /** Ids of images that are stored locally and can be displayed. */
    fun observeAvailableImages(): Flow<Set<String>> = imageDao.observeIds().map { it.toSet() }

    fun imageFile(imageId: String): File = imageStore.file(imageId)

    val lastViewedSheetId: Flow<String?> get() = preferences.lastViewedSheetId

    suspend fun markViewed(id: String) = preferences.setLastViewedSheet(id)

    fun observeSheetCount(): Flow<Int> = sheetDao.observeCount()

    fun observeImageBytes(): Flow<Long> = imageDao.observeTotalBytes()
}
