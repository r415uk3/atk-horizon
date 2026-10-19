package io.github.r415uk3.atkhorizon.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.shapes.RoundedRectangle
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import kotlin.math.roundToInt

/** Where a menu goes: its top-left corner and whether it opens above the anchor. */
internal data class MenuPlacement(val offset: IntOffset, val above: Boolean)

/**
 * Places a [menu] next to [anchor] inside [bounds] (all in the same coordinates, px): below the
 * anchor, left edges aligned, [gap] apart; above it when it does not fit below but fits above (or
 * there is more room above); always clamped [margin] inside [bounds].
 */
internal fun placeMenu(anchor: IntRect, menu: IntSize, bounds: IntSize, margin: Int, gap: Int): MenuPlacement {
    val minX = margin
    val maxX = (bounds.width - margin - menu.width).coerceAtLeast(minX)
    val minY = margin
    val maxY = (bounds.height - margin - menu.height).coerceAtLeast(minY)
    val below = anchor.bottom + gap
    val above = anchor.top - gap - menu.height
    val fitsBelow = below + menu.height <= bounds.height - margin
    val fitsAbove = above >= margin
    val roomBelow = bounds.height - margin - below
    val roomAbove = anchor.top - gap - margin
    val goAbove = !fitsBelow && (fitsAbove || roomAbove > roomBelow)
    val y = (if (goAbove) above else below).coerceIn(minY, maxY)
    val x = anchor.left.coerceIn(minX, maxX)
    return MenuPlacement(IntOffset(x, y), goAbove)
}

/** Closes the menu that shows these items. */
private val LocalMenuDismiss = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * A liquid-glass context menu for the box's content (its anchor, e.g. a pill): a drop-in for
 * Material's DropdownMenu that is drawn inside the app window by [GlassDialogLayer], so the glass
 * refracts the screen behind it. While open, the page is inert and focus is inside the menu;
 * tapping outside, Back or an item closes it. Fill it with [GlassMenuItem]s. Needs a
 * [GlassDialogHost] ([LocalGlassDialogHost]); without one nothing is shown.
 */
@Composable
fun BoxScope.GlassMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    Spacer(Modifier.matchParentSize().onGloballyPositioned { anchor = it.boundsInWindow() })
    val host = LocalGlassDialogHost.current ?: return
    val at = anchor
    if (!expanded || at == null) return
    val entry = remember { GlassDialogHost.Entry(onDismissRequest, content, at) }
    SideEffect {
        entry.onDismiss = onDismissRequest
        entry.content = content
        entry.anchor = at
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** The open menu's card, placed next to [anchor] (window coordinates). */
@Composable
internal fun GlassMenuLayer(anchor: Rect, cardModifier: Modifier, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalHorizonColors.current
    val density = LocalDensity.current
    val margin = with(density) { MenuMargin.roundToPx() }
    val gap = with(density) { MenuGap.roundToPx() }
    var above by remember { mutableStateOf(false) }
    // Same appear motion as the Liquid controls: a quick spring from slightly small and clear.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
    Layout(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        content = {
            GlassSurface(
                cardModifier
                    .graphicsLayer {
                        val p = appear.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = lerp(0.85f, 1f, p)
                        scaleY = lerp(0.85f, 1f, p)
                        transformOrigin = TransformOrigin(0.15f, if (above) 1f else 0f)
                    }
                    .semantics { isTraversalGroup = true }
                    // Swallow taps so they do not reach the outside-tap catcher.
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedRectangle(22.dp),
                surface = c.glassSurfaceFloating,
                blurRadius = 16.dp,
                refractionHeight = 20.dp,
                refractionAmount = 40.dp,
            ) {
                CompositionLocalProvider(LocalMenuDismiss provides onDismiss) {
                    Column(
                        Modifier.width(IntrinsicSize.Max).widthIn(min = 180.dp, max = 280.dp).padding(vertical = 6.dp),
                        content = content,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val bounds = IntSize(constraints.maxWidth, constraints.maxHeight)
        val card = measurables.single().measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (bounds.width - 2 * margin).coerceAtLeast(0)),
        )
        layout(bounds.width, bounds.height) {
            // The anchor is in window coordinates; this layer starts at the safe-drawing inset.
            val origin = coordinates?.positionInWindow() ?: Offset.Zero
            val local = IntRect(
                left = (anchor.left - origin.x).roundToInt(),
                top = (anchor.top - origin.y).roundToInt(),
                right = (anchor.right - origin.x).roundToInt(),
                bottom = (anchor.bottom - origin.y).roundToInt(),
            )
            val placement = placeMenu(local, IntSize(card.width, card.height), bounds, margin, gap)
            above = placement.above
            card.place(placement.offset)
        }
    }
}

/** A menu row: tapping it closes the menu, then runs [onClick]. */
@Composable
fun GlassMenuItem(text: String, onClick: () -> Unit) {
    val c = LocalHorizonColors.current
    val dismiss = LocalMenuDismiss.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawBehind { if (pressed) drawRect(c.ink.copy(alpha = 0.08f)) }
            .then(highlight.modifier)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                dismiss()
                onClick()
            }
            .then(highlight.gestureModifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
    }
}

private val MenuMargin = 16.dp
private val MenuGap = 6.dp
