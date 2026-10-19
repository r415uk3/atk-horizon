package io.github.r415uk3.atkhorizon.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.r415uk3.atkhorizon.data.ImportError
import io.github.r415uk3.atkhorizon.data.ImportResult
import io.github.r415uk3.atkhorizon.data.parseHubEq
import java.io.ByteArrayOutputStream

internal val IMPORT_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

private const val MAX_IMPORT_BYTES = 64 * 1024

/** The file name without its extension (preset name fallback); blank when unknown. */
internal fun nameWithoutExtension(displayName: String?): String =
    displayName.orEmpty().substringBeforeLast('.', displayName.orEmpty()).trim()

/** Reads at most 64 KB; null when the file is larger. Blocking: call off the main thread. */
internal fun readLimited(open: () -> java.io.InputStream?): String? {
    val input = open() ?: throw java.io.IOException("cannot open")
    input.use {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = it.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_IMPORT_BYTES) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/** Reads and parses a picked file. Blocking: call off the main thread. */
internal fun readImportFile(resolver: ContentResolver, uri: Uri): ImportResult = try {
    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
    val text = readLimited { resolver.openInputStream(uri) }
    if (text == null) ImportResult.Failed(ImportError.TooLarge)
    else parseHubEq(text, nameWithoutExtension(displayName))
} catch (_: Exception) {
    ImportResult.Failed(ImportError.Unreadable)
}
