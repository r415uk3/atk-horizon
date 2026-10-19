package io.github.r415uk3.atkhorizon.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/**
 * Reads the picked image [uri] (at most [MAX_WALLPAPER_SOURCE_BYTES]), decodes it with
 * [ImageDecoder] (which applies the EXIF rotation) scaled so its longer side is at most [maxSide],
 * and saves it as a JPEG (quality 90) over the current wallpaper. Never throws. Blocking: call off
 * the main thread.
 */
fun saveWallpaper(context: Context, uri: Uri, files: WallpaperFiles, maxSide: Int): WallpaperResult {
    // The reported size refuses an oversized file without reading it; the copy limit stays as
    // the backstop for providers that report no (or a wrong) size.
    if ((reportedSize(context, uri) ?: 0L) > MAX_WALLPAPER_SOURCE_BYTES) return WallpaperResult.Failed(WallpaperError.TooLarge)
    val source = File(context.cacheDir, "wallpaper-source.tmp")
    try {
        val copied = try {
            val input = context.contentResolver.openInputStream(uri) ?: return WallpaperResult.Failed(WallpaperError.Unreadable)
            copyLimited(input, source, MAX_WALLPAPER_SOURCE_BYTES)
        } catch (_: Exception) {
            return WallpaperResult.Failed(WallpaperError.Unreadable)
        }
        if (!copied) return WallpaperResult.Failed(WallpaperError.TooLarge)

        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                val (w, h) = wallpaperTargetSize(info.size.width, info.size.height, maxSide)
                decoder.setTargetSize(w, h)
                // Software memory so the bitmap can be JPEG-encoded.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) {
            return WallpaperResult.Failed(WallpaperError.Unreadable)
        } catch (_: OutOfMemoryError) {
            return WallpaperResult.Failed(WallpaperError.TooLarge)
        }

        return try {
            files.writeAtomically { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw IOException("JPEG encoding failed")
            }
            WallpaperResult.Saved(System.currentTimeMillis())
        } catch (_: OutOfMemoryError) {
            WallpaperResult.Failed(WallpaperError.TooLarge)
        } catch (_: Exception) {
            WallpaperResult.Failed(WallpaperError.Storage)
        } finally {
            bitmap.recycle()
        }
    } finally {
        source.delete()
    }
}

/** The picked file's size from its provider; null when unknown. */
private fun reportedSize(context: Context, uri: Uri): Long? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }
} catch (_: Exception) {
    null
}
