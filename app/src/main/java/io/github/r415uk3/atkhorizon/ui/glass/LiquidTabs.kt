// Adapted from the AndroidLiquidGlass catalog app (LiquidBottomTabs, LiquidBottomTab),
// https://github.com/Kyant0/AndroidLiquidGlass, Apache-2.0, © Kyant0.
package io.github.r415uk3.atkhorizon.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private val LocalTabScale = staticCompositionLocalOf { { 1f } }

/**
 * A glass capsule of [count] segments with a liquid selection lens that can be tapped or dragged
 * between segments (the floating tab bar and the theme switch). [selected] is the source of truth:
 * a tap or drag calls [onSelect], and the lens follows [selected] when it changes.
 */
@Composable
fun LiquidSegments(
    selected: Int,
    onSelect: (Int) -> Unit,
    count: Int,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    inset: Dp = 4.dp,
    surface: Color = LocalHorizonColors.current.glassSurfaceFloating,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalHorizonColors.current
    val backdrop = LocalBackdrop.current
    val segmentsBackdrop = rememberLayerBackdrop()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val indicatorHeight = height - inset * 2

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - (inset * 2).toPx()) / count }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val scope = rememberCoroutineScope()
        val damped = remember(scope, count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selected.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                    animateToValue(target.toFloat())
                    if (target != currentSelected) currentOnSelect(target)
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue((targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, (count - 1).toFloat()))
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        // The lens always ends on the real selection (e.g. a choice that was not applied).
        LaunchedEffect(damped) {
            snapshotFlow { currentSelected }.drop(1).collectLatest { index ->
                damped.animateToValue(index.toFloat())
            }
        }
        val highlight = remember(scope) {
            InteractiveHighlight(scope) { size, _ ->
                Offset(
                    if (isLtr) (damped.value + 0.5f) * tabWidth + panelOffset
                    else size.width - (damped.value + 0.5f) * tabWidth + panelOffset,
                    size.height / 2f,
                )
            }
        }

        // The visible segments on a glass capsule.
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { PillShape },
                    effects = { glassEffects(c, blurRadius = 8.dp, refractionHeight = 24.dp, refractionAmount = 24.dp) },
                    highlight = { Highlight.Default.copy(alpha = c.glassHighlight) },
                    shadow = { Shadow(radius = 20.dp, color = c.glassShadow) },
                    layerBlock = {
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, damped.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(surface) },
                )
                .then(highlight.modifier)
                .height(height)
                .fillMaxWidth()
                .padding(inset),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // An invisible copy, ink-tinted, that only the lens shows (the selected look).
        CompositionLocalProvider(LocalTabScale provides { lerp(1f, 1.2f, damped.pressProgress) }) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(segmentsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { PillShape },
                        effects = {
                            val progress = damped.pressProgress
                            glassEffects(c, blurRadius = 8.dp, refractionHeight = 24.dp * progress, refractionAmount = 24.dp * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = damped.pressProgress) },
                        onDrawSurface = { drawRect(surface) },
                    )
                    .then(highlight.modifier)
                    .height(indicatorHeight)
                    .fillMaxWidth()
                    .padding(horizontal = inset)
                    .graphicsLayer(colorFilter = ColorFilter.tint(c.ink)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // The lens.
        Box(
            Modifier
                .padding(horizontal = inset)
                .graphicsLayer {
                    translationX = if (isLtr) damped.value * tabWidth + panelOffset
                    else size.width - (damped.value + 1f) * tabWidth + panelOffset
                }
                .clearAndSetSemantics {}
                .then(highlight.gestureModifier)
                .then(damped.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, segmentsBackdrop),
                    shape = { PillShape },
                    effects = {
                        val progress = damped.pressProgress
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = damped.pressProgress) },
                    shadow = { Shadow(alpha = damped.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * damped.pressProgress, alpha = damped.pressProgress) },
                    layerBlock = {
                        scaleX = damped.scaleX
                        scaleY = damped.scaleY
                        val velocity = damped.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = damped.pressProgress
                        drawRect(c.ink.copy(alpha = if (c.isDark) 0.12f else 0.10f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(indicatorHeight)
                .fillMaxWidth(1f / count),
        )
    }
}

/** One segment of [LiquidSegments]: a full-height, equally wide tap target. */
@Composable
fun RowScope.LiquidSegment(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalTabScale.current
    Column(
        modifier
            .clip(PillShape)
            .semantics { this.selected = selected }
            .clickable(interactionSource = null, indication = null, role = role, onClick = onClick)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
