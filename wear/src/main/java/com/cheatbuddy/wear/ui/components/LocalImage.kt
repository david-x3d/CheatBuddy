package com.cheatbuddy.wear.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Tiny decoder + memory cache for local image files. Files are small (≤400px), so decoding is fast. */
object LocalImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(file: File, maxEdge: Int): ImageBitmap? = cache.get(key(file, maxEdge))

    fun decode(file: File, maxEdge: Int): ImageBitmap? {
        val key = key(file, maxEdge)
        cache.get(key)?.let { return it }
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxEdge && bounds.outHeight / (sample * 2) >= maxEdge) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun key(file: File, maxEdge: Int) = "${file.path}@$maxEdge"
}

/** Returns the decoded image; the first value comes from the cache, so revisits render instantly. */
@Composable
fun rememberLocalImage(file: File?, maxEdge: Int = 400): State<ImageBitmap?> =
    produceState(initialValue = file?.let { LocalImageCache.peek(it, maxEdge) }, file, maxEdge) {
        if (file != null && value == null) {
            value = withContext(Dispatchers.IO) { runCatching { LocalImageCache.decode(file, maxEdge) }.getOrNull() }
        }
    }
