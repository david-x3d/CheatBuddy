package com.cheatbuddy.wear.sync

enum class SyncState {
    /** Nothing has ever been received from the phone. */
    NeverSynced,
    Syncing,
    UpToDate,
    /** Content is usable, but some items (usually images) wait for the phone to be reachable. */
    WaitingForPhone,
    Failed,
    StorageFull,
}

data class SyncStatus(
    val state: SyncState = SyncState.NeverSynced,
    val lastSuccessAt: Long? = null,
    val message: String? = null,
    val pendingItems: Int = 0,
)
