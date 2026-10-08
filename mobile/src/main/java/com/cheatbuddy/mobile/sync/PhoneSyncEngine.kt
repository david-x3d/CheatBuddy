package com.cheatbuddy.mobile.sync

import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.sync.SheetCodec
import com.cheatbuddy.shared.sync.SyncPlanner
import kotlinx.coroutines.CancellationException

sealed interface PushResult {
    /** Items are in the Data Layer; it delivers them whenever the watch connects. */
    data class Published(val revision: String, val written: Int, val deleted: Int) : PushResult
    data class Failed(val message: String, val retryable: Boolean) : PushResult
}

/**
 * Publishes the phone's sheets to the Data Layer:
 * changed sheets as DataItems, removed sheets deleted, and a manifest listing everything in order.
 */
class PhoneSyncEngine(
    private val loadSheets: suspend () -> List<Sheet>,
    private val gateway: PhoneGateway,
    private val preferences: SyncPreferences,
    private val onPublished: suspend (List<Sheet>) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun push(): PushResult {
        val result = try {
            val sheets = loadSheets()
            val plan = SyncPlanner.planPush(sheets, gateway.publishedVersions())

            val tooLarge = plan.upserts.firstOrNull { SheetCodec.encodeSheet(it).encodeToByteArray().size > MAX_ITEM_BYTES }
            if (tooLarge != null) {
                PushResult.Failed("“${tooLarge.title.ifBlank { "Untitled" }}” is too large to sync. Split it into smaller sheets.", retryable = false)
            } else {
                plan.upserts.forEach { gateway.putSheet(it.id, it.updatedAt, SheetCodec.encodeSheet(it)) }
                plan.deletes.forEach { gateway.deleteSheet(it) }
                gateway.putManifest(SheetCodec.encodeManifest(plan.manifest))
                onPublished(sheets)
                PushResult.Published(plan.manifest.revision, plan.upserts.size, plan.deletes.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PushResult.Failed(e.message ?: "Could not reach the Wear OS Data Layer", retryable = true)
        }

        when (result) {
            is PushResult.Published -> preferences.recordPush(result.revision, clock())
            is PushResult.Failed -> preferences.recordError(result.message, clock())
        }
        return result
    }

    companion object {
        /** DataItems are limited to 100 KB; keep a margin for the DataMap wrapper. */
        const val MAX_ITEM_BYTES = 90 * 1024
    }
}
