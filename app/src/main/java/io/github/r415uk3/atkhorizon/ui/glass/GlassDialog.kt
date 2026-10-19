package io.github.r415uk3.atkhorizon.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.shapes.RoundedRectangle
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

/**
 * Shows glass dialogs inside the app window, so they can refract the screen behind them (a
 * platform dialog window has no access to the app's backdrop). Place [GlassDialogLayer] last in
 * the shell and provide the host through [LocalGlassDialogHost].
 */
@Stable
class GlassDialogHost {
    internal val entries = mutableStateListOf<Entry>()

    internal class Entry(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        var onDismiss by mutableStateOf(onDismiss)
        var content by mutableStateOf(content)
    }
}

val LocalGlassDialogHost = staticCompositionLocalOf<GlassDialogHost?> { null }

/** The topmost dialog of [host] over a dim scrim; tapping the scrim or Back dismisses it. */
@Composable
fun GlassDialogLayer(host: GlassDialogHost) {
    val c = LocalHorizonColors.current
    val top = host.entries.lastOrNull()
    AnimatedVisibility(visible = top != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.dialogScrim)
                .pointerInput(Unit) { detectTapGestures { host.entries.lastOrNull()?.onDismiss?.invoke() } },
        )
    }
    if (top != null) {
        BackHandler { top.onDismiss() }
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.padding(horizontal = 28.dp), top.content)
        }
    }
}

@Composable
private fun DialogCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalHorizonColors.current
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = appear, enter = fadeIn() + scaleIn(initialScale = 0.92f)) {
        GlassSurface(
            modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .semantics { dialog() }
                // Swallow taps so they do not reach the scrim.
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedRectangle(32.dp),
            surface = c.glassSurfaceFloating,
            blurRadius = 16.dp,
            refractionHeight = 24.dp,
            refractionAmount = 48.dp,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

/**
 * An alert dialog in liquid glass ([GlassDialogLayer] draws it). Without a host (e.g. a preview)
 * it falls back to a platform dialog window.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    title: String? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val c = LocalHorizonColors.current
    val body: @Composable ColumnScope.() -> Unit = {
        if (title != null) Text(title, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (text != null) text()
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dismissButton?.invoke(this)
            confirmButton()
        }
    }
    val host = LocalGlassDialogHost.current
    if (host == null) {
        Dialog(onDismissRequest) { DialogCard(Modifier, body) }
        return
    }
    val entry = remember { GlassDialogHost.Entry(onDismissRequest, body) }
    SideEffect {
        entry.onDismiss = onDismissRequest
        entry.content = body
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** A dialog action: [primary] is accent-filled glass, otherwise plain glass. */
@Composable
fun GlassDialogButton(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    val c = LocalHorizonColors.current
    GlassButton(onClick = onClick, enabled = enabled, filled = primary, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp)) {
        Text(text, color = if (primary) c.onAccent else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
