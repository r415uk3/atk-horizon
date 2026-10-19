package io.github.r415uk3.atkhorizon.ui.eq

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.glass.PillShape
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@StringRes
private fun typeLabel(t: FilterType): Int = when (t) {
    FilterType.Off -> R.string.band_type_off
    FilterType.LowShelf -> R.string.band_type_low_shelf
    FilterType.Peak -> R.string.band_type_peak
    FilterType.HighShelf -> R.string.band_type_high_shelf
    FilterType.LowPass -> R.string.band_type_low_pass
    FilterType.HighPass -> R.string.band_type_high_pass
}

/** Q is used by every type except Off and the low shelf (fixed slope in the coefficient math). */
private fun hasQ(t: FilterType) = t != FilterType.Off && t != FilterType.LowShelf

private fun round(x: Double, scale: Int): Double = floor(x * scale + 0.5) / scale

private const val Q_MIN = 0.1
private const val Q_MAX = 10.0
private fun qToT(q: Double): Float = (log10(q.coerceIn(Q_MIN, Q_MAX) / Q_MIN) / log10(Q_MAX / Q_MIN)).toFloat()
private fun tToQ(t: Float): Double = round(Q_MIN * (Q_MAX / Q_MIN).pow(t.toDouble()), 100)

/**
 * Glass panel for one band: type, frequency, gain and Q, each with − / + steppers and a slider.
 * Every change goes to [onChange] (the ViewModel clamps and commits it). Read-only unless [enabled].
 */
@Composable
fun BandSheet(
    index: Int,
    band: EqBand,
    enabled: Boolean,
    onChange: (EqBand) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalHorizonColors.current
    val locale = Locale.getDefault()
    GlassSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.band_title, index + 1),
                    color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.action_done),
                    color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(c.accent)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }

            Text(stringResource(R.string.band_type), color = c.muted, fontSize = 12.sp)
            FilterType.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t ->
                        val on = t == band.type
                        Text(
                            stringResource(typeLabel(t)),
                            color = if (on) c.onAccent else c.ink,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .clip(PillShape)
                                .background(if (on) c.accent else c.ink.copy(alpha = 0.06f))
                                .clickable(enabled = enabled && !on, role = Role.RadioButton) { onChange(band.copy(type = t)) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }

            val active = band.type != FilterType.Off
            Param(
                label = stringResource(R.string.band_frequency),
                value = stringResource(R.string.unit_hz, band.fc.toString()),
                enabled = enabled && active,
                onMinus = { onChange(band.copy(fc = band.fc - 1)) },
                onPlus = { onChange(band.copy(fc = band.fc + 1)) },
                slider = Axis.freqToX(band.fc.toDouble().coerceIn(Axis.MIN_HZ, Axis.MAX_HZ), 1f),
                onSlider = { onChange(band.copy(fc = Axis.xToFreq(it, 1f))) },
            )
            Param(
                label = stringResource(R.string.band_gain),
                value = stringResource(R.string.unit_db, String.format(locale, "%+.1f", band.gainDb)),
                enabled = enabled && hasGain(band.type),
                onMinus = { onChange(band.copy(gainDb = round(band.gainDb - 0.1, 10))) },
                onPlus = { onChange(band.copy(gainDb = round(band.gainDb + 0.1, 10))) },
                slider = ((band.gainDb.coerceIn(-GAIN_MAX, GAIN_MAX) + GAIN_MAX) / (2 * GAIN_MAX)).toFloat(),
                onSlider = { onChange(band.copy(gainDb = round(it * 2 * GAIN_MAX - GAIN_MAX, 10))) },
            )
            Param(
                label = stringResource(R.string.band_q),
                value = String.format(locale, "%.2f", band.q),
                enabled = enabled && hasQ(band.type),
                onMinus = { onChange(band.copy(q = round(band.q - 0.01, 100))) },
                onPlus = { onChange(band.copy(q = round(band.q + 0.01, 100))) },
                slider = qToT(band.q),
                onSlider = { onChange(band.copy(q = tToQ(it))) },
            )
        }
    }
}

private const val GAIN_MAX = 10.0

@Composable
private fun Param(
    label: String,
    value: String,
    enabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    slider: Float,
    onSlider: (Float) -> Unit,
) {
    val c = LocalHorizonColors.current
    Column(Modifier.alpha(if (enabled) 1f else 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Stepper("−", enabled, onMinus)
            Text(
                value, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Stepper("+", enabled, onPlus)
        }
        Slider(
            value = slider,
            onValueChange = onSlider,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = c.ink,
                activeTrackColor = c.ink,
                inactiveTrackColor = c.ink.copy(alpha = 0.15f),
                disabledThumbColor = c.ink,
                disabledActiveTrackColor = c.ink,
                disabledInactiveTrackColor = c.ink.copy(alpha = 0.15f),
            ),
        )
    }
}

@Composable
private fun Stepper(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalHorizonColors.current
    Box(
        Modifier
            .size(32.dp)
            .clip(PillShape)
            .background(c.ink.copy(alpha = 0.06f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
