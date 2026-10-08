package com.cheatbuddy.wear.sync

import com.cheatbuddy.shared.sync.SyncProtocol
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Wakes up on Data Layer events and hands the work to WorkManager. */
@AndroidEntryPoint
class WatchListenerService : WearableListenerService() {

    @Inject lateinit var scheduler: SyncScheduler

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val relevant = dataEvents.any { it.dataItem.uri.path.orEmpty().startsWith(SyncProtocol.ROOT) }
        dataEvents.release()
        if (relevant) scheduler.syncSoon()
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name == SyncProtocol.CAPABILITY_PHONE && capabilityInfo.nodes.isNotEmpty()) {
            scheduler.syncSoon(delaySeconds = 1)
        }
    }
}
