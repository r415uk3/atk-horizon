package io.github.r415uk3.atkhorizon.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

/** The backdrop that glass blurs; null (e.g. in previews) draws plain translucent fill. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

val GlassCardShape = RoundedCornerShape(26.dp)
val PillShape = RoundedCornerShape(percent = 50)

/**
 * Frosted glass: the backdrop blurred by ~22 dp under the theme's glass fill, a 1 dp border and a
 * 1 px top rim highlight (AGSL), with the mockup's soft drop shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalHorizonColors.current
    val haze = LocalHazeState.current
    val glass = if (haze != null) {
        Modifier.hazeEffect(
            state = haze,
            style = HazeStyle(
                backgroundColor = c.background,
                tint = HazeTint(c.glassFill),
                blurRadius = 22.dp,
                noiseFactor = 0f,
            ),
        )
    } else {
        Modifier.background(c.glassFill)
    }
    Box(
        modifier
            .shadow(8.dp, shape, clip = false, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .then(glass)
            .border(1.dp, c.glassBorder, shape)
            .rimHighlight(shape),
        content = content,
    )
}

private val ShadowColor = Color.Black.copy(alpha = 0.12f)

/** The two soft pastel blobs behind everything (mockup: 260/280 of the width, blurred 50 px). */
@Composable
fun BlobBackground(modifier: Modifier = Modifier) {
    val c = LocalHorizonColors.current
    Canvas(modifier.background(c.background)) {
        val w = size.width
        // Solid core plus a soft falloff approximates a blurred circle without a blur pass.
        fun blob(color: Color, center: Offset) {
            val core = w * 0.46f
            val radius = core + w * 0.36f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color,
                    core / radius * 0.75f to color,
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        blob(c.blob1, Offset(w * 0.25f, w * 0.21f))
        blob(c.blob2, Offset(w * 0.82f, size.height - w * 0.25f))
    }
}
