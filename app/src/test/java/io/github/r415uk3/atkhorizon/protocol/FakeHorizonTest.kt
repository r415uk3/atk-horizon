package io.github.r415uk3.atkhorizon.protocol

import java.util.concurrent.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeHorizonTest {
    private val custom = EqBand(FilterType.Peak, 1234, 1.5, 3.5)

    @Test
    fun readsWhatWasWritten() = runTest {
        val dev = FakeHorizon()
        val link = HorizonLink(dev, backgroundScope)
        link.write(Packets.writeBand(2, custom))
        val got = Responses.band(link.request(Packets.readBand(2)))
        assertEquals(Responses.band(Packets.writeBand(2, custom)), got)
        assertEquals(got, dev.bands[2])
        assertEquals(2, got.index)
    }

    @Test
    fun saveSnapshotsFlash() = runTest {
        val dev = FakeHorizon()
        val link = HorizonLink(dev, backgroundScope)
        link.write(Packets.writeBand(0, custom))
        link.write(Packets.setDacOffset(-2.5))
        link.write(Packets.setAdcOffset(1.0))
        assertNotEquals(dev.bands[0], dev.flashBands[0])
        assertEquals(0.0, dev.flashDacOffset, 0.0)
        link.write(Packets.saveToFlash())
        assertEquals(dev.bands, dev.flashBands)
        assertEquals(-2.5, dev.flashDacOffset, 0.0)
        assertEquals(1.0, dev.flashAdcOffset, 0.0)
    }

    @Test
    fun offsetsNcAndFirmwareRoundTrip() = runTest {
        val dev = FakeHorizon()
        val link = HorizonLink(dev, backgroundScope)
        link.write(Packets.setDacOffset(-2.5))
        link.write(Packets.setAdcOffset(3.25))
        link.write(Packets.setNoiseCancelling(true))
        assertEquals(-2.5, Responses.offsetDb(link.request(Packets.readDacOffset())), 0.0)
        assertEquals(3.25, Responses.offsetDb(link.request(Packets.readAdcOffset())), 0.0)
        assertTrue(Responses.noiseCancelling(link.request(Packets.readNoiseCancelling())))
        assertEquals("0.9", Responses.firmware(link.request(Packets.readFirmware())))
    }

    @Test
    fun dropWritesFor() = runTest {
        val dev = FakeHorizon()
        dev.dropWritesFor = setOf(1)
        val link = HorizonLink(dev, backgroundScope)
        link.write(Packets.writeBand(1, custom))
        link.write(Packets.writeBand(3, custom))
        assertEquals(EqBand.DEFAULTS[1], dev.bands[1].band)
        assertEquals(custom, dev.bands[3].band)
    }

    @Test
    fun logHasVirtualTimestamps() = runTest {
        val dev = FakeHorizon { currentTime }
        val link = HorizonLink(dev, backgroundScope)
        link.write(Packets.commitCoefficients())
        link.delay(40)
        link.write(Packets.saveToFlash())
        assertEquals(listOf(LogEntry(0x01, 0x0A, 0), LogEntry(0x01, 0x01, 40)), dev.log)
    }

    @Test
    fun unplugAfterThrowsAndEndsIncoming() = runTest {
        val dev = FakeHorizon()
        dev.unplugAfter = 2
        val link = HorizonLink(dev, backgroundScope, timeoutMs = 10, retries = 0)
        link.write(Packets.setDacOffset(1.0))
        link.write(Packets.setDacOffset(2.0))
        try { link.write(Packets.setDacOffset(3.0)); fail() } catch (_: DeviceGoneException) {}
        try { link.write(Packets.setDacOffset(4.0)); fail() } catch (_: DeviceGoneException) {}
        assertEquals(2.0, dev.dacOffset, 0.0)
        assertEquals(2, dev.log.size)
    }

    @Test
    fun forbiddenCommandFails() = runTest {
        val dev = FakeHorizon()
        for (cmd in intArrayOf(0x05, 0x0D, 0x17, 0xFF)) {
            val p = ByteArray(63).also { it[0] = 0x80.toByte(); it[1] = cmd.toByte() }
            try { dev.send(p); fail("cmd $cmd") } catch (_: AssertionError) {}
        }
        dev.failIfForbidden = false
        dev.send(ByteArray(63).also { it[0] = 0x80.toByte(); it[1] = 0x05 })
        assertFalse(dev.log.isEmpty())
    }
}
