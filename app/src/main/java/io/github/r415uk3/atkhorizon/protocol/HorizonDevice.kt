package io.github.r415uk3.atkhorizon.protocol

import kotlin.math.abs

data class DeviceSnapshot(
    val firmware: String,
    val bands: List<DeviceBand>,
    val dacOffset: Double,
    val adcOffset: Double,
    val noiseCancelling: Boolean,
)

class VerifyException(val mismatchedBands: List<Int>) : IllegalStateException("Verification failed, bands: $mismatchedBands")

/** High-level verified operations over [HorizonLink]. Never sends 0x05/0x0D/0x17/0xFF. */
class HorizonDevice(private val link: HorizonLink) {
    suspend fun readSnapshot(): DeviceSnapshot {
        val fw = Responses.firmware(link.request(Packets.readFirmware()))
        val bands = (0 until BAND_COUNT).map { Responses.band(link.request(Packets.readBand(it))) }
        val dac = Responses.offsetDb(link.request(Packets.readDacOffset()))
        val adc = Responses.offsetDb(link.request(Packets.readAdcOffset()))
        val nc = Responses.noiseCancelling(link.request(Packets.readNoiseCancelling()))
        return DeviceSnapshot(fw, bands, dac, adc, nc)
    }

    /** Live edit for dragging: 0x09 + 0x0A only, no flash save. */
    suspend fun liveBand(index: Int, band: EqBand) {
        link.write(Packets.writeBand(index, band))
        link.write(Packets.commitCoefficients())
    }

    suspend fun commitBands(bands: Map<Int, EqBand>): List<DeviceBand> {
        if (bands.isEmpty()) return emptyList()
        for ((i, b) in bands) link.write(Packets.writeBand(i, b))
        link.write(Packets.commitCoefficients())
        val dac = Responses.offsetDb(link.request(Packets.readDacOffset()))
        link.write(Packets.setDacOffset(dac))
        link.delay(1000)
        link.write(Packets.saveToFlash())
        val back = bands.keys.map { Responses.band(link.request(Packets.readBand(it))) }
        val bad = bands.entries.zip(back).filter { (e, got) ->
            got != Responses.band(Packets.writeBand(e.key, e.value))
        }.map { it.first.key }
        if (bad.isNotEmpty()) throw VerifyException(bad)
        return back
    }

    suspend fun applyPreset(bands: List<EqBand>): List<DeviceBand> =
        commitBands(bands.mapIndexed { i, b -> i to b }.toMap())

    suspend fun setDacOffset(db: Double) =
        setOffset(Packets.setDacOffset(db), Packets.readDacOffset(), db)

    suspend fun setAdcOffset(db: Double) =
        setOffset(Packets.setAdcOffset(db), Packets.readAdcOffset(), db)

    private suspend fun setOffset(write: ByteArray, read: ByteArray, db: Double) {
        link.write(write)
        link.write(Packets.saveToFlash())
        val got = Responses.offsetDb(link.request(read))
        if (abs(got - db) > 1.0 / 256) throw VerifyException(emptyList())
    }

    suspend fun setNoiseCancelling(on: Boolean) {
        link.write(Packets.setNoiseCancelling(on))
        if (Responses.noiseCancelling(link.request(Packets.readNoiseCancelling())) != on) {
            throw VerifyException(emptyList())
        }
    }

    suspend fun softReset(): DeviceSnapshot {
        applyPreset(EqBand.DEFAULTS)
        setDacOffset(0.0)
        setAdcOffset(0.0)
        return readSnapshot()
    }

    private companion object {
        const val BAND_COUNT = 8
    }
}
