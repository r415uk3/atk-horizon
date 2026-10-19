package io.github.r415uk3.atkhorizon.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

data class LogEntry(val dir: Int, val cmd: Int, val atMs: Long)

/** Test-only simulator of the ATK Horizon headset. Replies synchronously from [send]. */
class FakeHorizon(private val clock: () -> Long = { 0L }) : HidTransport {
    private fun defaultBands() = EqBand.DEFAULTS.mapIndexed { i, b -> Responses.band(Packets.writeBand(i, b)) }
    private fun copy(b: DeviceBand) = b.copy(coeff = b.coeff.copyOf())

    val bands: MutableList<DeviceBand> = defaultBands().toMutableList()
    var flashBands: List<DeviceBand> = bands.map(::copy)
        private set
    var dacOffset = 0.0
    var adcOffset = 0.0
    var flashDacOffset = 0.0
        private set
    var flashAdcOffset = 0.0
        private set
    var nc = false
    var firmware = "0.9"

    val log = mutableListOf<LogEntry>()

    var dropWritesFor: Set<Int> = emptySet()
    var unplugAfter: Int? = null
    var failIfForbidden = true

    private var sends = 0
    private val shared = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val end = ByteArray(0)

    override val incoming: Flow<ByteArray> = flow {
        shared.transformWhile { if (it === end) false else { emit(it); true } }.collect { emit(it) }
    }

    override suspend fun send(payload: ByteArray) {
        sends++
        val limit = unplugAfter
        if (limit != null && sends > limit) {
            shared.emit(end)
            throw DeviceGoneException()
        }
        val dir = payload[0].toInt() and 0xFF
        val cmd = payload[1].toInt() and 0xFF
        log += LogEntry(dir, cmd, clock())
        if (failIfForbidden && cmd in FORBIDDEN) throw AssertionError("forbidden command 0x%02X sent".format(cmd))
        when (dir) {
            0x01 -> write(cmd, payload)
            0x80 -> read(cmd, payload)?.let { shared.emit(it) }
        }
    }

    private fun write(cmd: Int, p: ByteArray) {
        when (cmd) {
            0x09 -> {
                val b = Responses.band(p)
                if (b.index in bands.indices && b.index !in dropWritesFor) bands[b.index] = b
            }
            0x03 -> dacOffset = Responses.offsetDb(p)
            0x02 -> adcOffset = Responses.offsetDb(p)
            0x1B -> nc = Responses.noiseCancelling(p)
            0x01 -> {
                flashBands = bands.map(::copy)
                flashDacOffset = dacOffset
                flashAdcOffset = adcOffset
            }
            // 0x0A and unknown commands: log only.
        }
    }

    private fun read(cmd: Int, req: ByteArray): ByteArray? {
        val r = ByteArray(63)
        r[0] = req[0]
        r[1] = req[1]
        when (cmd) {
            0x09 -> {
                val b = bands.getOrNull(req[4].toInt() and 0xFF) ?: return null
                // Same layout as the write packet; payload length byte matches Packets.writeBand.
                val w = Packets.writeBand(b.index, b.band)
                w.copyInto(r)
                r[0] = req[0]
                b.coeff.copyInto(r, 7)
                r[29] = (b.qRaw and 0xFF).toByte(); r[30] = ((b.qRaw shr 8) and 0xFF).toByte()
                r[31] = (b.gainRaw and 0xFF).toByte(); r[32] = ((b.gainRaw shr 8) and 0xFF).toByte()
            }
            0x03 -> putOffset(r, dacOffset)
            0x02 -> putOffset(r, adcOffset)
            0x1B -> r[3] = if (nc) 1 else 0
            0x0C -> firmware.take(r.size - 3).forEachIndexed { i, c -> r[3 + i] = c.code.toByte() }
            else -> return null
        }
        return r
    }

    private fun putOffset(r: ByteArray, db: Double) {
        val v = Math.floor(db * 256 + 0.5).toInt()
        r[2] = 0x02
        r[3] = (v and 0xFF).toByte()
        r[4] = ((v shr 8) and 0xFF).toByte()
    }

    override fun close() {}

    private companion object {
        val FORBIDDEN = setOf(0x05, 0x0D, 0x17, 0xFF)
    }
}
