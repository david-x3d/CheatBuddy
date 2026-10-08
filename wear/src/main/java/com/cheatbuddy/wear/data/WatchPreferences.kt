package com.cheatbuddy.wear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cheatbuddy.wear.sync.SyncState
import com.cheatbuddy.wear.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Small key-value state: last viewed sheet (for the tile) and the latest sync status (for settings). */
class WatchPreferences(private val store: DataStore<Preferences>) {

    val lastViewedSheetId: Flow<String?> = store.data.map { it[LAST_VIEWED] }

    suspend fun setLastViewedSheet(id: String) {
        store.edit { it[LAST_VIEWED] = id }
    }

    suspend fun lastViewedSheetIdNow(): String? = lastViewedSheetId.first()

    val syncStatus: Flow<SyncStatus> = store.data.map { it.toSyncStatus() }

    suspend fun updateSyncStatus(transform: (SyncStatus) -> SyncStatus) {
        store.edit { prefs ->
            val next = transform(prefs.toSyncStatus())
            prefs[SYNC_STATE] = next.state.name
            if (next.lastSuccessAt != null) prefs[LAST_SUCCESS] = next.lastSuccessAt else prefs.remove(LAST_SUCCESS)
            if (next.message != null) prefs[SYNC_MESSAGE] = next.message else prefs.remove(SYNC_MESSAGE)
            prefs[PENDING] = next.pendingItems
        }
    }

    private fun Preferences.toSyncStatus() = SyncStatus(
        state = this[SYNC_STATE]?.let { name -> SyncState.entries.firstOrNull { it.name == name } }
            ?: SyncState.NeverSynced,
        lastSuccessAt = this[LAST_SUCCESS],
        message = this[SYNC_MESSAGE],
        pendingItems = this[PENDING] ?: 0,
    )

    private companion object {
        val LAST_VIEWED = stringPreferencesKey("last_viewed_sheet")
        val SYNC_STATE = stringPreferencesKey("sync_state")
        val LAST_SUCCESS = longPreferencesKey("sync_last_success")
        val SYNC_MESSAGE = stringPreferencesKey("sync_message")
        val PENDING = intPreferencesKey("sync_pending")
    }
}
