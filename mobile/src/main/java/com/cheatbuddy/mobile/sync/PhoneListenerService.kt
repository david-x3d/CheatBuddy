package com.cheatbuddy.mobile.sync

import com.cheatbuddy.mobile.data.ImageRepository
import com.cheatbuddy.shared.sync.SyncProtocol
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Serves image requests from the watch, records sync acknowledgements and re-publishes when a watch appears.
 * Callbacks run on a background thread, so blocking I/O here is fine and keeps the service alive.
 */
@AndroidEntryPoint
class PhoneListenerService : WearableListenerService() {

    @Inject lateinit var images: ImageRepository
    @Inject lateinit var preferences: SyncPreferences
    @Inject lateinit var scheduler: PhoneSyncScheduler

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val imageId = SyncProtocol.imageIdFromPath(channel.path) ?: return
        val client = Wearable.getChannelClient(this)
        try {
            val output = Tasks.await(client.getOutputStream(channel), 10, TimeUnit.SECONDS)
            output.use { out ->
                // A missing file results in an empty stream, which the watch treats as "not available".
                images.watchFile(imageId)?.inputStream()?.use { it.copyTo(out) }
            }
        } catch (_: Exception) {
            // The watch retries on its next sync.
        } finally {
            runCatching { Tasks.await(client.close(channel), 5, TimeUnit.SECONDS) }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == SyncProtocol.ACK_PATH) {
            val revision = messageEvent.data.decodeToString()
            runBlocking { preferences.recordAck(revision, System.currentTimeMillis()) }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name == SyncProtocol.CAPABILITY_WATCH && capabilityInfo.nodes.isNotEmpty()) {
            scheduler.pushSoon()
        }
    }
}
