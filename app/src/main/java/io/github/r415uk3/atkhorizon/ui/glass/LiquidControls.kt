// Adapted from the AndroidLiquidGlass catalog app (LiquidToggle, LiquidSlider),
// https://github.com/Kyant0/AndroidLiquidGlass, Apache-2.0, © Kyant0. Unlike the catalog versions
// these are fully controlled: the thumb always ends on the caller's value, so a change the app
// did not apply is never shown as applied.
package io.github.r415uk3.atkhorizon.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The liquid thumb shared by the toggle and the slider: solid at rest, a clear lens while pressed. */
@Composable
private fun Modifier.liquidThumb(damped: DampedDragAnimation, track: com.kyant.backdrop.backdrops.LayerBackdrop, velocityScale: Float): Modifier {
    val c = LocalHorizonColors.current
    val thumbColor = if (c.isDark) Color(0xFFF2F2F5) else Color.White
    return drawBackdrop(
        backdrop = rememberCombinedBackdrop(
            LocalBackdrop.current,
            rememberBackdrop(track) { drawTrack ->
                val progress = damped.pressProgress
                scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) { drawTrack() }
            },
        ),
        shape = { PillShape },
        effects = {
            val progress = damped.pressProgress
            blur(8.dp.toPx() * (1f - progress))
            lens(5.dp.toPx() * progress, 10.dp.toPx() * progress, chromaticAberration = true)
        },
        highlight = {
            Highlight.Ambient.copy(
                width = Highlight.Ambient.width / 1.5f,
                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                alpha = damped.pressProgress,
            )
        },
        shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = if (c.isDark) 0.3f else 0.08f)) },
        innerShadow = { InnerShadow(radius = 4.dp * damped.pressProgress, alpha = damped.pressProgress) },
        layerBlock = {
            scaleX = damped.scaleX
            scaleY = damped.scaleY
            val velocity = damped.velocity / velocityScale
            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
        },
        onDrawSurface = { drawRect(thumbColor.copy(alpha = 1f - damped.pressProgress)) },
    )
}

/** A liquid-glass switch. Tap or drag; the thumb settles on [checked] (the caller's state). */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalHorizonColors.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(LocalDensity.current) { 20.dp.toPx() }
    val scope = rememberCoroutineScope()
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    val damped = remember(scope) {
        DampedDragAnimation(scope, if (checked) 1f else 0f, 0f..1f, 0.001f, initialScale = 1f, pressedScale = 1.5f)
    }
    /** Bumped after a drag so the thumb returns to [checked] even when it did not change. */
    var settle by remember { mutableIntStateOf(0) }
    LaunchedEffect(checked, settle) {
        val target = if (checked) 1f else 0f
        if (damped.targetValue != target) damped.animateToValue(target)
    }
    val track = rememberLayerBackdrop()

    Box(
        modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = null,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    var fraction = damped.targetValue
                    damped.press()
                    horizontalDrag(start.id) { change ->
                        val delta = (change.position.x - change.previousPosition.x) / dragWidth
                        fraction = (fraction + if (isLtr) delta else -delta).fastCoerceIn(0f, 1f)
                        damped.updateValue(fraction)
                        change.consume()
                    }
                    damped.release()
                    val target = fraction >= 0.5f
                    if (target == currentChecked) {
                        settle++
                    } else {
                        damped.animateToValue(if (target) 1f else 0f)
                        currentOnChange(target)
                        // If the change was not taken, go back to what is really set.
                        scope.launch {
                            delay(300)
                            settle++
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(track)
                .clip(PillShape)
                .drawBehind { drawRect(lerp(c.track, c.accent, damped.value.fastCoerceIn(0f, 1f))) }
                .size(64.dp, 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = if (isLtr) lerp(padding, padding + dragWidth, damped.value)
                    else lerp(-padding, -(padding + dragWidth), damped.value)
                }
                .liquidThumb(damped, track, velocityScale = 50f)
                .size(40.dp, 24.dp),
        )
    }
}

/**
 * A liquid-glass slider with the semantics of the Material one: [onValueChange] while dragging
 * (snapped to [steps] when set), [onValueChangeFinished] on release or tap. The thumb follows [value].
 */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = LocalHorizonColors.current
    val scope = rememberCoroutineScope()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange))
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val span = valueRange.endInclusive - valueRange.start
    val damped = remember(scope, valueRange) {
        DampedDragAnimation(scope, value.coerceIn(valueRange), valueRange, span / 1000f, initialScale = 1f, pressedScale = 1.5f)
    }
    LaunchedEffect(damped) {
        snapshotFlow { currentValue }.collect { if (damped.targetValue != it) damped.updateValue(it) }
    }
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(valueRange)
        if (steps <= 0) return clamped
        val step = span / (steps + 1)
        return valueRange.start + ((clamped - valueRange.start) / step).roundToInt() * step
    }
    val track = rememberLayerBackdrop()
    val fill = if (c.isDark) c.ink.copy(alpha = 0.75f) else c.ink

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                if (!enabled) disabled()
                setProgress { target ->
                    if (!enabled) return@setProgress false
                    currentOnChange(snap(target))
                    currentOnFinished?.invoke()
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = constraints.maxWidth.toFloat()
        fun valueAt(x: Float): Float {
            val t = (x / width).fastCoerceIn(0f, 1f)
            return snap(valueRange.start + span * if (isLtr) t else 1f - t)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(enabled, width, valueRange, steps) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (start == null) {
                            // A tap (not a scroll that took the gesture) jumps to that point.
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) {
                                damped.press()
                                currentOnChange(valueAt(down.position.x))
                                currentOnFinished?.invoke()
                                damped.release()
                            }
                            return@awaitEachGesture
                        }
                        damped.press()
                        currentOnChange(valueAt(start.position.x))
                        horizontalDrag(start.id) { change ->
                            currentOnChange(valueAt(change.position.x))
                            change.consume()
                        }
                        damped.release()
                        currentOnFinished?.invoke()
                    }
                },
        )
        Box(Modifier.layerBackdrop(track)) {
            Box(Modifier.clip(PillShape).background(c.track).height(6.dp).fillMaxWidth())
            Box(
                Modifier
                    .clip(PillShape)
                    .background(fill)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val w = (constraints.maxWidth * damped.progress.fastCoerceIn(0f, 1f)).fastRoundToInt()
                        layout(w, placeable.height) { placeable.place(0, 0) }
                    },
            )
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (-size.width / 2f + width * damped.progress)
                        .fastCoerceIn(-size.width / 4f, width - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .liquidThumb(damped, track, velocityScale = 10f)
                .size(40.dp, 24.dp),
        )
    }
}
