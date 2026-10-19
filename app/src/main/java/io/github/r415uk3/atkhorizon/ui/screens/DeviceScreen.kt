package io.github.r415uk3.atkhorizon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.ui.ConnState
import io.github.r415uk3.atkhorizon.ui.HorizonViewModel
import io.github.r415uk3.atkhorizon.ui.UiState
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.glass.PillShape
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors
import io.github.r415uk3.atkhorizon.usb.UsbIds

/** "0x373B / 0x120C". */
internal fun vidPidText(): String = "0x%04X / 0x%04X".format(UsbIds.VENDOR_ATK, UsbIds.PRODUCT_HORIZON)

/** Read-only device info, the playback gain offset and a confirmed soft reset. */
@Composable
fun DeviceScreen(state: UiState, vm: HorizonViewModel, modifier: Modifier = Modifier) {
    val c = LocalHorizonColors.current
    val ready = state.conn == ConnState.Ready
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(stringResource(R.string.device_model), stringResource(R.string.device_model_value))
                InfoRow(stringResource(R.string.device_vid_pid), vidPidText())
                InfoRow(stringResource(R.string.device_firmware), state.firmware ?: "—")
            }
        }
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GainSlider(
                    label = stringResource(R.string.device_playback_gain_offset),
                    value = state.dacOffset,
                    enabled = ready,
                    onCommit = vm::setDacOffset,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.device_reset),
                        color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .alpha(if (ready && !state.busy) 1f else 0.45f)
                            .clip(PillShape)
                            .background(c.accent)
                            .clickable(enabled = ready && !state.busy, role = Role.Button) { confirming = true }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    if (state.busy) {
                        CircularProgressIndicator(
                            Modifier.padding(start = 12.dp).size(16.dp), color = c.muted, strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; vm.softReset() }) {
                    Text(stringResource(R.string.reset_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalHorizonColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
