package io.github.r415uk3.atkhorizon.protocol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HorizonDeviceTest {
    private class Rig(scope: TestScope) {
        val fake = FakeHorizon { scope.currentTime }
        val link = HorizonLink(fake, scope.backgroundScope)
        val dev = HorizonDevice(link)
    }

    private val custom = List(8) { EqBand(FilterType.Peak, 100 * (it + 1), 1.5, if (it % 2 == 0) 3.5 else -2.0) }

    @Test
    fun applyPresetCommandOrder() = runTest {
        val r = Rig(this)
        r.dev.applyPreset(custom)
        val pairs = r.fake.log.map { it.dir to it.cmd }
        val expected = List(8) { 0x01 to 0x09 } + listOf(0x01 to 0x0A, 0x80 to 0x03, 0x01 to 0x03, 0x01 to 0x01) +
            List(8) { 0x80 to 0x09 }
        assertEquals(expected, pairs)
        val dac = r.fake.log.first { it.dir == 0x01 && it.cmd == 0x03 }
        val save = r.fake.log.first { it.dir == 0x01 && it.cmd == 0x01 }
        assertTrue(save.atMs - dac.atMs >= 1000)
    }

    @Test
    fun applyPresetVerifies() = runTest {
        val r = Rig(this)
        val back = r.dev.applyPreset(custom)
        assertEquals(custom.mapIndexed { i, b -> Responses.band(Packets.writeBand(i, b)) }, back)
        assertEquals(back, r.fake.flashBands)
    }

    @Test
    fun applyPresetDetectsDroppedWrite() = runTest {
        val r = Rig(this)
        r.fake.dropWritesFor = setOf(2)
        val e = runCatching { r.dev.applyPreset(custom) }.exceptionOrNull()
        assertTrue(e is VerifyException)
        assertEquals(listOf(2), (e as VerifyException).mismatchedBands)
    }

    @Test
    fun emptyCommitIsNoop() = runTest {
        val r = Rig(this)
        assertEquals(emptyList<DeviceBand>(), r.dev.commitBands(emptyMap()))
        assertTrue(r.fake.log.isEmpty())
    }

    @Test
    fun liveBandDoesNotSave() = runTest {
        val r = Rig(this)
        r.dev.liveBand(3, custom[3])
        assertEquals(listOf(0x09, 0x0A), r.fake.log.map { it.cmd })
        assertFalse(r.fake.log.any { it.cmd == 0x01 })
        assertEquals(custom[3], r.fake.bands[3].band)
    }

    @Test
    fun softResetRestoresDefaults() = runTest {
        val r = Rig(this)
        r.dev.applyPreset(custom)
        r.link.write(Packets.setDacOffset(2.5))
        r.link.write(Packets.setAdcOffset(-1.0))
        r.link.write(Packets.saveToFlash())
        val snap = r.dev.softReset()
        val defaults = EqBand.DEFAULTS.mapIndexed { i, b -> Responses.band(Packets.writeBand(i, b)) }
        assertEquals(defaults, r.fake.bands)
        assertEquals(defaults, r.fake.flashBands)
        assertEquals(defaults, snap.bands)
        assertEquals(0.0, r.fake.dacOffset, 0.0)
        assertEquals(0.0, r.fake.adcOffset, 0.0)
        assertEquals(0.0, r.fake.flashDacOffset, 0.0)
        assertEquals(0.0, r.fake.flashAdcOffset, 0.0)
    }

    @Test
    fun softResetNeverErases() = runTest {
        val r = Rig(this)
        r.fake.failIfForbidden = true
        r.dev.softReset()
        assertTrue(r.fake.log.none { it.cmd in setOf(0x05, 0x0D, 0x17, 0xFF) })
    }

    @Test
    fun unplugDuringApplyFailsCleanly() = runTest {
        val r = Rig(this)
        r.fake.unplugAfter = 3
        val e = runCatching { r.dev.applyPreset(custom) }.exceptionOrNull()
        assertTrue(e is DeviceGoneException)
        assertEquals(3, r.fake.log.size)
    }

    @Test
    fun ncVerify() = runTest {
        val r = Rig(this)
        r.dev.setNoiseCancelling(true)
        assertTrue(r.fake.nc)
        r.dev.setNoiseCancelling(false)
        assertFalse(r.fake.nc)
    }

    @Test
    fun offsetsVerify() = runTest {
        val r = Rig(this)
        r.dev.setDacOffset(1.5)
        r.dev.setAdcOffset(-2.25)
        assertEquals(1.5, r.fake.dacOffset, 0.0)
        assertEquals(-2.25, r.fake.adcOffset, 0.0)
        assertEquals(1.5, r.fake.flashDacOffset, 0.0)
        assertEquals(-2.25, r.fake.flashAdcOffset, 0.0)
    }

    @Test
    fun snapshotReadOrder() = runTest {
        val r = Rig(this)
        val s = r.dev.readSnapshot()
        assertEquals("0.9", s.firmware)
        assertEquals(8, s.bands.size)
        val cmds = r.fake.log.map { it.cmd }
        assertEquals(listOf(0x0C) + List(8) { 0x09 } + listOf(0x03, 0x02, 0x1B), cmds)
    }
}
