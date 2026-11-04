package com.bloodmoon.app.util

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icons of the apps in the Exclusions list, loaded on demand and scaled down to the size at which
 * they are displayed.
 *
 * Before, the screen kept the ORIGINAL Drawable of ALL installed apps (adaptive icons have layers
 * of ~432 px, ~1.5 MB each, times 100-200 apps) and Coil also kept its own cache of decoded bitmaps
 * that outlived the screen (a process-wide singleton, 25% of the app's memory by default). Now only
 * the visible items load their icon, at [sizePx] x [sizePx] (≈ 37 KB), in a small LRU cache - and
 * the screen empties it when leaving ([clear]).
 */
object AppIconCache {
    private const val MAX_ENTRIES = 48
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
            }.getOrNull()
        } ?: return null
        cache.put(packageName, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()
}
