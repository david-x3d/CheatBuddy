package com.cheatbuddy.wear

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.sync.SyncManifest
import com.cheatbuddy.wear.data.ImageStore
import com.cheatbuddy.wear.data.WatchPreferences
import com.cheatbuddy.wear.data.db.WatchDatabase
import com.cheatbuddy.wear.sync.RemoteSnapshot
import com.cheatbuddy.wear.sync.WearableGateway
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

fun sheet(id: String, updatedAt: Long = 1, vararg imageIds: String) = Sheet(
    id = id,
    title = "Sheet $id",
    updatedAt = updatedAt,
    blocks = listOf(
        TextBlock("$id-text", "Hello $id"),
        TableBlock("$id-table", listOf(listOf("Key", "Value"), listOf("a", "1"))),
    ) + imageIds.map { ImageBlock("$id-$it", it, 8, 8) },
)

fun inMemoryDatabase(context: Context): WatchDatabase =
    Room.inMemoryDatabaseBuilder(context, WatchDatabase::class.java).allowMainThreadQueries().build()

fun preferences(dir: File) = WatchPreferences(PreferenceDataStoreFactory.create { File(dir, "test.preferences_pb") })

fun jpegBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
}

/** Configurable fake of the Data Layer. */
class FakeGateway(
    var snapshot: RemoteSnapshot = RemoteSnapshot(null, emptyMap()),
    var phoneNodeId: String? = "phone",
    var images: Map<String, ByteArray> = emptyMap(),
    var imageError: IOException? = null,
) : WearableGateway {
    val acks = mutableListOf<String>()
    val requestedImages = mutableListOf<String>()
    var stalledImageIds: Set<String> = emptySet()

    fun publish(vararg sheets: Sheet) {
        snapshot = RemoteSnapshot(SyncManifest.of(sheets.toList()), sheets.associateBy { it.id })
    }

    override suspend fun readSnapshot() = snapshot
    override suspend fun findReachablePhone() = phoneNodeId

    override suspend fun <T> receiveImage(nodeId: String, imageId: String, consume: (InputStream) -> T): T {
        requestedImages += imageId
        if (imageId in stalledImageIds) delay(Long.MAX_VALUE)
        imageError?.let { throw it }
        return consume((images[imageId] ?: ByteArray(0)).inputStream())
    }

    override suspend fun sendAck(nodeId: String, revision: String) {
        acks += revision
    }
}

/** A gateway that behaves like a phone that is switched off and out of range. */
class PhoneOffGateway : WearableGateway {
    var calls = 0
    override suspend fun readSnapshot(): RemoteSnapshot { calls++; throw IOException("Wearable API unavailable") }
    override suspend fun findReachablePhone(): String? { calls++; return null }
    override suspend fun <T> receiveImage(nodeId: String, imageId: String, consume: (InputStream) -> T): T {
        calls++; throw IOException("No phone")
    }
    override suspend fun sendAck(nodeId: String, revision: String) { calls++; throw IOException("No phone") }
}

fun newImageStore(dir: File) = ImageStore(File(dir, "images"))
