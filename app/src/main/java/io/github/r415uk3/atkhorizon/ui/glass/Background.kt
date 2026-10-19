package io.github.r415uk3.atkhorizon.ui.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

/**
 * What the glass refracts: the theme's solid colour, or the wallpaper [image] (cropped to fill the
 * window, behind the system bars) under the theme's legibility scrim.
 */
@Composable
fun HorizonBackground(image: ImageBitmap?, modifier: Modifier = Modifier) {
    val c = LocalHorizonColors.current
    Box(modifier.background(c.background)) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(c.wallpaperScrim))
        }
    }
}
