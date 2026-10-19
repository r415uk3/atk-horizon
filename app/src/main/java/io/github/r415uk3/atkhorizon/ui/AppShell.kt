package io.github.r415uk3.atkhorizon.ui

import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.data.ImportError
import io.github.r415uk3.atkhorizon.data.WallpaperError
import io.github.r415uk3.atkhorizon.data.WallpaperFiles
import io.github.r415uk3.atkhorizon.data.saveWallpaper
import io.github.r415uk3.atkhorizon.data.wallpaperMaxSide
import io.github.r415uk3.atkhorizon.ui.eq.BandSheet
import io.github.r415uk3.atkhorizon.ui.glass.GlassButton
import io.github.r415uk3.atkhorizon.ui.glass.GlassDialogHost
import io.github.r415uk3.atkhorizon.ui.glass.GlassDialogLayer
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.glass.HorizonBackground
import io.github.r415uk3.atkhorizon.ui.glass.LiquidSegment
import io.github.r415uk3.atkhorizon.ui.glass.LiquidSegments
import io.github.r415uk3.atkhorizon.ui.glass.LocalBackdrop
import io.github.r415uk3.atkhorizon.ui.glass.LocalGlassDialogHost
import io.github.r415uk3.atkhorizon.ui.screens.DeviceScreen
import io.github.r415uk3.atkhorizon.ui.screens.MicScreen
import io.github.r415uk3.atkhorizon.ui.screens.SettingsScreen
import io.github.r415uk3.atkhorizon.ui.screens.SoundScreen
import io.github.r415uk3.atkhorizon.ui.screens.StatusScreen
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Tab(@StringRes val label: Int) { Sound(R.string.tab_sound), Mic(R.string.tab_mic), Device(R.string.tab_device) }

/** String resource and optional format argument for an error. */
internal fun errorMessage(e: UiError): Pair<Int, Int?> = when (e) {
    is UiError.VerifyFailed -> R.string.error_verify_failed to null
    UiError.Disconnected -> R.string.error_disconnected to null
    UiError.Timeout -> R.string.error_timeout to null
    // Bands are 0-based internally, 1-based for people.
    is UiError.UnstableFilter -> R.string.error_unstable_filter to e.band + 1
    UiError.PresetStorage -> R.string.error_preset_storage to null
    is UiError.ImportFailed -> importErrorMessage(e.reason)
    is UiError.SuspiciousOffset ->
        (if (e.changeBlocked) R.string.error_suspicious_offset_blocked else R.string.error_suspicious_offset) to null
    is UiError.WallpaperFailed -> when (e.reason) {
        WallpaperError.TooLarge -> R.string.wallpaper_error_too_large
        WallpaperError.Unreadable -> R.string.wallpaper_error_unreadable
        WallpaperError.Storage -> R.string.wallpaper_error_storage
    } to null
}

/** Band numbers are 0-based internally, 1-based for people. */
internal fun importErrorMessage(r: ImportError): Pair<Int, Int?> = when (r) {
    ImportError.NotJson -> R.string.import_error_not_json to null
    ImportError.TooLarge -> R.string.import_error_too_large to null
    ImportError.Unreadable -> R.string.import_error_unreadable to null
    ImportError.WrongShape -> R.string.import_error_wrong_shape to null
    ImportError.WrongType -> R.string.import_error_wrong_type to null
    is ImportError.WrongFilterCount -> R.string.import_error_filter_count to r.count
    ImportError.BadFilterIndex -> R.string.import_error_filter_index to null
    is ImportError.UnknownFilterType -> R.string.import_error_filter_type to r.band + 1
    is ImportError.BandOutOfRange -> R.string.import_error_band_range to r.band + 1
    is ImportError.BandUnstable -> R.string.import_error_band_unstable to r.band + 1
}

private val TabBarHeight = 64.dp
private val ContentBottomInset = TabBarHeight + 14.dp + 16.dp

