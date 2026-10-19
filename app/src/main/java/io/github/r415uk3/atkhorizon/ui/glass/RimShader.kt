package io.github.r415uk3.atkhorizon.ui.glass

import android.graphics.RuntimeShader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * AGSL rim light: a 1 px highlight that follows the top edge of a rounded rectangle, like the
 * mockup's `box-shadow: inset 0 1px 0 rgba(255,255,255,.4)`. A pixel is lit when it is inside the
 * shape but the point [width] px above it is outside, so the line thins out around the corners.
 */
private const val RIM_AGSL = """
uniform float2 size;
uniform float radius;
uniform float width;
uniform float alpha;

float roundRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - hs;
    float r = min(radius, min(hs.x, hs.y));
    float inside = 1.0 - smoothstep(-0.5, 0.5, roundRect(p, hs, r));
    float aboveOutside = smoothstep(-0.5, 0.5, roundRect(p - float2(0.0, width), hs, r));
    float a = alpha * inside * aboveOutside;
    return half4(a, a, a, a);
}
"""

/** Corner radius in px of [shape] at [size]; the shader models rounded rectangles only. */
internal fun cornerRadiusPx(shape: Shape, size: Size, layoutDirection: LayoutDirection, density: Density): Float =
    when (val o = shape.createOutline(size, layoutDirection, density)) {
        is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
        is Outline.Rectangle -> 0f
        is Outline.Generic -> 0f
    }

/** Draws the rim highlight of [shape] behind the content. */
fun Modifier.rimHighlight(shape: Shape, alpha: Float = 0.4f): Modifier = drawWithCache {
    val shader = RuntimeShader(RIM_AGSL).apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("radius", cornerRadiusPx(shape, size, layoutDirection, this@drawWithCache))
        setFloatUniform("width", 1.dp.toPx())
        setFloatUniform("alpha", alpha)
    }
    val brush = ShaderBrush(shader)
    onDrawBehind { drawRect(brush) }
}
