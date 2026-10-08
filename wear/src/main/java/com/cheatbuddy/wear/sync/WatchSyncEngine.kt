package com.cheatbuddy.wear.sync

import com.cheatbuddy.shared.sync.LocalSheetInfo
import com.cheatbuddy.shared.sync.SyncPlanner
import com.cheatbuddy.wear.data.ImageStore
import com.cheatbuddy.wear.data.StorageFullException
import com.cheatbuddy.wear.data.WatchPreferences
import com.cheatbuddy.wear.data.db.ImageDao
import com.cheatbuddy.wear.data.db.ImageEntity
import com.cheatbuddy.wear.data.db.SheetDao
import com.cheatbuddy.wear.data.decodeBlocks
import com.cheatbuddy.wear.data.isOutOfSpace
import com.cheatbuddy.wear.data.toEntity
import com.cheatbuddy.shared.model.ImageBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

sealed interface SyncOutcome {
    data object Success : SyncOutcome
    /** Local content is updated, but images or sheets still need the phone. Retried when it reconnects. */
    data class WaitingForPhone(val pending: Int) : SyncOutcome
    data object StorageFull : SyncOutcome
    data class Failed(val error: Throwable) : SyncOutcome
}

/**
 * Mirrors the phone's content into the local database and image store.
 *
 * 1. Read the Data Layer snapshot (cached on the watch, works offline).
 * 2. Plan with the shared [SyncPlanner] and apply sheet changes in one transaction.
 * 3. Pull missing images from the phone over a channel, if it is reachable.
 * 4. Delete orphaned images, acknowledge the revision, and record the status for the settings screen.
 */
class WatchSyncEngine(
    private val gateway: WearableGateway,
    private val sheetDao: SheetDao,
    private val imageDao: ImageDao,
    private val imageStore: ImageStore,
    private val preferences: WatchPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun sync(): SyncOutcome {
        preferences.updateSyncStatus { it.copy(state = SyncState.Syncing, message = null) }
        val outcome = try {
            runSync()
        } catch (e: CancellationException) {
            preferences.updateSyncStatus { it.copy(state = if (it.lastSuccessAt == null) SyncState.NeverSynced else SyncState.WaitingForPhone) }
            throw e
        } catch (e: Exception) {
            if (e.isOutOfSpace()) SyncOutcome.StorageFull else SyncOutcome.Failed(e)
        }
        record(outcome)
        return outcome
    }

    private suspend fun runSync(): SyncOutcome {
        val snapshot = gateway.readSnapshot()
        val manifest = snapshot.manifest
            ?: return if (findPhoneOrNull() == null) SyncOutcome.WaitingForPhone(0) else SyncOutcome.Success

        val localSheets = sheetDao.getAll()
        val localInfo = localSheets.associate { entity ->
            entity.id to LocalSheetInfo(
                updatedAt = entity.updatedAt,
                imageIds = entity.decodeBlocks().filterIsInstance<ImageBlock>().map { it.imageId },
            )
        }
        // Only count images whose file really exists, so a lost file gets downloaded again.
        val localImages = imageDao.ids().filterTo(mutableSetOf()) { imageStore.exists(it) }

        val plan = SyncPlanner.planPull(manifest, snapshot.sheets, localInfo, localImages)
        val positions = plan.order.withIndex().associate { (index, id) -> id to index }
        sheetDao.apply(
            upserts = plan.upserts.map { it.toEntity(positions[it.id] ?: Int.MAX_VALUE) },
            deletes = plan.deletes,
            order = plan.order,
        )

        // Free space before downloading new images.
        if (plan.orphanImageIds.isNotEmpty()) {
            plan.orphanImageIds.forEach(imageStore::delete)
            imageDao.delete(plan.orphanImageIds)
        }

        val phone = findPhoneOrNull()
        var stillMissing = plan.missingImageIds.size
        if (phone != null && plan.missingImageIds.isNotEmpty()) {
            for (imageId in plan.missingImageIds) {
                try {
                    val stored = withTimeout(IMAGE_TIMEOUT_MS) {
                        gateway.receiveImage(phone, imageId) { input -> imageStore.save(imageId, input) }
                    }
                    imageDao.insert(ImageEntity(imageId, stored.width, stored.height, stored.sizeBytes, clock()))
                    stillMissing--
                } catch (e: StorageFullException) {
                    throw e
                } catch (_: TimeoutCancellationException) {
                    // A stalled image stays pending; other images and local content remain usable.
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.isOutOfSpace()) throw StorageFullException()
                    // Keep going; this image is retried on the next sync.
                }
            }
        }

        val pending = plan.pendingSheetIds.size + stillMissing
        if (pending == 0 && phone != null) {
            runCatching { gateway.sendAck(phone, manifest.revision) }
        }
        return when {
            pending == 0 -> SyncOutcome.Success
            else -> SyncOutcome.WaitingForPhone(pending)
        }
    }

    private suspend fun findPhoneOrNull(): String? = try {
        gateway.findReachablePhone()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun record(outcome: SyncOutcome) {
        val now = clock()
        preferences.updateSyncStatus { current ->
            when (outcome) {
                SyncOutcome.Success -> current.copy(
                    state = SyncState.UpToDate, lastSuccessAt = now, message = null, pendingItems = 0,
                )
                is SyncOutcome.WaitingForPhone -> current.copy(
                    state = if (current.lastSuccessAt == null && outcome.pending == 0) {
                        SyncState.NeverSynced
                    } else {
                        SyncState.WaitingForPhone
                    },
                    message = null,
                    pendingItems = outcome.pending,
                )
                SyncOutcome.StorageFull -> current.copy(
                    state = SyncState.StorageFull, message = "Watch storage is full. Free up space to finish syncing.",
                )
                is SyncOutcome.Failed -> current.copy(
                    state = SyncState.Failed, message = outcome.error.message ?: outcome.error.javaClass.simpleName,
                )
            }
        }
    }

    companion object {
        const val IMAGE_TIMEOUT_MS = 30_000L
    }
}
