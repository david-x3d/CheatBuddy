package com.cheatbuddy.wear.sync

import android.content.Context
import androidx.core.net.toUri
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.sync.SheetCodec
import com.cheatbuddy.shared.sync.SyncManifest
import com.cheatbuddy.shared.sync.SyncPayloadException
import com.cheatbuddy.shared.sync.SyncProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

/** What the phone has published to the Data Layer (as cached locally on the watch). */
data class RemoteSnapshot(
    val manifest: SyncManifest?,
    val sheets: Map<String, Sheet>,
    /** Items that could not be decoded (e.g. from a newer app version). */
    val unreadableItems: Int = 0,
)

/** Abstraction over the Wearable Data Layer so the sync engine can be tested without Play services. */
interface WearableGateway {
    /** Reads the locally cached DataItems. Works offline; returns the last state received from the phone. */
    suspend fun readSnapshot(): RemoteSnapshot

    /** Node id of a reachable phone running Cheat Buddy, or null if none is connected. */
    suspend fun findReachablePhone(): String?

    /** Opens an image channel to [nodeId] and hands the stream to [consume]. */
    suspend fun <T> receiveImage(nodeId: String, imageId: String, consume: (InputStream) -> T): T

    suspend fun sendAck(nodeId: String, revision: String)
}

class PlayServicesWearableGateway(context: Context) : WearableGateway {
    private val dataClient = Wearable.getDataClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val channelClient = Wearable.getChannelClient(context)
    private val messageClient = Wearable.getMessageClient(context)

    override suspend fun readSnapshot(): RemoteSnapshot {
        val buffer = dataClient.getDataItems("wear://*${SyncProtocol.ROOT}".toUri(), DataClient.FILTER_PREFIX).await()
        var manifest: SyncManifest? = null
        val sheets = mutableMapOf<String, Sheet>()
        var unreadable = 0
        try {
            for (item in buffer) {
                val path = item.uri.path ?: continue
                val json = DataMapItem.fromDataItem(item).dataMap.getString(SyncProtocol.KEY_JSON) ?: continue
                try {
                    when {
                        path == SyncProtocol.MANIFEST_PATH -> {
                            val candidate = SheetCodec.decodeManifest(json)
                            // With several paired phones, keep the newest manifest.
                            if (manifest == null || candidate.newestUpdate() > manifest.newestUpdate()) {
                                manifest = candidate
                            }
                        }
                        SyncProtocol.sheetIdFromPath(path) != null -> {
                            val sheet = SheetCodec.decodeSheet(json)
                            val existing = sheets[sheet.id]
                            if (existing == null || existing.updatedAt < sheet.updatedAt) sheets[sheet.id] = sheet
                        }
                    }
                } catch (_: SyncPayloadException) {
                    unreadable++
                }
            }
        } finally {
            buffer.release()
        }
        return RemoteSnapshot(manifest, sheets, unreadable)
    }

    override suspend fun findReachablePhone(): String? {
        val nodes = capabilityClient
            .getCapability(SyncProtocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
        return (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
    }

    override suspend fun <T> receiveImage(nodeId: String, imageId: String, consume: (InputStream) -> T): T {
        val channel = channelClient.openChannel(nodeId, SyncProtocol.imagePath(imageId)).await()
        try {
            val input = channelClient.getInputStream(channel).await()
            return withContext(Dispatchers.IO) { input.use(consume) }
        } finally {
            runCatching { channelClient.close(channel).await() }
        }
    }

    override suspend fun sendAck(nodeId: String, revision: String) {
        messageClient.sendMessage(nodeId, SyncProtocol.ACK_PATH, revision.toByteArray()).await()
    }

    private fun SyncManifest.newestUpdate(): Long = entries.maxOfOrNull { it.updatedAt } ?: 0L
}
