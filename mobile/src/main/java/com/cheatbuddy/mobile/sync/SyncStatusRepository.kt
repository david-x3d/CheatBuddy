package com.cheatbuddy.mobile.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

sealed interface WatchSyncStatus {
    /** No watch has ever confirmed a sync and none is connected. */
    data object NoWatch : WatchSyncStatus
    data object Syncing : WatchSyncStatus
    data class Synced(val at: Long) : WatchSyncStatus
    /** Changes are queued and will arrive when the watch reconnects. */
    data object WaitingForWatch : WatchSyncStatus
    data class Failed(val message: String) : WatchSyncStatus
}

class SyncStatusRepository(
    private val preferences: SyncPreferences,
    private val scheduler: PhoneSyncScheduler,
    private val watchReachable: Flow<Boolean>,
) {
    val status: Flow<WatchSyncStatus> = combine(
        preferences.record,
        scheduler.isRunning,
        watchReachable,
    ) { record, running, reachable -> derive(record, running, reachable) }.distinctUntilChanged()

    fun retry() = scheduler.pushNow()

    companion object {
        fun derive(record: SyncRecord, running: Boolean, reachable: Boolean): WatchSyncStatus = when {
            running -> WatchSyncStatus.Syncing
            record.error != null -> WatchSyncStatus.Failed(record.error)
            record.pushedRevision != null && record.pushedRevision == record.ackedRevision ->
                WatchSyncStatus.Synced(record.ackedAt ?: record.pushedAt ?: 0)
            !reachable && record.ackedRevision == null -> WatchSyncStatus.NoWatch
            !reachable -> WatchSyncStatus.WaitingForWatch
            else -> WatchSyncStatus.Syncing
        }
    }
}
