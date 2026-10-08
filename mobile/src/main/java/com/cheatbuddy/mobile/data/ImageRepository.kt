package com.cheatbuddy.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.sync.SyncProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

class StorageFullException : IOException("Not enough storage on this phone")
class UnreadableImageException : IOException("This image could not be read")

/**
 * Imports images from the gallery or camera. Every image is stored twice:
 * - `images/full/<id>.jpg` (≤1600px) for the editor,
 * - `images/watch/<id>.jpg` (≤400px, compressed) which is what the watch downloads.
 */
class ImageRepository(private val context: Context) {
    private val fullDir = File(context.filesDir, "images/full").apply { mkdirs() }
    private val watchDir = File(context.filesDir, "images/watch").apply { mkdirs() }

    fun fullFile(imageId: String) = File(fullDir, "$imageId.jpg")
    fun watchFile(imageId: String): File? =
        if (SyncProtocol.isValidId(imageId)) File(watchDir, "$imageId.jpg").takeIf { it.isFile } else null

    /** Creates a content URI for the camera to write into. */
    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun import(uri: Uri): ImageBlock = withContext(Dispatchers.IO) {
        if (context.filesDir.usableSpace < MIN_FREE_BYTES) throw StorageFullException()
        val id = UUID.randomUUID().toString()
        try {
            val source = decodeSampled(uri, FULL_MAX_EDGE) ?: throw UnreadableImageException()
            val rotated = source.rotate(readRotation(uri))
            val full = rotated.scaleToFit(FULL_MAX_EDGE)
            val watch = rotated.scaleToFit(SyncProtocol.WATCH_IMAGE_MAX_EDGE)
            write(full, fullFile(id), quality = 88)
            write(watch, File(watchDir, "$id.jpg"), quality = 78)
            ImageBlock(id = UUID.randomUUID().toString(), imageId = id, width = watch.width, height = watch.height)
        } catch (e: IOException) {
            delete(id)
            if (e.message.orEmpty().contains("ENOSPC")) throw StorageFullException()
            throw e
        } finally {
            // Camera captures are temporary.
            if (uri.authority == "${context.packageName}.files") {
                File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
            }
        }
    }

    /**
     * Deletes image files that no sheet references anymore. Recent files are kept, because an image that was
     * just imported may not be saved into its sheet yet.
     */
    suspend fun deleteUnreferenced(referenced: Set<String>, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            listOf(fullDir, watchDir).forEach { dir ->
                dir.listFiles()?.forEach { file ->
                    val stale = now - file.lastModified() > ORPHAN_GRACE_MS
                    if (file.nameWithoutExtension !in referenced && stale) file.delete()
                }
            }
        }

    private fun delete(id: String) {
        fullFile(id).delete()
        File(watchDir, "$id.jpg").delete()
    }

    private fun decodeSampled(uri: Uri, maxEdge: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun readRotation(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    }.getOrDefault(0)

    private fun Bitmap.rotate(degrees: Int): Bitmap =
        if (degrees == 0) this else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    private fun Bitmap.scaleToFit(maxEdge: Int): Bitmap {
        val edge = max(width, height)
        if (edge <= maxEdge) return this
        val factor = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(this, (width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1), true)
    }

    private fun write(bitmap: Bitmap, file: File, quality: Int) {
        val temp = File(file.parentFile, file.name + ".part")
        temp.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) throw IOException("Could not encode image") }
        if (!temp.renameTo(file)) throw IOException("Could not store image")
    }

    private companion object {
        const val FULL_MAX_EDGE = 1600
        const val MIN_FREE_BYTES = 20L * 1024 * 1024
        const val ORPHAN_GRACE_MS = 60L * 60 * 1000
    }
}
