package io.github.r415uk3.atkhorizon.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.ui.glass.BlobBackground
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.glass.LocalHazeState
import io.github.r415uk3.atkhorizon.ui.glass.PillShape
import io.github.r415uk3.atkhorizon.ui.eq.BandSheet
import io.github.r415uk3.atkhorizon.ui.screens.SoundScreen
import io.github.r415uk3.atkhorizon.ui.screens.StatusScreen
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

enum class Tab(@StringRes val label: Int) { Sound(R.string.tab_sound), Mic(R.string.tab_mic), Device(R.string.tab_device) }

/** String resource and optional format argument for an error. */
internal fun errorMessage(e: UiError): Pair<Int, Int?> = when (e) {
    is UiError.VerifyFailed -> R.string.error_verify_failed to null
    UiError.Disconnected -> R.string.error_disconnected to null
    UiError.Timeout -> R.string.error_timeout to null
    // Bands are 0-based internally, 1-based for people.
    is UiError.UnstableFilter -> R.string.error_unstable_filter to e.band + 1
    UiError.PresetStorage -> R.string.error_preset_storage to null
}

private val TabBarHeight = 54.dp
private val ContentBottomInset = TabBarHeight + 14.dp + 16.dp

/**
 * The app frame: pastel blobs, a header with the ⚙︎ button, the screen for the selected tab (or a
 * status screen while there is no ready device), a floating glass tab bar and an error snackbar.
 */
@Composable
fun AppShell(vm: HorizonViewModel) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Sound) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    /** Band whose sheet is open (Sound tab). */
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val haze = rememberHazeState()
    val hasDevice = state.conn == ConnState.Ready || state.conn == ConnState.Loading
    val showTabs = hasDevice && !settingsOpen
    val sheetBand = editing?.takeIf { showTabs && tab == Tab.Sound && it in state.bands.indices }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = sheetBand != null) { editing = null }

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            // zIndex 0: glass inside the content blurs only the blobs; the tab bar (outside any
            // source) blurs blobs and content.
            BlobBackground(Modifier.fillMaxSize().hazeSource(haze, zIndex = 0f))
            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze, zIndex = 1f)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Header(
                    settingsOpen = settingsOpen,
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
                        settingsOpen -> PlaceholderScreen(R.string.theme_title)
                        !hasDevice -> StatusScreen(
                            state.conn,
                            onAction = if (state.conn == ConnState.Disconnected) vm::retryConnect else vm::retryPermission,
                            modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp),
                        )
                        tab == Tab.Sound -> SoundScreen(
                            state, vm, onEditBand = { editing = it },
                            modifier = Modifier.padding(bottom = ContentBottomInset),
                        )
                        else -> PlaceholderScreen(
                            when (tab) {
                                Tab.Mic -> R.string.mic_noise_cancelling
                                else -> R.string.device_model
                            },
                        )
                    }
                }
            }

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
        }
    }
}

@Composable
private fun Header(settingsOpen: Boolean, subtitle: String?, onToggleSettings: () -> Unit) {
    val c = LocalHorizonColors.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (settingsOpen) R.string.settings_title else R.string.app_title),
                color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
            )
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 13.sp)
        }
        val description = stringResource(if (settingsOpen) R.string.settings_close else R.string.settings_open)
        GlassSurface(Modifier.size(40.dp), shape = CircleShape) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(role = Role.Button, onClick = onToggleSettings)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (settingsOpen) "✕" else "⚙︎", color = c.ink, fontSize = 18.sp)
            }
        }
    }
}

/** Stand-in until the real screen lands (Mic, Device, Settings: Task 11). */
@Composable
private fun PlaceholderScreen(title: Int) {
    val c = LocalHorizonColors.current
    Column(Modifier.fillMaxSize().padding(bottom = ContentBottomInset)) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Text(
                stringResource(title), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalHorizonColors.current
    GlassSurface(Modifier.fillMaxWidth().height(TabBarHeight), shape = PillShape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(PillShape)
                        .clickable(role = Role.Tab, onClick = { onSelect(t) })
                        .padding(vertical = 6.dp)
                        .alpha(if (on) 1f else 0.5f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Canvas(Modifier.size(20.dp)) { drawTabIcon(t, c.ink) }
                    Text(stringResource(t.label), color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
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
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (arg != null) stringResource(res, arg) else stringResource(res),
                    color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.action_dismiss),
                    color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
