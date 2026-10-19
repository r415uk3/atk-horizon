package io.github.r415uk3.atkhorizon.protocol

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    private val band = EqBand(FilterType.Peak, 1500, 1.2, 2.0)

    @Test
    fun matchingCoefficientsAreReportedAsMatch() {
        val line = Diagnostics.bandLine(Responses.band(Packets.writeBand(4, band)))
        assertTrue(line, line.startsWith("band 4 type=Peak(2) fc=1500 q=1.2 "))
        assertTrue(line, " coeff MATCH " in line)
        val hex = Packets.writeBand(4, band).copyOfRange(7, 27).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        assertTrue(line, "dev=$hex" in line)
        assertTrue(line, "calc=$hex" in line)
    }

    @Test
    fun differingCoefficientsAreReportedAsMismatchWithBothHex() {
        val raw = Packets.writeBand(4, band).also { it[7] = (it[7] + 1).toByte() }
        val line = Diagnostics.bandLine(Responses.band(raw))
        assertTrue(line, " coeff MISMATCH " in line)
        val dev = raw.copyOfRange(7, 27).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        val calc = Biquad.coefficientBytes(band.type, band.fc, band.q, band.gainDb).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        assertTrue(line, "dev=$dev" in line)
        assertTrue(line, "calc=$calc" in line)
    }

    @Test
    fun unstableParametersAreReportedWithoutThrowing() {
        val raw = Packets.writeBand(3, EqBand(FilterType.Peak, 20, 5.0, 10.0)).also { it[33] = FilterType.HighShelf.code.toByte() }
        val line = Diagnostics.bandLine(Responses.band(raw))
        assertTrue(line, " coeff UNSTABLE " in line)
    }

    @Test
    fun snapshotLogsEveryBandWhenEnabled() = runTest {
        val lines = mutableListOf<String>()
        val dev = HorizonDevice(HorizonLink(FakeHorizon(), backgroundScope), debugLog = { lines += it })
        dev.readSnapshot()
        assertEquals(9, lines.size)
        assertEquals((0 until 8).map { "band $it " }, lines.take(8).map { it.take(7) })
        assertTrue(lines.take(8).all { " coeff MATCH " in it })
        assertTrue(lines[8], lines[8].startsWith("snapshot firmware=0.9 "))
    }
}
