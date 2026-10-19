package io.github.r415uk3.atkhorizon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.r415uk3.atkhorizon.data.ThemeMode

/**
 * The Liquid-Glass palette: a solid background (or the user's wallpaper under a scrim) that the
 * glass surfaces refract, plus per-theme glass tuning so text stays legible over both.
 */
@Immutable
data class HorizonColors(
    val isDark: Boolean,
    /** Solid background when there is no wallpaper. */
    val background: Color,
    /** Drawn over the wallpaper for legibility. */
    val wallpaperScrim: Color,
    /** Fill drawn over the refracted backdrop of a glass surface (cards, bars, pills). */
    val glassSurface: Color,
    /** Fill of floating glass (tab bar, band card, snackbar), which sits over busier content. */
    val glassSurfaceFloating: Color,
    /** Brightness shift applied to the backdrop seen through glass (pushes it away from the ink). */
    val glassBrightness: Float,
    /** Rim highlight strength (0…1). */
    val glassHighlight: Float,
    /** Drop shadow under glass. */
    val glassShadow: Color,
    /** Dim behind a glass dialog. */
    val dialogScrim: Color,
    val ink: Color,
    val muted: Color,
    /** Selected pill / segment background and its text. */
    val accent: Color,
    val onAccent: Color,
    /** Unfilled part of slider and toggle tracks. */
    val track: Color,
)

/** Scrim over a wallpaper: white 30 % in the light theme, black 35 % in the dark one. */
val WallpaperScrimLight = Color.White.copy(alpha = 0.30f)
val WallpaperScrimDark = Color.Black.copy(alpha = 0.35f)

val LightHorizonColors = HorizonColors(
    isDark = false,
    background = Color(0xFFE9ECF3),
    wallpaperScrim = WallpaperScrimLight,
    glassSurface = Color.White.copy(alpha = 0.50f),
    glassSurfaceFloating = Color(0xFFFAFAFA).copy(alpha = 0.58f),
    glassBrightness = 0.04f,
    glassHighlight = 1f,
    glassShadow = Color.Black.copy(alpha = 0.10f),
    dialogScrim = Color(0xFF29293A).copy(alpha = 0.23f),
    ink = Color(0xFF111111),
    muted = Color.Black.copy(alpha = 0.65f),
    accent = Color(0xFF111111),
    onAccent = Color.White,
    track = Color(0xFF787878).copy(alpha = 0.22f),
)

val DarkHorizonColors = HorizonColors(
    isDark = true,
    background = Color(0xFF0B0D12),
    wallpaperScrim = WallpaperScrimDark,
    glassSurface = Color(0xFF16181E).copy(alpha = 0.58f),
    glassSurfaceFloating = Color(0xFF121212).copy(alpha = 0.64f),
    glassBrightness = -0.04f,
    glassHighlight = 0.8f,
    glassShadow = Color.Black.copy(alpha = 0.25f),
    dialogScrim = Color(0xFF121212).copy(alpha = 0.56f),
    ink = Color.White,
    muted = Color.White.copy(alpha = 0.70f),
    accent = Color.White.copy(alpha = 0.92f),
    onAccent = Color.Black,
    track = Color(0xFF787880).copy(alpha = 0.36f),
)

val LocalHorizonColors = staticCompositionLocalOf { LightHorizonColors }

/** Whether [mode] resolves to dark; Auto follows the system. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.Auto -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** Glass palette plus a matching Material scheme (system sans font, Material defaults for type). */
@Composable
fun HorizonTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = if (mode.isDark()) DarkHorizonColors else LightHorizonColors
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.ink,
            surface = colors.background, onSurface = colors.ink,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.ink,
            surface = colors.background, onSurface = colors.ink,
        )
    }
    CompositionLocalProvider(LocalHorizonColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
