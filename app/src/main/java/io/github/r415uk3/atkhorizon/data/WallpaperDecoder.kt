package io.github.r415uk3.atkhorizon.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * Reads the picked image [uri] (at most [MAX_WALLPAPER_SOURCE_BYTES]), decodes it with
 * [ImageDecoder] (which applies the EXIF rotation) scaled so its longer side is at most [maxSide],
 * and saves it as a JPEG (quality 90) over the current wallpaper. Never throws. Blocking: call off
 * the main thread.
 */
fun saveWallpaper(context: Context, uri: Uri, files: WallpaperFiles, maxSide: Int): WallpaperResult {
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
        } catch (_: Exception) {
            WallpaperResult.Failed(WallpaperError.Storage)
        } finally {
            bitmap.recycle()
        }
    } finally {
        source.delete()
    }
}
