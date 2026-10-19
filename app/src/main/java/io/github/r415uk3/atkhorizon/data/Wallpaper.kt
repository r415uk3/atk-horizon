package io.github.r415uk3.atkhorizon.data

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.max
import kotlin.math.roundToInt

enum class WallpaperError {
    /** The picked image is larger than [MAX_WALLPAPER_SOURCE_BYTES]. */
    TooLarge,
    /** The picked file could not be read or decoded as an image. */
    Unreadable,
    /** Writing the wallpaper or its setting failed. */
    Storage,
}

sealed interface WallpaperResult {
    /** Saved; [version] identifies this wallpaper so the UI reloads it. */
    data class Saved(val version: Long) : WallpaperResult
    data class Failed(val error: WallpaperError) : WallpaperResult
}

/** Source images above 50 MB are refused before decoding. */
const val MAX_WALLPAPER_SOURCE_BYTES = 50L * 1024 * 1024

private const val WALLPAPER_SIDE_CAP = 2560

/** The longer side a wallpaper is scaled down to: the screen's longer side, at most 2560 px. */
fun wallpaperMaxSide(screenWidth: Int, screenHeight: Int): Int =
    max(screenWidth, screenHeight).takeIf { it > 0 }?.coerceAtMost(WALLPAPER_SIDE_CAP) ?: WALLPAPER_SIDE_CAP

/** [width]×[height] scaled so the longer side is at most [maxSide], keeping the aspect; never upscaled. */
fun wallpaperTargetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longer = max(width, height)
    if (longer <= maxSide) return width to height
    val scale = maxSide.toDouble() / longer
    return (width * scale).roundToInt().coerceIn(1, maxSide) to (height * scale).roundToInt().coerceIn(1, maxSide)
}

/** Copies [input] to [dest]; false (dest content undefined) once more than [maxBytes] were read. Blocking. */
fun copyLimited(input: InputStream, dest: File, maxBytes: Long): Boolean {
    input.use { src ->
        FileOutputStream(dest).use { out ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = src.read(buf)
                if (n < 0) return true
                total += n
                if (total > maxBytes) return false
                out.write(buf, 0, n)
            }
        }
    }
}

/** The wallpaper JPEG in the app's private files directory [dir]. Blocking I/O: call off the main thread. */
class WallpaperFiles(private val dir: File) {
    val file: File get() = File(dir, "wallpaper.jpg")
    val exists: Boolean get() = file.isFile

    /** Writes via a temp file renamed over [file], so a failed write keeps the old wallpaper. */
    @Throws(IOException::class)
    fun writeAtomically(write: (OutputStream) -> Unit) {
        val tmp = File(dir, "wallpaper.jpg.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                write(out)
                out.fd.sync()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
    }

    fun delete() {
        file.delete()
    }
}

/** The decoded wallpaper [image] for [version]; both null when there is no wallpaper. */
data class WallpaperImage<T>(val version: Long?, val image: T?)

/**
 * Keeps the decoded wallpaper for the whole process, so a recreated activity (theme or
 * configuration change) shows it from its first frame. [state] is null until the setting has been
 * read once. A newer version replaces the image only once decoded; a decode failure shows none.
 */
class WallpaperLoader<T : Any>(
    scope: CoroutineScope,
    versions: Flow<Long?>,
    io: CoroutineContext,
    decode: (Long) -> T?,
) {
    private val _state = MutableStateFlow<WallpaperImage<T>?>(null)
    val state: StateFlow<WallpaperImage<T>?> = _state

    init {
        scope.launch {
            versions.distinctUntilChanged().collectLatest { v ->
                _state.value = if (v == null) {
                    WallpaperImage(null, null)
                } else {
                    WallpaperImage(v, withContext(io) { runCatching { decode(v) }.getOrNull() })
                }
            }
        }
    }
}
