package io.github.r415uk3.atkhorizon.protocol

import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DeviceSnapshot(
    val firmware: String,
    val bands: List<DeviceBand>,
    val dacOffset: Double,
    val adcOffset: Double,
    val noiseCancelling: Boolean,
)

class VerifyException(val mismatchedBands: List<Int>) : IllegalStateException("Verification failed, bands: $mismatchedBands")

/** Gain offsets outside ±[OFFSET_LIMIT_DB] are the UI range limit and treated as implausible reads. */
const val OFFSET_LIMIT_DB = 10.0

fun plausibleOffset(db: Double): Boolean = db in -OFFSET_LIMIT_DB..OFFSET_LIMIT_DB

/**
 * The device reported a value that is implausible (e.g. a DAC offset outside ±10 dB: the 0x03 read
 * parse is unverified on hardware). Thrown before anything is written, so the wild value is never
 * written back.
 */
class SuspiciousDeviceValueException(what: String, val value: Double) :
    IllegalStateException("Implausible $what read from the device: $value")

/**
 * High-level verified operations over [HorizonLink]. Never sends 0x05/0x0D/0x17/0xFF.
 *
 * Every public operation holds this device's lock for its whole multi-packet sequence, so two
 * callers sharing the device (e.g. an old ViewModel's commit and a new one's snapshot read) never
 * interleave on the wire; waiting callers run in call order.
 */
class HorizonDevice(private val link: HorizonLink) {
    private val lock = Mutex()

    suspend fun readSnapshot(): DeviceSnapshot = lock.withLock { snapshot() }

    /** Live edit for dragging: 0x09 + 0x0A only, no flash save. */
    suspend fun liveBand(index: Int, band: EqBand) = lock.withLock {
        link.write(Packets.writeBand(index, band))
        link.write(Packets.commitCoefficients())
    }

    /**
     * The flash-save scheme: 0x09 per band → 0x0A → 0x03 (current DAC offset) → 1 s → 0x01, then
     * read back and verify. The current DAC offset is read first and must be plausible
     * ([SuspiciousDeviceValueException] otherwise, before any write).
     */
    suspend fun commitBands(bands: Map<Int, EqBand>): List<DeviceBand> {
        require(bands.keys.all { it in 0 until BAND_COUNT }) { "band indices ${bands.keys} outside 0..7" }
        return lock.withLock { commit(bands, dacOverride = null) }
    }

    suspend fun applyPreset(bands: List<EqBand>): List<DeviceBand> {
        require(bands.size == BAND_COUNT) { "a preset has $BAND_COUNT bands, got ${bands.size}" }
        return commitBands(bands.mapIndexed { i, b -> i to b }.toMap())
    }

    suspend fun setDacOffset(db: Double) = lock.withLock { setDac(db) }

    suspend fun setAdcOffset(db: Double) = lock.withLock { setAdc(db) }

    suspend fun setNoiseCancelling(on: Boolean) = lock.withLock {
        link.write(Packets.setNoiseCancelling(on))
        if (Responses.noiseCancelling(link.request(Packets.readNoiseCancelling())) != on) {
            throw VerifyException(emptyList())
        }
    }

    /**
     * Default preset, DAC and ADC offsets 0, saved and re-read, as one sequence. The preset is
     * written with a 0 dB DAC offset (it is zeroed right after anyway), so a reset also recovers
     * from an implausible one.
     */
    suspend fun softReset(): DeviceSnapshot = lock.withLock {
        commit(EqBand.DEFAULTS.mapIndexed { i, b -> i to b }.toMap(), dacOverride = 0.0)
        setDac(0.0)
        setAdc(0.0)
        snapshot()
    }

    // ---- Unlocked steps; callers hold [lock]. ----

    private suspend fun snapshot(): DeviceSnapshot {
        val fw = Responses.firmware(link.request(Packets.readFirmware()))
        val bands = (0 until BAND_COUNT).map { readBand(it) }
        val dac = Responses.offsetDb(link.request(Packets.readDacOffset()))
        val adc = Responses.offsetDb(link.request(Packets.readAdcOffset()))
        val nc = Responses.noiseCancelling(link.request(Packets.readNoiseCancelling()))
        return DeviceSnapshot(fw, bands, dac, adc, nc)
    }

    /** [dacOverride]: DAC offset to write instead of the device's current one (no read). */
    private suspend fun commit(bands: Map<Int, EqBand>, dacOverride: Double?): List<DeviceBand> {
        if (bands.isEmpty()) return emptyList()
        val dac = dacOverride ?: Responses.offsetDb(link.request(Packets.readDacOffset())).also {
            if (!plausibleOffset(it)) throw SuspiciousDeviceValueException("DAC offset", it)
        }
        for ((i, b) in bands) link.write(Packets.writeBand(i, b))
        link.write(Packets.commitCoefficients())
        link.write(Packets.setDacOffset(dac))
        link.delay(1000)
        link.write(Packets.saveToFlash())
        val back = bands.keys.map { readBand(it) }
        val bad = bands.entries.zip(back).filter { (e, got) ->
            got != Responses.band(Packets.writeBand(e.key, e.value))
        }.map { it.first.key }
        if (bad.isNotEmpty()) throw VerifyException(bad)
        return back
    }

    /**
     * Reads band [i]. A reply carrying another band's index (e.g. a late reply to an earlier,
     * retried request) is never taken for band [i]: it is re-requested once, then [VerifyException].
     */
    private suspend fun readBand(i: Int): DeviceBand {
        repeat(2) {
            val b = Responses.band(link.request(Packets.readBand(i)))
            if (b.index == i) return b
        }
        throw VerifyException(listOf(i))
    }

    private suspend fun setDac(db: Double) = setOffset(Packets.setDacOffset(db), Packets.readDacOffset(), db)

    private suspend fun setAdc(db: Double) = setOffset(Packets.setAdcOffset(db), Packets.readAdcOffset(), db)

    private suspend fun setOffset(write: ByteArray, read: ByteArray, db: Double) {
        link.write(write)
        link.write(Packets.saveToFlash())
        val got = Responses.offsetDb(link.request(read))
        if (abs(got - db) > 1.0 / 256) throw VerifyException(emptyList())
    }

    private companion object {
        const val BAND_COUNT = 8
    }
}