/**
 * The app frame: the background (solid colour or wallpaper), a header with the ⚙︎ button, the
 * screen for the selected tab (or a status screen while there is no ready device), a floating
 * liquid-glass tab bar, an error snackbar and glass dialogs.
 *
 * Glass in the page refracts the background only; floating glass (tab bar, band card, snackbar,
 * dialogs) refracts the background and the page, e.g. the EQ curve.
 */
@Composable
fun AppShell(vm: HorizonViewModel) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Sound) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    /** Band whose sheet is open (Sound tab). */
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val hasDevice = state.conn == ConnState.Ready || state.conn == ConnState.Loading
    val showTabs = hasDevice && !settingsOpen
    val sheetBand = editing?.takeIf { showTabs && tab == Tab.Sound && it in state.bands.indices }
    val context = LocalContext.current
    val wallpaperFiles = remember(context) { WallpaperFiles(context.filesDir) }
    val dialogs = remember { GlassDialogHost() }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = sheetBand != null) { editing = null }

    val background = LocalHorizonColors.current.background
    val backgroundBackdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    val pageBackdrop = rememberLayerBackdrop()
    val floatingBackdrop = rememberCombinedBackdrop(backgroundBackdrop, pageBackdrop)

    CompositionLocalProvider(LocalGlassDialogHost provides dialogs) {
        Box(Modifier.fillMaxSize()) {
            HorizonBackground(state.wallpaperVersion, wallpaperFiles, Modifier.fillMaxSize().layerBackdrop(backgroundBackdrop))
            CompositionLocalProvider(LocalBackdrop provides backgroundBackdrop) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp),
                ) {
                    Header(
                        settingsOpen = settingsOpen,
                        overWallpaper = state.wallpaperVersion != null,
                        subtitle = when {
                            settingsOpen -> stringResource(R.string.settings_subtitle)
                            state.conn == ConnState.Ready -> stringResource(R.string.status_connected)
                            state.conn == ConnState.Loading -> stringResource(R.string.status_loading)
                            else -> null
                        },
                        onToggleSettings = { settingsOpen = !settingsOpen },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            settingsOpen -> SettingsScreen(
                                themeMode = state.themeMode,
                                onTheme = vm::setThemeMode,
                                wallpaperSet = state.wallpaperVersion != null,
                                wallpaperSaving = state.wallpaperSaving,
                                onPickWallpaper = { uri ->
                                    val maxSide = screenMaxSide(context)
                                    vm.setWallpaper {
                                        withContext(Dispatchers.IO) { saveWallpaper(context.applicationContext, uri, wallpaperFiles, maxSide) }
                                    }
                                },
                                onRemoveWallpaper = { vm.removeWallpaper { withContext(Dispatchers.IO) { wallpaperFiles.delete() } } },
                                modifier = Modifier.padding(bottom = ContentBottomInset),
                            )
                            !hasDevice -> StatusScreen(
                                state.conn,
                                onAction = if (state.conn == ConnState.Disconnected) vm::retryConnect else vm::retryPermission,
                                modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp),
                            )
                            tab == Tab.Sound -> SoundScreen(
                                state, vm, onEditBand = { editing = it },
                                modifier = Modifier.padding(bottom = ContentBottomInset),
                            )
                            tab == Tab.Mic -> MicScreen(state, vm, modifier = Modifier.padding(bottom = ContentBottomInset))
                            else -> DeviceScreen(state, vm, modifier = Modifier.padding(bottom = ContentBottomInset))
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalBackdrop provides floatingBackdrop) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ErrorSnackbar(state.error, onDismiss = vm::dismissError)
                    // The band sheet floats in the tab bar's place; tapping another knob switches band.
                    if (sheetBand != null) {
                        BandSheet(
                            index = sheetBand,
                            band = state.bands[sheetBand],
                            enabled = state.conn == ConnState.Ready,
                            onChange = { vm.setBand(sheetBand, it) },
                            onDismiss = { editing = null },
                        )
                    } else if (showTabs) {
                        TabBar(tab, onSelect = { tab = it })
                    }
                }
                GlassDialogLayer(dialogs)
            }
        }
    }
}

