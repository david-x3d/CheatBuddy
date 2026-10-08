package com.cheatbuddy.mobile.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SyncRecord(
    val pushedRevision: String? = null,
    val pushedAt: Long? = null,
    val ackedRevision: String? = null,
    val ackedAt: Long? = null,
    val error: String? = null,
    val errorAt: Long? = null,
)

/** Persists what was pushed and what the watch confirmed, for the phone's sync status chip. */
class SyncPreferences(private val store: DataStore<Preferences>) {

    val record: Flow<SyncRecord> = store.data.map { p ->
        SyncRecord(p[PUSHED], p[PUSHED_AT], p[ACKED], p[ACKED_AT], p[ERROR], p[ERROR_AT])
    }

    suspend fun recordPush(revision: String, at: Long) = store.edit {
        it[PUSHED] = revision
        it[PUSHED_AT] = at
        it.remove(ERROR)
        it.remove(ERROR_AT)
    }

    suspend fun recordAck(revision: String, at: Long) = store.edit {
        it[ACKED] = revision
        it[ACKED_AT] = at
    }

    suspend fun recordError(message: String, at: Long) = store.edit {
        it[ERROR] = message
        it[ERROR_AT] = at
    }

    private companion object {
        val PUSHED = stringPreferencesKey("pushed_revision")
        val PUSHED_AT = longPreferencesKey("pushed_at")
        val ACKED = stringPreferencesKey("acked_revision")
        val ACKED_AT = longPreferencesKey("acked_at")
        val ERROR = stringPreferencesKey("error")
        val ERROR_AT = longPreferencesKey("error_at")
    }
}
