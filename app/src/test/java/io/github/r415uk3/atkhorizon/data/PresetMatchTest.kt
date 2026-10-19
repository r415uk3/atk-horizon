package io.github.r415uk3.atkhorizon.data

import io.github.r415uk3.atkhorizon.protocol.DeviceBand
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import io.github.r415uk3.atkhorizon.protocol.Packets
import io.github.r415uk3.atkhorizon.protocol.Responses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetMatchTest {
    private fun fromDevice(bands: List<EqBand>): List<DeviceBand> =
        bands.mapIndexed { i, b -> Responses.band(Packets.writeBand(i, b)) }

    private fun cs2() = BuiltInPresets.all.first { it.id == "cs2" }

    @Test
    fun curveMatchingFindsCs2() {
        assertEquals("cs2", matchPreset(fromDevice(cs2().bands), BuiltInPresets.all)?.id)
    }

    @Test
    fun everyBuiltInRoundTrips() {
        for (p in BuiltInPresets.all) {
            // custom1 and default are distinct curves, so each must find itself
            assertEquals(p.id, matchPreset(fromDevice(p.bands), BuiltInPresets.all)?.id)
        }
    }

    @Test
    fun curveMatchingNoPresetReturnsNull() {
        val changed = cs2().bands.toMutableList().also { it[3] = it[3].copy(gainDb = 5.0) }
        assertNull(matchPreset(fromDevice(changed), BuiltInPresets.all))
        assertNull(matchPreset(emptyList(), BuiltInPresets.all))
        assertNull(matchPreset(fromDevice(cs2().bands.take(7)), BuiltInPresets.all))
    }

    @Test
    fun typeOrFcMismatchDoesNotMatch() {
        val t = cs2().bands.toMutableList().also { it[0] = it[0].copy(type = FilterType.LowShelf) }
        assertNull(matchPreset(fromDevice(t), BuiltInPresets.all))
        val f = cs2().bands.toMutableList().also { it[0] = it[0].copy(fc = 61) }
        assertNull(matchPreset(fromDevice(f), BuiltInPresets.all))
    }

    @Test
    fun curveMatchingToleratesQ88Rounding() {
        // Q 0.7 -> raw 179 -> 0.6992 on the wire
        val dev = fromDevice(cs2().bands)
        assertEquals(179, dev[0].qRaw)
        assertEquals("cs2", matchPreset(dev, BuiltInPresets.all)?.id)
    }

    @Test
    fun returnsFirstMatch() {
        val dup = cs2().copy(id = "custom-x", name = "mine", builtIn = false)
        assertEquals("cs2", matchPreset(fromDevice(cs2().bands), listOf(cs2(), dup))?.id)
        assertEquals("custom-x", matchPreset(fromDevice(cs2().bands), listOf(dup, cs2()))?.id)
    }

    @Test
    fun deviceBandWithUnknownTypeOrZeroFcMatchesNothingWithoutThrowing() {
        val raw = cs2().bands.mapIndexed { i, b -> Packets.writeBand(i, b) }
        val unknownType = raw.map { it.copyOf() }.also { it[2][33] = 7 }.map { Responses.band(it) }
        assertEquals(FilterType.Off, unknownType[2].band.type)
        assertNull(matchPreset(unknownType, BuiltInPresets.all))
        val zeroFc = raw.map { it.copyOf() }.also { it[4][27] = 0; it[4][28] = 0 }.map { Responses.band(it) }
        assertEquals(0, zeroFc[4].band.fc)
        assertNull(matchPreset(zeroFc, BuiltInPresets.all))
    }
}
