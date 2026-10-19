package io.github.r415uk3.atkhorizon.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.BuildConfig
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.data.ThemeMode
import io.github.r415uk3.atkhorizon.ui.glass.GlassButton
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.glass.LiquidSegment
import io.github.r415uk3.atkhorizon.ui.glass.LiquidSegments
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

internal const val GITHUB_URL = "https://github.com/r415uk3/atk-horizon"

private val ThemeOptions = listOf(
    ThemeMode.Auto to R.string.theme_auto,
    ThemeMode.Light to R.string.theme_light,
    ThemeMode.Dark to R.string.theme_dark,
)

/**
 * Theme switch, wallpaper (picked with the Android photo picker, so no storage permission), source
 * link and app version. [onPickWallpaper] gets the picked image; the caller decodes and stores it.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    wallpaperSet: Boolean,
    wallpaperSaving: Boolean,
    onPickWallpaper: (Uri) -> Unit,
    onRemoveWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalHorizonColors.current
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickWallpaper(uri)
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.theme_title), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val selected = ThemeOptions.indexOfFirst { it.first == themeMode }.coerceAtLeast(0)
                LiquidSegments(
                    selected = selected,
                    onSelect = { onTheme(ThemeOptions[it].first) },
                    count = ThemeOptions.size,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                    surface = c.glassSurface,
                ) {
                    ThemeOptions.forEachIndexed { i, (mode, label) ->
                        LiquidSegment(selected = i == selected, onClick = { onTheme(mode) }, role = Role.RadioButton) {
                            Text(
                                stringResource(label),
                                color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center, maxLines = 1,
                            )
                        }
                    }
                }
                Text(stringResource(R.string.theme_auto_hint), color = c.muted, fontSize = 12.sp)
            }
        }
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.wallpaper_title), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.wallpaper_hint), color = c.muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !wallpaperSaving,
                        filled = true,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.wallpaper_choose), color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (wallpaperSet) {
                        GlassButton(
                            onClick = onRemoveWallpaper,
                            enabled = !wallpaperSaving,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(R.string.wallpaper_remove), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (wallpaperSaving) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = c.muted, strokeWidth = 2.dp)
                        Text(stringResource(R.string.wallpaper_saving), color = c.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        GlassSurface(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                            } catch (_: ActivityNotFoundException) {
                                // No browser installed: nothing to open.
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_source_code), color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.settings_github), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_version), color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(BuildConfig.VERSION_NAME, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
