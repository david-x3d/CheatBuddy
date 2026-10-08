package com.cheatbuddy.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.wear.data.SheetRepository
import com.cheatbuddy.wear.data.db.ImageEntity
import com.cheatbuddy.wear.data.db.WatchDatabase
import com.cheatbuddy.wear.data.toEntity
import com.cheatbuddy.wear.sync.SyncOutcome
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

/** Proves the watch shows previously synced content from its own storage with no phone connected. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class OfflineLoadTest {

    @get:Rule val temp = TemporaryFolder()
    private lateinit var db: WatchDatabase

    @Before fun setUp() {
        db = inMemoryDatabase(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() = db.close()

    @Test
    fun `sheets load from the local database while the phone is off`() = runTest {
        // Content synced earlier.
        val synced = listOf(sheet("a", 10, "img1"), sheet("b", 20))
        db.sheetDao().upsert(synced.mapIndexed { index, sheet -> sheet.toEntity(index) })
        val imageStore = newImageStore(temp.root)
        imageStore.file("img1").writeBytes(byteArrayOf(1, 2, 3))
        db.imageDao().insert(ImageEntity("img1", 8, 8, 3, 0))

        // The phone is now unreachable: every Data Layer call fails.
        val phoneOff = PhoneOffGateway()
        val prefs = preferences(temp.root)
        val engine = WatchSyncEngine(phoneOff, db.sheetDao(), db.imageDao(), imageStore, prefs)
        val outcome = engine.sync()
        assertThat(outcome).isInstanceOf(SyncOutcome.Failed::class.java)

        // The UI layer still gets everything from local storage.
        val repository = SheetRepository(db.sheetDao(), db.imageDao(), imageStore, prefs)
        val summaries = repository.observeSummaries().first()
        assertThat(summaries.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(summaries.first().firstImageId).isEqualTo("img1")

        val sheet = repository.observeSheet("a").first()!!
        assertThat(sheet.blocks.filterIsInstance<TableBlock>().single().rows).hasSize(2)
        assertThat(sheet.blocks.filterIsInstance<ImageBlock>().single().imageId).isEqualTo("img1")
        assertThat(repository.observeAvailableImages().first()).containsExactly("img1")
        assertThat(repository.imageFile("img1").exists()).isTrue()

        // Reading content never touched the Data Layer; only the background sync attempt did.
        val callsAfterSync = phoneOff.calls
        repository.observeSummaries().first()
        repository.observeSheet("b").first()
        assertThat(phoneOff.calls).isEqualTo(callsAfterSync)
    }
}
