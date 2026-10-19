package io.github.r415uk3.atkhorizon.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

data class LogEntry(val dir: Int, val cmd: Int, val atMs: Long)

/** Test-only simulator of the ATK Horizon headset. Replies synchronously from [send]. */
class FakeHorizon(private val clock: () -> Long = { 0L }) : HidTransport {
    // Raw 63-byte write packets per band index, stored exactly as received.
    private val rawBands: MutableList<ByteArray> =
        EqBand.DEFAULTS.mapIndexed { i, b -> Packets.writeBand(i, b) }.toMutableList()
    private var flashRaw: List<ByteArray> = rawBands.map { it.copyOf() }

    /** Parsed view of the stored bands, for test assertions. */
    val bands: List<DeviceBand> get() = rawBands.map(Responses::band)
    val flashBands: List<DeviceBand> get() = flashRaw.map(Responses::band)
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
    var dropOffsetWrites = false
    var dropNcWrites = false

    /**
     * Band index → number of 0x09 read replies that carry the next band's data (and index) instead,
     * like a late reply to an earlier request being taken for this one.
     */
    val wrongIndexReplies = mutableMapOf<Int, Int>()

    /** Added (in 1/256 dB units) to every stored offset write, to simulate a device that applies a slightly different value. */
    var offsetPerturbRaw = 0

    private var sends = 0
    @Volatile private var unplugged = false
    private val shared = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val end = ByteArray(0)

    override val incoming: Flow<ByteArray> = flow {
        if (unplugged) return@flow
        shared.transformWhile { if (it === end) false else { emit(it); true } }.collect { emit(it) }
    }

    override suspend fun send(payload: ByteArray) {
        sends++
        val limit = unplugAfter
        if (limit != null && sends > limit) {
            unplugged = true
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
                val index = p[4].toInt() and 0xFF
                if (index in rawBands.indices && index !in dropWritesFor) rawBands[index] = p.copyOf()
            }
            0x03 -> if (!dropOffsetWrites) dacOffset = Responses.offsetDb(p) + offsetPerturbRaw / 256.0
            0x02 -> if (!dropOffsetWrites) adcOffset = Responses.offsetDb(p) + offsetPerturbRaw / 256.0
            0x1B -> if (!dropNcWrites) nc = Responses.noiseCancelling(p)
            0x01 -> {
                flashRaw = rawBands.map { it.copyOf() }
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
                var index = req[4].toInt() and 0xFF
                val wrong = wrongIndexReplies[index] ?: 0
                if (wrong > 0) {
                    wrongIndexReplies[index] = wrong - 1
                    index = (index + 1) % rawBands.size
                }
                val raw = rawBands.getOrNull(index) ?: return null
                raw.copyInto(r)
                r[0] = req[0]
                r[1] = req[1]
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
