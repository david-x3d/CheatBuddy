package com.cheatbuddy.mobile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cheatbuddy.mobile.sync.PhoneGateway
import com.cheatbuddy.mobile.sync.PhoneSyncEngine
import com.cheatbuddy.mobile.sync.PushResult
import com.cheatbuddy.mobile.sync.SyncPreferences
import com.cheatbuddy.mobile.sync.SyncRecord
import com.cheatbuddy.mobile.sync.SyncStatusRepository
import com.cheatbuddy.mobile.sync.WatchSyncStatus
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.sync.SheetCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class PhoneSyncEngineTest {

    @get:Rule val temp = TemporaryFolder()

    private class FakeGateway : PhoneGateway {
        val items = mutableMapOf<String, Pair<Long, String>>()
        var manifest: String? = null
        var failWith: Exception? = null
        var writes = 0

        override suspend fun publishedVersions() = items.mapValues { it.value.first }
        override suspend fun putSheet(id: String, updatedAt: Long, json: String) {
            failWith?.let { throw it }
            writes++
            items[id] = updatedAt to json
        }
        override suspend fun deleteSheet(id: String) { items.remove(id) }
        override suspend fun putManifest(json: String) { manifest = json }
        override suspend fun isWatchReachable() = true
    }

    private fun sheet(id: String, updatedAt: Long, text: String = "x") =
        Sheet(id, "Title $id", updatedAt, listOf(TextBlock("$id-t", text), TableBlock("$id-tb", listOf(listOf("a")))))

    private var sheets = listOf<Sheet>()
    private val gateway = FakeGateway()
    private val prefs by lazy {
        SyncPreferences(PreferenceDataStoreFactory.create { temp.newFile("sync.preferences_pb").also { it.delete() } })
    }
    private val engine by lazy { PhoneSyncEngine({ sheets }, gateway, prefs, clock = { 42L }) }

    @Test
    fun `publishes sheets and a manifest that decodes on the watch`() = runTest {
        sheets = listOf(sheet("a", 1), sheet("b", 2))

        val result = engine.push()

        assertThat(result).isInstanceOf(PushResult.Published::class.java)
        assertThat(SheetCodec.decodeSheet(gateway.items.getValue("a").second)).isEqualTo(sheets[0])
        val manifest = SheetCodec.decodeManifest(gateway.manifest!!)
        assertThat(manifest.entries.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(prefs.record.first().pushedRevision).isEqualTo(manifest.revision)
    }

    @Test
    fun `only changed sheets are rewritten and deleted sheets are removed`() = runTest {
        sheets = listOf(sheet("a", 1), sheet("b", 1))
        engine.push()
        val writesAfterFirstPush = gateway.writes

        sheets = listOf(sheet("a", 2, text = "edited"))
        engine.push()

        assertThat(gateway.writes - writesAfterFirstPush).isEqualTo(1)
        assertThat(gateway.items.keys).containsExactly("a")
        assertThat(SheetCodec.decodeSheet(gateway.items.getValue("a").second).blocks.first())
            .isEqualTo(TextBlock("a-t", "edited"))
    }

    @Test
    fun `data layer failure is recorded and retryable`() = runTest {
        sheets = listOf(sheet("a", 1))
        gateway.failWith = IOException("Wearable API unavailable")

        val result = engine.push() as PushResult.Failed

        assertThat(result.retryable).isTrue()
        assertThat(prefs.record.first().error).isEqualTo("Wearable API unavailable")
    }

    @Test
    fun `oversized sheet fails without retry`() = runTest {
        sheets = listOf(sheet("a", 1, text = "x".repeat(PhoneSyncEngine.MAX_ITEM_BYTES + 1)))

        val result = engine.push() as PushResult.Failed

        assertThat(result.retryable).isFalse()
        assertThat(gateway.items).isEmpty()
    }

    @Test
    fun `status reflects acknowledgements and connectivity`() {
        val pushed = SyncRecord(pushedRevision = "r2", pushedAt = 10, ackedRevision = "r1", ackedAt = 5)
        assertThat(SyncStatusRepository.derive(pushed, running = false, reachable = false))
            .isEqualTo(WatchSyncStatus.WaitingForWatch)
        assertThat(SyncStatusRepository.derive(pushed.copy(ackedRevision = "r2", ackedAt = 11), false, false))
            .isEqualTo(WatchSyncStatus.Synced(11))
        assertThat(SyncStatusRepository.derive(SyncRecord(pushedRevision = "r1"), false, reachable = false))
            .isEqualTo(WatchSyncStatus.NoWatch)
        assertThat(SyncStatusRepository.derive(pushed.copy(error = "boom"), false, true))
            .isEqualTo(WatchSyncStatus.Failed("boom"))
        assertThat(SyncStatusRepository.derive(pushed, running = true, reachable = true))
            .isEqualTo(WatchSyncStatus.Syncing)
    }
}
