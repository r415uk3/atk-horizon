package io.github.r415uk3.atkhorizon.protocol

/**
 * Debug-build log lines for the hardware session (docs/hardware-notes.md): each band read from the
 * device, cross-checked against our port of the Hub coefficient math.
 */
object Diagnostics {
    /**
     * `band i type=… fc=… q=… gain=… coeff MATCH|MISMATCH|UNSTABLE dev=<hex> calc=<hex>`: whether
     * the device's coefficient bytes equal [Biquad.coefficientBytes] for the band's own parameters.
     */
    fun bandLine(d: DeviceBand): String {
        val b = d.band
        val calc = try {
            Biquad.coefficientBytes(b.type, b.fc, b.q, b.gainDb)
        } catch (_: UnstableFilterException) {
            null
        }
        val verdict = when {
            calc == null -> "UNSTABLE"
            calc.contentEquals(d.coeff) -> "MATCH"
            else -> "MISMATCH"
        }
        return "band ${d.index} type=${b.type}(${b.type.code}) fc=${b.fc} q=${b.q} (raw ${d.qRaw}) " +
            "gain=${b.gainDb} (raw ${d.gainRaw}) coeff $verdict dev=${hex(d.coeff)} calc=${calc?.let(::hex) ?: "-"}"
    }

    fun snapshotLine(s: DeviceSnapshot): String =
        "snapshot firmware=${s.firmware} dac=${s.dacOffset} dB (raw ${(s.dacOffset * 256).toInt()}) " +
            "adc=${s.adcOffset} dB (raw ${(s.adcOffset * 256).toInt()}) nc=${s.noiseCancelling}"

    private fun hex(a: ByteArray) = a.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
}
