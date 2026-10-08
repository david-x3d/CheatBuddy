package com.cheatbuddy.mobile.sync

import android.content.Context
import androidx.core.net.toUri
import com.cheatbuddy.shared.sync.SyncProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Abstraction over the Data Layer so [PhoneSyncEngine] can be unit tested. */
interface PhoneGateway {
    /** Sheet versions currently published as DataItems (sheet id → updatedAt). */
    suspend fun publishedVersions(): Map<String, Long>
    suspend fun putSheet(id: String, updatedAt: Long, json: String)
    suspend fun deleteSheet(id: String)
    suspend fun putManifest(json: String)
    suspend fun isWatchReachable(): Boolean
}

class PlayServicesPhoneGateway(private val context: Context) : PhoneGateway {
    private val dataClient = Wearable.getDataClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    override suspend fun publishedVersions(): Map<String, Long> {
        val buffer = dataClient.getDataItems("wear://*${SyncProtocol.SHEET_PREFIX}".toUri(), DataClient.FILTER_PREFIX).await()
        return try {
            buffer.mapNotNull { item ->
                val id = SyncProtocol.sheetIdFromPath(item.uri.path.orEmpty()) ?: return@mapNotNull null
                id to DataMapItem.fromDataItem(item).dataMap.getLong(SyncProtocol.KEY_UPDATED_AT)
            }.toMap()
        } finally {
            buffer.release()
        }
    }

    override suspend fun putSheet(id: String, updatedAt: Long, json: String) {
        val request = PutDataMapRequest.create(SyncProtocol.sheetPath(id)).apply {
            dataMap.putString(SyncProtocol.KEY_JSON, json)
            dataMap.putLong(SyncProtocol.KEY_UPDATED_AT, updatedAt)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override suspend fun deleteSheet(id: String) {
        dataClient.deleteDataItems("wear://*${SyncProtocol.sheetPath(id)}".toUri()).await()
    }

    override suspend fun putManifest(json: String) {
        val request = PutDataMapRequest.create(SyncProtocol.MANIFEST_PATH).apply {
            dataMap.putString(SyncProtocol.KEY_JSON, json)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override suspend fun isWatchReachable(): Boolean =
        capabilityClient.getCapability(SyncProtocol.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.isNotEmpty()

    /** Emits whether a watch with Cheat Buddy is currently connected. */
    fun observeWatchReachable(): Flow<Boolean> = callbackFlow {
        val listener = CapabilityClient.OnCapabilityChangedListener { info -> trySend(info.nodes.isNotEmpty()) }
        capabilityClient.addListener(listener, SyncProtocol.CAPABILITY_WATCH)
        trySend(runCatching { isWatchReachable() }.getOrDefault(false))
        awaitClose { capabilityClient.removeListener(listener, SyncProtocol.CAPABILITY_WATCH) }
    }
}
