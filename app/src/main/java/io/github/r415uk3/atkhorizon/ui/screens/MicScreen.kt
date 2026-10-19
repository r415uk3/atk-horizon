package io.github.r415uk3.atkhorizon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.ui.ConnState
import io.github.r415uk3.atkhorizon.ui.HorizonViewModel
import io.github.r415uk3.atkhorizon.ui.UiState
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

/** AI noise-cancelling switch and the mic gain offset. Editable only when the device is Ready. */
@Composable
fun MicScreen(state: UiState, vm: HorizonViewModel, modifier: Modifier = Modifier) {
    val c = LocalHorizonColors.current
    val ready = state.conn == ConnState.Ready
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.mic_noise_cancelling),
                        color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    if (state.busy) CircularProgressIndicator(Modifier.size(14.dp), color = c.muted, strokeWidth = 2.dp)
                    Switch(
                        checked = state.noiseCancelling,
                        onCheckedChange = vm::setNoiseCancelling,
                        enabled = ready,
                        modifier = Modifier.padding(start = 10.dp),
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = c.accent, checkedThumbColor = c.onAccent,
                            uncheckedTrackColor = c.ink.copy(alpha = 0.15f), uncheckedThumbColor = c.ink,
                            uncheckedBorderColor = c.ink.copy(alpha = 0.3f),
                        ),
                    )
                }
                GainSlider(
                    label = stringResource(R.string.mic_gain_offset),
                    value = state.adcOffset,
                    enabled = ready,
                    onCommit = vm::setAdcOffset,
                )
            }
        }
    }
}
