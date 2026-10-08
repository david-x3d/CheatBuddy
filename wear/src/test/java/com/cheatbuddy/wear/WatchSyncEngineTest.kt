package com.cheatbuddy.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheatbuddy.wear.data.ImageStore
import com.cheatbuddy.wear.data.WatchPreferences
import com.cheatbuddy.wear.data.db.WatchDatabase
import com.cheatbuddy.wear.sync.SyncOutcome
import com.cheatbuddy.wear.sync.SyncState
import com.cheatbuddy.wear.sync.WatchSyncEngine
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchSyncEngineTest {

    @get:Rule val temp = TemporaryFolder()
    private lateinit var db: WatchDatabase
    private lateinit var imageStore: ImageStore
    private lateinit var prefs: WatchPreferences
    private val gateway = FakeGateway()
    private lateinit var engine: WatchSyncEngine

    @Before fun setUp() {
        db = inMemoryDatabase(ApplicationProvider.getApplicationContext())
        imageStore = newImageStore(temp.root)
        prefs = preferences(temp.root)
        engine = WatchSyncEngine(gateway, db.sheetDao(), db.imageDao(), imageStore, prefs, clock = { 1_000L })
    }

    @After fun tearDown() = db.close()

    @Test
    fun `first sync stores sheets and images and acknowledges the revision`() = runTest {
        gateway.publish(sheet("a", 1, "img1"), sheet("b", 2))
        gateway.images = mapOf("img1" to jpegBytes())

        assertThat(engine.sync()).isEqualTo(SyncOutcome.Success)

        assertThat(db.sheetDao().getAll().sortedBy { it.position }.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(db.imageDao().ids()).containsExactly("img1")
        assertThat(imageStore.exists("img1")).isTrue()
        assertThat(gateway.acks).containsExactly(gateway.snapshot.manifest!!.revision)
        val status = prefs.syncStatus.first()
        assertThat(status.state).isEqualTo(SyncState.UpToDate)
        assertThat(status.lastSuccessAt).isEqualTo(1_000L)
    }

    @Test
    fun `phone out of range keeps text and tables and waits for images`() = runTest {
        gateway.publish(sheet("a", 1, "img1"))
        gateway.phoneNodeId = null

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.WaitingForPhone(1))
        assertThat(db.sheetDao().get("a")).isNotNull()
        assertThat(gateway.requestedImages).isEmpty()
        assertThat(gateway.acks).isEmpty()
        assertThat(prefs.syncStatus.first().state).isEqualTo(SyncState.WaitingForPhone)
    }

    @Test
    fun `sheets deleted on the phone are removed together with their images`() = runTest {
        gateway.publish(sheet("a", 1, "img1"), sheet("b", 1, "img2"))
        gateway.images = mapOf("img1" to jpegBytes(), "img2" to jpegBytes())
        engine.sync()

        gateway.publish(sheet("a", 1, "img1"))
        assertThat(engine.sync()).isEqualTo(SyncOutcome.Success)

        assertThat(db.sheetDao().getAll().map { it.id }).containsExactly("a")
        assertThat(db.imageDao().ids()).containsExactly("img1")
        assertThat(imageStore.exists("img2")).isFalse()
    }

    @Test
    fun `edited sheet replaces the stored version`() = runTest {
        gateway.publish(sheet("a", 1))
        engine.sync()
        gateway.publish(sheet("a", 2).copy(title = "Renamed"))
        engine.sync()
        assertThat(db.sheetDao().get("a")!!.title).isEqualTo("Renamed")
    }

    @Test
    fun `out of storage is reported as storage full`() = runTest {
        gateway.publish(sheet("a", 1, "img1"))
        gateway.imageError = IOException("write failed: ENOSPC (No space left on device)")

        assertThat(engine.sync()).isEqualTo(SyncOutcome.StorageFull)

        val status = prefs.syncStatus.first()
        assertThat(status.state).isEqualTo(SyncState.StorageFull)
        assertThat(status.message).isNotEmpty()
        // Already applied text content stays available.
        assertThat(db.sheetDao().get("a")).isNotNull()
    }

    @Test
    fun `stalled image stays pending while remaining images finish`() = runTest {
        gateway.publish(sheet("a", 1, "img1", "img2"))
        gateway.stalledImageIds = setOf("img1")
        gateway.images = mapOf("img2" to jpegBytes())

        assertThat(engine.sync()).isEqualTo(SyncOutcome.WaitingForPhone(1))
        assertThat(db.imageDao().ids()).containsExactly("img2")
        assertThat(db.sheetDao().get("a")).isNotNull()
        assertThat(prefs.syncStatus.first().state).isEqualTo(SyncState.WaitingForPhone)
    }

    @Test
    fun `missing image on the phone does not fail the whole sync`() = runTest {
        gateway.publish(sheet("a", 1, "img1", "img2"))
        gateway.images = mapOf("img1" to jpegBytes())

        assertThat(engine.sync()).isEqualTo(SyncOutcome.WaitingForPhone(1))
        assertThat(db.imageDao().ids()).containsExactly("img1")
    }
}
