package io.github.r415uk3.atkhorizon.ui.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import io.github.r415uk3.atkhorizon.ui.theme.HorizonColors
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * What glass placed here refracts. The shell provides the background (solid colour or wallpaper)
 * for in-page glass and background + page content for floating glass; a [GlassSurface] provides
 * its own glass to its children, so glass on glass refracts the panel beneath it.
 */
val LocalBackdrop = compositionLocalOf<Backdrop> { emptyBackdrop() }

val GlassCardShape: Shape = RoundedRectangle(26.dp)
val PillShape: Shape = Capsule()

/**
 * The shared liquid-glass recipe (Kyant0/backdrop): saturation boost and a slight brightness shift
 * (away from the ink colour), blur, then lens refraction near the edges.
 */
internal fun BackdropEffectScope.glassEffects(c: HorizonColors, blurRadius: Dp, refractionHeight: Dp, refractionAmount: Dp) {
    colorControls(brightness = c.glassBrightness, saturation = 1.5f)
    if (blurRadius > 0.dp) blur(blurRadius.toPx())
    val h = refractionHeight.toPx().fastCoerceAtMost(size.minDimension / 2f)
    val a = refractionAmount.toPx().fastCoerceAtMost(size.minDimension)
    lens(h, a)
}

/** A tinted glass fill whose hue follows [tint] (the backdrop shows through faintly). */
internal fun DrawScope.drawTint(tint: Color, alpha: Float = 0.85f) {
    drawRect(tint, blendMode = BlendMode.Hue)
    drawRect(tint.copy(alpha = alpha))
}

/**
 * A liquid-glass panel: the [LocalBackdrop] seen through [shape] with vibrancy, blur, lens
 * refraction, a rim highlight and a soft shadow, under the theme's glass fill ([surface]) or a
 * [tint]. Its children get this panel as their backdrop.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    surface: Color = LocalHorizonColors.current.glassSurface,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 10.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 32.dp,
    shadow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalHorizonColors.current
    val panel = rememberLayerBackdrop()
    Box(
        modifier.drawBackdrop(
            backdrop = LocalBackdrop.current,
            shape = { shape },
            effects = { glassEffects(c, blurRadius, refractionHeight, refractionAmount) },
            highlight = { Highlight.Default.copy(alpha = c.glassHighlight) },
            shadow = if (shadow) ({ Shadow(radius = 20.dp, color = c.glassShadow) }) else null,
            exportedBackdrop = panel,
            onDrawSurface = { if (tint.isSpecified) drawTint(tint) else drawRect(surface) },
        ),
    ) {
        CompositionLocalProvider(LocalBackdrop provides panel) { content() }
    }
}

/**
 * A glass capsule button (adapted from the AndroidLiquidGlass catalog's LiquidButton, Apache-2.0,
 * © Kyant0): it swells and follows the finger while pressed. [filled] tints it with the accent.
 * Modified for this app; license text in third_party/AndroidLiquidGlass-LICENSE.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** False when a parent already dims the disabled state. */
    dimWhenDisabled: Boolean = true,
    filled: Boolean = false,
    shape: Shape = PillShape,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalHorizonColors.current
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    Row(
        modifier
            .alpha(if (enabled || !dimWhenDisabled) 1f else 0.45f)
            .drawBackdrop(
                backdrop = LocalBackdrop.current,
                shape = { shape },
                effects = { glassEffects(c, blurRadius = 2.dp, refractionHeight = 12.dp, refractionAmount = 24.dp) },
                highlight = { Highlight.Default.copy(alpha = c.glassHighlight) },
                shadow = { Shadow(radius = 12.dp, color = c.glassShadow) },
                layerBlock = {
                    val progress = highlight.pressProgress
                    val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, progress)
                    val maxOffset = size.minDimension
                    val offset = highlight.offset
                    translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                    translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                    val maxDragScale = 4.dp.toPx() / size.height
                    val angle = atan2(offset.y, offset.x)
                    scaleX = scale + maxDragScale * abs(cos(angle) * offset.x / size.maxDimension) *
                        (size.width / size.height).fastCoerceAtMost(1f)
                    scaleY = scale + maxDragScale * abs(sin(angle) * offset.y / size.maxDimension) *
                        (size.height / size.width).fastCoerceAtMost(1f)
                },
                onDrawSurface = { if (filled) drawTint(c.accent) else drawRect(c.glassSurface) },
            )
            .combinedClickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = role,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .then(if (enabled) highlight.modifier.then(highlight.gestureModifier) else Modifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
