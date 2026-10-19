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

/** The Liquid-Glass palette (docs/design/mockup-main-screen.html, variant A). */
@Immutable
data class HorizonColors(
    val isDark: Boolean,
    val background: Color,
    val blob1: Color,
    val blob2: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val ink: Color,
    val muted: Color,
    /** Selected pill / segment background and its text. */
    val accent: Color,
    val onAccent: Color,
)

val LightHorizonColors = HorizonColors(
    isDark = false,
    background = Color(0xFFE9ECF3),
    blob1 = Color(0xFFBF5AF2).copy(alpha = 0.35f),
    blob2 = Color(0xFF64D2FF).copy(alpha = 0.40f),
    glassFill = Color.White.copy(alpha = 0.42f),
    glassBorder = Color.White.copy(alpha = 0.70f),
    ink = Color(0xFF111111),
    muted = Color.Black.copy(alpha = 0.50f),
    accent = Color(0xFF111111),
    onAccent = Color.White,
)

val DarkHorizonColors = HorizonColors(
    isDark = true,
    background = Color(0xFF0B0D12),
    blob1 = Color(0xFF5E5CE6).copy(alpha = 0.35f),
    blob2 = Color(0xFF0A84FF).copy(alpha = 0.25f),
    glassFill = Color.White.copy(alpha = 0.08f),
    glassBorder = Color.White.copy(alpha = 0.18f),
    ink = Color.White,
    muted = Color.White.copy(alpha = 0.55f),
    accent = Color.White.copy(alpha = 0.90f),
    onAccent = Color.Black,
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
