package io.github.r415uk3.atkhorizon.ui.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import kotlin.math.pow

/** Types whose gain shapes the curve; the others keep their knob on the 0 dB line. */
internal fun hasGain(t: FilterType) = t == FilterType.Peak || t == FilterType.LowShelf || t == FilterType.HighShelf

private fun knobOffset(b: EqBand, width: Float, height: Float) = Offset(
    Axis.freqToX(b.fc.toDouble().coerceIn(Axis.MIN_HZ, Axis.MAX_HZ), width),
    Axis.gainToY(if (hasGain(b.type)) b.gainDb.coerceIn(-Axis.RANGE_DB, Axis.RANGE_DB) else 0.0, height),
)

private const val SAMPLES = 128
private val FreqLabels = listOf(32 to "32", 125 to "125", 500 to "500", 2000 to "2k", 8000 to "8k", 16000 to "16k")

/**
 * The summed frequency response of [bands] with one knob per band. Dragging a knob moves fc
 * (horizontal, log axis) and gain (vertical, ±12 dB axis; gain-less types keep their gain) via
 * [onDrag], then [onDragEnd] on release; a tap calls [onTap]. Read-only while [enabled] is false.
 */
@Composable
fun EqCurve(
    bands: List<EqBand>,
    enabled: Boolean,
    onDrag: (Int, EqBand) -> Unit,
    onDragEnd: (Int) -> Unit,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalHorizonColors.current
    val coefficients = remember(bands) { bands.mapNotNull(FrequencyResponse::coefficients) }
    val currentBands by rememberUpdatedState(bands)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val tap by rememberUpdatedState(onTap)
    var active by remember { mutableStateOf<Int?>(null) }

    val fillTop = if (c.isDark) Color.White.copy(alpha = 0.3f) else Color(0xFF5E5CE6).copy(alpha = 0.25f)
    val zeroLine = if (c.isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.15f)
    val knobFill = Color.White
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = c.muted, fontSize = 10.sp)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val touchRadius = 24.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val hit = currentBands.indices
                            .map { it to (knobOffset(currentBands[it], w, h) - down.position).getDistance() }
                            .filter { it.second <= touchRadius }
                            .minByOrNull { it.second }?.first ?: return@awaitEachGesture
                        down.consume()
                        // Keep the finger's offset from the knob centre so the knob doesn't jump.
                        val grab = knobOffset(currentBands[hit], w, h) - down.position
                        var dragging = false
                        var released = false
                        var moved = Offset.Zero
                        var last: EqBand? = null
                        active = hit
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    released = true
                                    break
                                }
                                moved += change.positionChange()
                                if (!dragging && moved.getDistance() > viewConfiguration.touchSlop) dragging = true
                                if (!dragging) continue
                                change.consume()
                                val p = change.position + grab
                                val b = currentBands.getOrNull(hit) ?: break
                                val next = b.copy(
                                    fc = Axis.xToFreq(p.x, w),
                                    gainDb = if (hasGain(b.type)) Axis.yToGain(p.y, h) else b.gainDb,
                                )
                                if (next != last) {
                                    last = next
                                    drag(hit, next)
                                }
                            }
                        } finally {
                            active = null
                            if (dragging) dragEnd(hit) else if (released) tap(hit)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val zeroY = Axis.gainToY(0.0, h)
            drawLine(
                zeroLine, Offset(0f, zeroY), Offset(w, zeroY), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
            )

            val curve = Path()
            for (i in 0 until SAMPLES) {
                val t = i / (SAMPLES - 1).toFloat()
                val f = Axis.MIN_HZ * (Axis.MAX_HZ / Axis.MIN_HZ).pow(t.toDouble())
                val db = FrequencyResponse.cascadeDb(coefficients, f).coerceIn(-Axis.RANGE_DB, Axis.RANGE_DB)
                val x = t * w
                val y = Axis.gainToY(db, h)
                if (i == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(curve)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(fillTop, fillTop.copy(alpha = 0f)), startY = 0f, endY = h))
            drawPath(curve, c.ink, style = Stroke(width = 2.5.dp.toPx()))

            val ring = Stroke(width = 1.5.dp.toPx())
            bands.forEachIndexed { i, b ->
                val o = knobOffset(b, w, h)
                val r = (if (i == active) 9 else 7).dp.toPx()
                if (b.type == FilterType.Off) {
                    drawCircle(c.ink, r, o, style = ring)
                } else {
                    drawCircle(knobFill, r, o)
                    if (!c.isDark) drawCircle(c.ink, r, o, style = ring)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            FreqLabels.forEach { (f, label) ->
                val text = measurer.measure(label, labelStyle)
                val x = (Axis.freqToX(f.toDouble(), size.width) - text.size.width / 2f)
                    .coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(x, size.height - text.size.height))
            }
        }
    }
}
