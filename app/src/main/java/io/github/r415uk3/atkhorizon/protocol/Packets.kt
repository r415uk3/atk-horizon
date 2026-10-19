package io.github.r415uk3.atkhorizon.protocol

import kotlin.math.floor

const val REPORT_ID: Byte = 0x4B

private const val PAYLOAD = 63

private fun pkt(vararg head: Int): ByteArray {
    val a = ByteArray(PAYLOAD)
    head.forEachIndexed { i, v -> a[i] = v.toByte() }
    return a
}

private fun putU16(a: ByteArray, at: Int, v: Int) {
    a[at] = (v and 0xFF).toByte()
    a[at + 1] = ((v shr 8) and 0xFF).toByte()
}

private fun u16(a: ByteArray, at: Int) = (a[at].toInt() and 0xFF) or ((a[at + 1].toInt() and 0xFF) shl 8)

private fun s16(a: ByteArray, at: Int) = u16(a, at).toShort().toInt()

/** JS Math.round(x * 256) */
private fun q88(x: Double) = floor(x * 256 + 0.5).toInt()

private fun round1(x: Double) = floor(x * 10 + 0.5) / 10

private fun round2(x: Double) = floor(x * 100 + 0.5) / 100

object Packets {
    fun writeBand(index: Int, b: EqBand): ByteArray {
        val a = pkt(0x01, 0x09, 0x18, 0x00, index)
        Biquad.coefficientBytes(b.type, b.fc, b.q, b.gainDb).copyInto(a, 7)
        putU16(a, 27, b.fc)
        putU16(a, 29, q88(b.q))
        putU16(a, 31, q88(b.gainDb))
        a[33] = b.type.code.toByte()
        return a
    }

    fun readBand(index: Int): ByteArray = pkt(0x80, 0x09, 0x02, 0x00, index)
    fun commitCoefficients(): ByteArray = pkt(0x01, 0x0A, 0x04, 0x00, 0x00, 0xFF, 0xFF)
    fun saveToFlash(): ByteArray = pkt(0x01, 0x01)

    private fun offset(cmd: Int, db: Double): ByteArray =
        pkt(0x01, cmd, 0x02).also { putU16(it, 3, q88(db)) }

    fun setDacOffset(db: Double): ByteArray = offset(0x03, db)
    fun readDacOffset(): ByteArray = pkt(0x80, 0x03, 0x00)
    fun setAdcOffset(db: Double): ByteArray = offset(0x02, db)
    fun readAdcOffset(): ByteArray = pkt(0x80, 0x02, 0x00)
    fun setNoiseCancelling(on: Boolean): ByteArray = pkt(0x01, 0x1B, 0x01, if (on) 1 else 0)
    fun readNoiseCancelling(): ByteArray = pkt(0x80, 0x1B, 0x00)
    fun readFirmware(): ByteArray = pkt(0x80, 0x0C, 0x00)
}

object Responses {
    fun band(r: ByteArray): DeviceBand {
        val qRaw = u16(r, 29)
        val gainRaw = s16(r, 31)
        return DeviceBand(
            index = r[4].toInt() and 0xFF,
            band = EqBand(FilterType.fromCode(r[33].toInt() and 0xFF), u16(r, 27), round2(qRaw / 256.0), round1(gainRaw / 256.0)),
            qRaw = qRaw,
            gainRaw = gainRaw,
            coeff = r.copyOfRange(7, 27),
        )
    }

    fun offsetDb(r: ByteArray): Double = s16(r, 3) / 256.0

    fun noiseCancelling(r: ByteArray): Boolean = r[3].toInt() != 0

    fun firmware(r: ByteArray): String {
        val sb = StringBuilder()
        for (i in 3 until r.size) {
            val c = r[i].toInt() and 0xFF
            if (c < 0x20 || c > 0x7E) break
            sb.append(c.toChar())
        }
        return sb.toString()
    }
}
