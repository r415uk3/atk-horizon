package io.github.r415uk3.atkhorizon.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import java.util.Locale
import kotlin.math.floor

internal const val OFFSET_MIN_DB = -10.0
internal const val OFFSET_MAX_DB = 10.0
internal const val OFFSET_STEP_DB = 0.5

/** Snaps [db] to the 0.5 dB grid inside −10…+10 dB. */
internal fun snapOffset(db: Double): Double =
    (floor(db.coerceIn(OFFSET_MIN_DB, OFFSET_MAX_DB) / OFFSET_STEP_DB + 0.5) * OFFSET_STEP_DB).let { if (it == 0.0) 0.0 else it }

/**
 * A labelled gain-offset slider. While dragging it shows the local value; the value is sent to
 * [onCommit] on release, after which it follows [value] (the ViewModel state). A null [value]
 * (implausible device value) shows "—" with the thumb at 0 dB.
 */
@Composable
internal fun GainSlider(
    label: String,
    value: Double?,
    enabled: Boolean,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalHorizonColors.current
    var drag by remember { mutableStateOf<Double?>(null) }
    // A gesture cancelled by disabling may never report its end; drop the stale local value.
    LaunchedEffect(enabled) { if (!enabled) drag = null }
    val shown = drag ?: value
    Column(modifier.alpha(if (enabled) 1f else 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                shown?.let { stringResource(R.string.unit_db, String.format(Locale.getDefault(), "%+.1f", it)) } ?: "—",
                color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Slider(
            value = (shown ?: 0.0).toFloat().coerceIn(OFFSET_MIN_DB.toFloat(), OFFSET_MAX_DB.toFloat()),
            onValueChange = { drag = snapOffset(it.toDouble()) },
            onValueChangeFinished = {
                drag?.let(onCommit)
                drag = null
            },
            valueRange = OFFSET_MIN_DB.toFloat()..OFFSET_MAX_DB.toFloat(),
            steps = ((OFFSET_MAX_DB - OFFSET_MIN_DB) / OFFSET_STEP_DB).toInt() - 1,
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
