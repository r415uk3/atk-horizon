package io.github.r415uk3.atkhorizon

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.r415uk3.atkhorizon.data.BuiltInPresets
import io.github.r415uk3.atkhorizon.protocol.Biquad
import io.github.r415uk3.atkhorizon.protocol.DeviceBand
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import io.github.r415uk3.atkhorizon.protocol.HorizonDevice
import io.github.r415uk3.atkhorizon.usb.HorizonConnector
import io.github.r415uk3.atkhorizon.usb.UsbState
import io.github.r415uk3.atkhorizon.usb.toHex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "Horizon"

class MainActivity : ComponentActivity() {
    private lateinit var connector: HorizonConnector
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connector = HorizonConnector(this, lifecycleScope)
        setContent {
            MaterialTheme {
                val state by connector.state.collectAsState()
                DebugPanel(state)
            }
        }
    }

    // Temporary hardware-session panel (Task 7); replaced by the real UI in Task 9.
    @androidx.compose.runtime.Composable
    private fun DebugPanel(state: UsbState) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ATK Horizon debug")
            Text("State: ${state::class.simpleName}" + ((state as? UsbState.Unsupported)?.let { " pid=0x%04x".format(it.pid) } ?: ""))
            Button(onClick = ::connect) { Text("Connect / permission") }
            Button(onClick = { withDevice("readSnapshot") { readSnapshot(it) } }) { Text("Read snapshot") }
            Button(onClick = { withDevice("applyCs2") { applyCs2(it) } }) { Text("Apply CS2") }
            Button(onClick = { withDevice("crossCheck") { crossCheck(it) } }) { Text("Coefficient cross-check") }
            Button(onClick = { withDevice("liveBurst") { liveBurst(it) } }) { Text("Live-write burst (5 s)") }
            Button(onClick = { Log.i(TAG, "release"); connector.release() }) { Text("Release") }
        }
    }

    private fun connect() {
        val s = connector.state.value
        Log.i(TAG, "connect: state=$s")
        if (s is UsbState.NeedsPermission || s is UsbState.PermissionDenied) connector.requestPermission()
        connector.open()
    }

    private fun withDevice(name: String, block: suspend (HorizonDevice) -> Unit) {
        val device = (connector.state.value as? UsbState.Connected)?.device
        if (device == null) { Log.w(TAG, "$name: not connected (${connector.state.value})"); return }
        if (job?.isActive == true) { Log.w(TAG, "$name: another action is running"); return }
        job = lifecycleScope.launch {
            Log.i(TAG, "$name: start")
            try {
                block(device)
                Log.i(TAG, "$name: done")
            } catch (e: Exception) {
                Log.e(TAG, "$name: failed", e)
            }
        }
    }

    private fun logBand(b: DeviceBand) = Log.i(
        TAG,
        "band ${b.index}: ${b.band.type} fc=${b.band.fc} q=${b.band.q} gain=${b.band.gainDb} " +
            "qRaw=${b.qRaw} gainRaw=${b.gainRaw} coeff=${b.coeff.toHex()}",
    )

    private suspend fun readSnapshot(d: HorizonDevice) {
        val s = d.readSnapshot()
        Log.i(TAG, "firmware=\"${s.firmware}\" dacOffset=${s.dacOffset} adcOffset=${s.adcOffset} nc=${s.noiseCancelling} bands=${s.bands.size}")
        s.bands.forEach(::logBand)
    }

    private suspend fun applyCs2(d: HorizonDevice) {
        val cs2 = BuiltInPresets.all.first { it.id == "cs2" }
        d.applyPreset(cs2.bands).forEach(::logBand)
    }

    private suspend fun crossCheck(d: HorizonDevice) {
        val bands = d.readSnapshot().bands
        var ok = 0
        for (b in bands) {
            logBand(b)
            val fromParams = runCatching { Biquad.coefficientBytes(b.band.type, b.band.fc, b.band.q, b.band.gainDb) }
            val fromRaw = runCatching { Biquad.coefficientBytes(b.band.type, b.band.fc, b.qRaw / 256.0, b.gainRaw / 256.0) }
            val match = fromParams.getOrNull()?.contentEquals(b.coeff) == true
            val rawMatch = fromRaw.getOrNull()?.contentEquals(b.coeff) == true
            if (match) ok++
            Log.i(
                TAG,
                "band ${b.index}: ${if (match) "MATCH" else "MISMATCH"} (raw q/gain: ${if (rawMatch) "match" else "mismatch"}) " +
                    "expected=${fromParams.getOrNull()?.toHex() ?: fromParams.exceptionOrNull()} " +
                    "expectedRaw=${fromRaw.getOrNull()?.toHex() ?: fromRaw.exceptionOrNull()}",
            )
        }
        Log.i(TAG, "cross-check: $ok/${bands.size} bands match")
    }

    private suspend fun liveBurst(d: HorizonDevice) {
        val base = EqBand.DEFAULTS[3]
        var errors = 0
        var writes = 0
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 5000) {
            val t = (System.currentTimeMillis() - start) / 5000.0
            val gain = Math.floor((-3.0 + 6.0 * t) * 10 + 0.5) / 10
            try {
                d.liveBand(3, EqBand(FilterType.Peak, base.fc, base.q, gain))
                writes++
            } catch (e: Exception) {
                errors++
                Log.e(TAG, "liveBand failed", e)
            }
            delay(50)
        }
        Log.i(TAG, "live burst: $writes writes, $errors errors in ${System.currentTimeMillis() - start} ms; checking device still responds")
        readSnapshot(d)
        Log.i(TAG, "live burst left band 3 live-edited and unsaved; replug or apply a preset to restore")
    }
}