/** The longer side of the largest window this device can show, for scaling the wallpaper. */
private fun screenMaxSide(context: Context): Int {
    val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    return wallpaperMaxSide(bounds.width(), bounds.height())
}

@Composable
private fun Header(settingsOpen: Boolean, overWallpaper: Boolean, subtitle: String?, onToggleSettings: () -> Unit) {
    val c = LocalHorizonColors.current
    // Over a busy wallpaper the title sits on bare photo: a soft halo in the background colour
    // keeps it readable without covering the picture.
    val halo = if (overWallpaper) Shadow(c.background.copy(alpha = 0.9f), blurRadius = 16f) else null
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (settingsOpen) R.string.settings_title else R.string.app_title),
                color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                style = TextStyle(shadow = halo),
            )
            if (subtitle != null) Text(subtitle, color = if (overWallpaper) c.ink else c.muted, fontSize = 13.sp, style = TextStyle(shadow = halo))
        }
        val description = stringResource(if (settingsOpen) R.string.settings_close else R.string.settings_open)
        GlassButton(
            onClick = onToggleSettings,
            modifier = Modifier.size(44.dp).semantics { contentDescription = description },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(if (settingsOpen) "✕" else "⚙︎", color = c.ink, fontSize = 18.sp)
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalHorizonColors.current
    LiquidSegments(
        selected = selected.ordinal,
        onSelect = { onSelect(Tab.entries[it]) },
        count = Tab.entries.size,
        modifier = Modifier.fillMaxWidth(),
        height = TabBarHeight,
    ) {
        Tab.entries.forEach { t ->
            LiquidSegment(selected = t == selected, onClick = { onSelect(t) }) {
                Canvas(Modifier.size(22.dp)) { drawTabIcon(t, c.ink) }
                Text(stringResource(t.label), color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Minimal line icons: EQ bars, a microphone, headphones. */
private fun DrawScope.drawTabIcon(tab: Tab, color: Color) {
    val w = size.width
    val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
    when (tab) {
        Tab.Sound -> listOf(0.2f to 0.45f, 0.5f to 0.8f, 0.8f to 0.6f).forEach { (x, h) ->
            drawLine(color, Offset(w * x, w * (0.9f - h)), Offset(w * x, w * 0.9f), stroke.width, StrokeCap.Round)
        }
        Tab.Mic -> {
            drawRoundRect(color, Offset(w * 0.35f, w * 0.05f), Size(w * 0.3f, w * 0.55f), CornerRadius(w * 0.15f), style = stroke)
            drawArc(color, 0f, 180f, false, Offset(w * 0.2f, w * 0.2f), Size(w * 0.6f, w * 0.55f), style = stroke)
            drawLine(color, Offset(w * 0.5f, w * 0.75f), Offset(w * 0.5f, w * 0.95f), stroke.width, StrokeCap.Round)
        }
        Tab.Device -> {
            drawArc(color, 180f, 180f, false, Offset(w * 0.12f, w * 0.1f), Size(w * 0.76f, w * 0.8f), style = stroke)
            drawRoundRect(color, Offset(w * 0.08f, w * 0.5f), Size(w * 0.22f, w * 0.42f), CornerRadius(w * 0.08f))
            drawRoundRect(color, Offset(w * 0.7f, w * 0.5f), Size(w * 0.22f, w * 0.42f), CornerRadius(w * 0.08f))
        }
    }
}

@Composable
private fun ErrorSnackbar(error: UiError?, onDismiss: () -> Unit) {
    val c = LocalHorizonColors.current
    AnimatedContent(
        targetState = error,
        transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { it / 2 }) },
        label = "error",
    ) { e ->
        if (e == null) return@AnimatedContent
        val (res, arg) = errorMessage(e)
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedRectangle(20.dp), surface = c.glassSurfaceFloating) {
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (arg != null) stringResource(res, arg) else stringResource(res),
                    color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                Spacer(Modifier.size(8.dp))
                GlassButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.action_dismiss), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
