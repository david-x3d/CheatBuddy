package com.cheatbuddy.mobile.data

import com.cheatbuddy.mobile.data.db.SheetDao
import com.cheatbuddy.mobile.data.db.SheetEntity
import com.cheatbuddy.mobile.sync.PhoneSyncScheduler
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.sync.SheetCodec
import com.cheatbuddy.shared.sync.SyncPayloadException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Phone-side sheet storage. Every write schedules a push to the watch. */
class SheetRepository(
    private val dao: SheetDao,
    private val scheduler: PhoneSyncScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSheets(): Flow<List<Sheet>> = dao.observeAll().map { list -> list.map { it.toSheet() } }

    suspend fun allSheets(): List<Sheet> = dao.getAll().map { it.toSheet() }

    suspend fun get(id: String): Sheet? = dao.get(id)?.toSheet()

    suspend fun create(title: String = ""): Sheet {
        val now = clock()
        val sheet = Sheet(
            id = UUID.randomUUID().toString(),
            title = title,
            updatedAt = now,
            blocks = listOf(TextBlock(UUID.randomUUID().toString(), "")),
        )
        dao.upsert(SheetEntity(sheet.id, sheet.title, now, now, SheetCodec.encodeBlocks(sheet.blocks)))
        scheduler.pushSoon()
        return sheet
    }

    /** Saves [sheet] with a fresh version and returns the stored copy. */
    suspend fun save(sheet: Sheet): Sheet {
        val existing = dao.get(sheet.id)
        // Strictly increasing versions even if the clock jumps back.
        val now = maxOf(clock(), (existing?.updatedAt ?: 0) + 1)
        val stored = sheet.copy(updatedAt = now)
        dao.upsert(
            SheetEntity(
                id = stored.id,
                title = stored.title,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                blocksJson = SheetCodec.encodeBlocks(stored.blocks),
            ),
        )
        scheduler.pushSoon()
        return stored
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        scheduler.pushSoon()
    }

    private fun SheetEntity.toSheet() = Sheet(
        id = id,
        title = title,
        updatedAt = updatedAt,
        blocks = try {
            SheetCodec.decodeBlocks(blocksJson)
        } catch (_: SyncPayloadException) {
            emptyList()
        },
    )
}
