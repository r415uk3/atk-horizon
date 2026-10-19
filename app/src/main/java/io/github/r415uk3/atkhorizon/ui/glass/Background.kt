package io.github.r415uk3.atkhorizon.ui.glass

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.r415uk3.atkhorizon.data.WallpaperFiles
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the glass refracts: the theme's solid colour, or the wallpaper (cropped to fill the window,
 * behind the system bars) under the theme's legibility scrim. [wallpaperVersion] null means no
 * wallpaper; a new version reloads the file. A missing or unreadable file shows the solid colour.
 */
@Composable
fun HorizonBackground(wallpaperVersion: Long?, files: WallpaperFiles, modifier: Modifier = Modifier) {
    val c = LocalHorizonColors.current
    val image by produceState<ImageBitmap?>(null, wallpaperVersion, files) {
        value = if (wallpaperVersion == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(files.file.path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.background(c.background)) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(c.wallpaperScrim))
        }
    }
}
