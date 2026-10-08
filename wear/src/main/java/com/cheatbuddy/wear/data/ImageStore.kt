package com.cheatbuddy.wear.data

import android.graphics.BitmapFactory
import com.cheatbuddy.shared.sync.SyncProtocol
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Thrown when the watch has no room left for synced content. */
class StorageFullException(message: String = "Not enough storage on the watch") : IOException(message)

/** Stores the downsized images received from the phone as files. */
class ImageStore(private val directory: File) {

    init {
        directory.mkdirs()
    }

    fun file(imageId: String): File {
        require(SyncProtocol.isValidId(imageId)) { "Invalid image id" }
        return File(directory, "$imageId.jpg")
    }

    fun exists(imageId: String): Boolean = SyncProtocol.isValidId(imageId) && file(imageId).isFile

    fun hasRoomFor(bytes: Long): Boolean = directory.usableSpace > bytes + RESERVED_BYTES

    /**
     * Copies [input] into the store under [imageId]. Writes go to a temp file first, so an interrupted transfer
     * never leaves a truncated image behind.
     *
     * @return decoded pixel size and byte count.
     */
    fun save(imageId: String, input: InputStream): StoredImage {
        if (!hasRoomFor(MAX_IMAGE_BYTES)) throw StorageFullException()
        val target = file(imageId)
        val temp = File(directory, "$imageId.part")
        try {
            var total = 0L
            temp.outputStream().use { out ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) throw IOException("Image $imageId exceeds size limit")
                    out.write(buffer, 0, read)
                }
            }
            if (total == 0L) throw IOException("Image $imageId is not available on the phone")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Image $imageId is corrupt")
            if (!temp.renameTo(target)) throw IOException("Could not store image $imageId")
            return StoredImage(bounds.outWidth, bounds.outHeight, total)
        } catch (e: IOException) {
            temp.delete()
            throw if (e.isOutOfSpace()) StorageFullException() else e
        }
    }

    fun delete(imageId: String) {
        if (SyncProtocol.isValidId(imageId)) file(imageId).delete()
    }

    data class StoredImage(val width: Int, val height: Int, val sizeBytes: Long)

    companion object {
        /** Watch images are ~400px JPEGs (~30–80 KB); anything above this is rejected. */
        const val MAX_IMAGE_BYTES = 2L * 1024 * 1024
        private const val RESERVED_BYTES = 8L * 1024 * 1024
    }
}

fun Throwable.isOutOfSpace(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is StorageFullException || current is android.database.sqlite.SQLiteFullException) return true
        val message = current.message.orEmpty()
        if ("ENOSPC" in message || "No space left" in message) return true
        current = current.cause
    }
    return false
}
