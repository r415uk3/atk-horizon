package io.github.r415uk3.atkhorizon.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketsTest {
    private fun bytes(vararg v: Int) = ByteArray(63).also { a -> v.forEachIndexed { i, x -> a[i] = x.toByte() } }
    private val b = EqBand(FilterType.Peak, 1000, 1.0, 3.0)

    @Test fun writeBandLayout() {
        val p = Packets.writeBand(3, b)
        val exp = bytes(0x01, 0x09, 0x18, 0x00, 0x03)
        Biquad.coefficientBytes(FilterType.Peak, 1000, 1.0, 3.0).copyInto(exp, 7)
        intArrayOf(0xe8, 0x03, 0x00, 0x01, 0x00, 0x03, 0x02).forEachIndexed { i, x -> exp[27 + i] = x.toByte() }
        assertEquals(63, p.size)
        assertArrayEquals(exp, p)
        assertEquals("6d fe b4 40", p.copyOfRange(7, 11).joinToString(" ") { "%02x".format(it) })
    }

    @Test fun writeBandNegativeGain() {
        val p = Packets.writeBand(0, EqBand(FilterType.Peak, 60, 0.7, -4.0))
        assertEquals(0x00, p[31].toInt()); assertEquals(0xfc, p[32].toInt() and 0xFF)
    }

    @Test fun commitLayout() = assertArrayEquals(bytes(0x01, 0x0a, 0x04, 0x00, 0x00, 0xff, 0xff), Packets.commitCoefficients())
    @Test fun saveLayout() = assertArrayEquals(bytes(0x01, 0x01), Packets.saveToFlash())
    @Test fun dacOffsetLayout() = assertArrayEquals(bytes(0x01, 0x03, 0x02, 0x80, 0xfe), Packets.setDacOffset(-1.5))
    @Test fun adcOffsetLayout() = assertArrayEquals(bytes(0x01, 0x02, 0x02, 0x00, 0x02), Packets.setAdcOffset(2.0))
    @Test fun readOffsetLayout() {
        assertArrayEquals(bytes(0x80, 0x03, 0x00), Packets.readDacOffset())
        assertArrayEquals(bytes(0x80, 0x02, 0x00), Packets.readAdcOffset())
    }
    @Test fun readBandLayout() = assertArrayEquals(bytes(0x80, 0x09, 0x02, 0x00, 0x05), Packets.readBand(5))
    @Test fun ncLayout() {
        assertArrayEquals(bytes(0x01, 0x1b, 0x01, 0x01), Packets.setNoiseCancelling(true))
        assertArrayEquals(bytes(0x01, 0x1b, 0x01, 0x00), Packets.setNoiseCancelling(false))
        assertArrayEquals(bytes(0x80, 0x1b, 0x00), Packets.readNoiseCancelling())
    }
    @Test fun firmwareLayout() = assertArrayEquals(bytes(0x80, 0x0c, 0x00), Packets.readFirmware())

    @Test fun parseBandRoundTrip() {
        val w = Packets.writeBand(3, b)
        val r = Responses.band(w.copyOf().also { it[0] = 0x80.toByte() })
        assertEquals(3, r.index)
        assertEquals(b, r.band)
        assertEquals(256, r.qRaw)
        assertEquals(768, r.gainRaw)
        assertArrayEquals(w.copyOfRange(7, 27), r.coeff)
        assertEquals(r, Responses.band(w))
    }

    @Test fun parseBandUnknownTypeMapsToDisabled() {
        val w = Packets.writeBand(0, b); w[33] = 9
        assertEquals(FilterType.Off, Responses.band(w).band.type)
    }

    @Test fun parseNegativeGain() {
        val r = Responses.band(Packets.writeBand(0, EqBand(FilterType.Peak, 60, 0.7, -4.0)))
        assertEquals(-4.0, r.band.gainDb, 0.0); assertEquals(0.7, r.band.q, 0.0)
    }

    @Test fun parseFirmware() = assertEquals("0.9", Responses.firmware(bytes(0x80, 0x0c, 0x03, '0'.code, '.'.code, '9'.code)))
    @Test fun parseOffset() = assertEquals(-1.5, Responses.offsetDb(bytes(0x80, 0x03, 0x02, 0x80, 0xfe)), 0.0)
    @Test fun parseNc() {
        assertTrue(Responses.noiseCancelling(bytes(0x80, 0x1b, 0x01, 0x01)))
        assertEquals(false, Responses.noiseCancelling(bytes(0x80, 0x1b, 0x01, 0x00)))
    }

    @Test fun clampedLimits() =
        assertEquals(EqBand(FilterType.Peak, 20, 10.0, 10.0), EqBand(FilterType.Peak, 5, 20.0, 15.04).clamped())

    @Test fun forbiddenCommandsUnreachable() {
        val allowed = setOf(0x01, 0x02, 0x03, 0x09, 0x0A, 0x0C, 0x1B)
        val fns = Packets::class.java.declaredMethods.filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
        assertTrue(fns.size >= 11)
        for (m in fns) {
            val args = m.parameterTypes.map {
                when (it) {
                    Int::class.javaPrimitiveType -> 2
                    Double::class.javaPrimitiveType -> 1.5
                    Boolean::class.javaPrimitiveType -> true
                    EqBand::class.java -> b
                    else -> error("unexpected param ${it.name}")
                }
            }.toTypedArray()
            val out = m.invoke(Packets, *args) as ByteArray
            assertEquals(63, out.size)
            val cmd = out[1].toInt() and 0xFF
            assertTrue("${m.name} cmd=$cmd", cmd in allowed)
        }
    }
}
