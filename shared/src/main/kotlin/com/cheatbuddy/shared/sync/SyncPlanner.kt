package com.cheatbuddy.shared.sync

import com.cheatbuddy.shared.model.Sheet

/** What the watch currently has stored for one sheet. */
data class LocalSheetInfo(
    val updatedAt: Long,
    val imageIds: Collection<String>,
)

/** Result of comparing the phone's state (manifest + sheet items) with the watch's local database. */
data class SyncPlan(
    /** Sheets to insert or replace locally. */
    val upserts: List<Sheet>,
    /** Local sheet ids that no longer exist on the phone. */
    val deletes: Set<String>,
    /** Display order (sheet ids) after applying the plan. */
    val order: List<String>,
    /** Sheets listed in the manifest whose DataItem has not arrived yet. */
    val pendingSheetIds: Set<String>,
    /** Images referenced after applying the plan but not stored locally. */
    val missingImageIds: Set<String>,
    /** Locally stored images no longer referenced by any sheet. */
    val orphanImageIds: Set<String>,
) {
    val isUpToDate: Boolean
        get() = upserts.isEmpty() && deletes.isEmpty() && pendingSheetIds.isEmpty() && missingImageIds.isEmpty()
}

/** What the phone has to write to / remove from the Data Layer. */
data class PushPlan(
    val manifest: SyncManifest,
    val upserts: List<Sheet>,
    val deletes: Set<String>,
)

/** Pure, side-effect free sync decisions, shared by both apps and unit tested in isolation. */
object SyncPlanner {

    /**
     * Watch side: plans how to bring the local database in line with the phone.
     *
     * @param manifest the phone's latest manifest.
     * @param remoteSheets sheets decoded from the Data Layer, by id. May lag behind or run ahead of the manifest.
     * @param local sheets currently stored on the watch, by id.
     * @param localImageIds image files currently stored on the watch.
     */
    fun planPull(
        manifest: SyncManifest,
        remoteSheets: Map<String, Sheet>,
        local: Map<String, LocalSheetInfo>,
        localImageIds: Set<String>,
    ): SyncPlan {
        val manifestIds = manifest.entries.map { it.id }
        val manifestIdSet = manifestIds.toSet()

        val upserts = mutableListOf<Sheet>()
        val pending = mutableSetOf<String>()
        val referencedImages = mutableSetOf<String>()

        for (entry in manifest.entries) {
            val remote = remoteSheets[entry.id]
            val stored = local[entry.id]
            when {
                remote != null && (stored == null || stored.updatedAt != remote.updatedAt) -> {
                    upserts += remote
                    referencedImages += remote.imageIds
                    // The item can lag behind the manifest. Accept it, but stay pending until the exact version lands.
                    if (remote.updatedAt != entry.updatedAt) pending += entry.id
                }
                stored != null -> {
                    referencedImages += stored.imageIds
                    if (stored.updatedAt != entry.updatedAt) pending += entry.id
                }
                else -> pending += entry.id
            }
        }

        val deletes = local.keys.filterNot { it in manifestIdSet }.toSet()
        val upsertIds = upserts.mapTo(mutableSetOf()) { it.id }
        val order = manifestIds.filter { it in local || it in upsertIds }

        return SyncPlan(
            upserts = upserts,
            deletes = deletes,
            order = order,
            pendingSheetIds = pending,
            missingImageIds = referencedImages - localImageIds,
            orphanImageIds = localImageIds - referencedImages,
        )
    }

    /**
     * Phone side: plans which DataItems to write.
     *
     * @param sheets all sheets on the phone, in display order.
     * @param published versions currently present in the Data Layer (sheet id → updatedAt).
     */
    fun planPush(sheets: List<Sheet>, published: Map<String, Long>): PushPlan {
        val ids = sheets.map { it.id }.toSet()
        return PushPlan(
            manifest = SyncManifest.of(sheets),
            upserts = sheets.filter { published[it.id] != it.updatedAt },
            deletes = published.keys.filterNot { it in ids }.toSet(),
        )
    }
}
