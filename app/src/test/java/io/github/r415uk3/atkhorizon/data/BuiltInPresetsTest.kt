package io.github.r415uk3.atkhorizon.data

import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPresetsTest {
    private fun row(fc: List<Int>, q: List<Double>, g: List<Double>) =
        fc.indices.map { EqBand(FilterType.Peak, fc[it], q[it], g[it]) }

    private val spec: Map<String, List<EqBand>> = mapOf(
        "default" to row(listOf(31, 62, 125, 250, 500, 1000, 2000, 4000), List(8) { 0.75 }, List(8) { 0.0 }),
        "custom1" to row(listOf(20, 50, 100, 200, 500, 3000, 8000, 20000),
            listOf(.75, .75, .5, .75, .75, .25, .1, .75), listOf(-2.0, -1.5, -0.9, 0.0, 0.0, -0.8, 1.1, 0.0)),
        "cs2" to row(listOf(60, 120, 250, 500, 1500, 2800, 4500, 6800),
            listOf(.7, .9, 1.0, 1.0, 1.2, 1.6, 2.0, 1.2), listOf(-4.0, -3.0, -2.0, 0.0, 2.0, -3.0, 1.0, 1.0)),
        "rnb" to row(listOf(60, 120, 250, 500, 1000, 2500, 5000, 10000),
            listOf(.7, 1.3, 1.3, 1.0, 1.3, 1.4, 1.2, .8), listOf(0.0, -2.0, -0.5, 0.5, 1.5, 1.0, 0.0, 0.5)),
        "valorant" to row(listOf(40, 80, 160, 400, 1800, 3200, 5500, 10000),
            listOf(.7, .9, 1.0, 1.1, 1.3, 1.6, 1.2, 1.0), listOf(-5.0, 0.1, 1.9, 2.4, -2.2, -1.0, -1.5, -2.0)),
        "hiphop" to row(listOf(40, 80, 160, 400, 1200, 2500, 6500, 10500),
            listOf(.8, 1.0, 1.1, 1.0, 1.1, 1.2, 1.6, .7), listOf(2.5, 1.2, -2.0, -1.0, 2.5, 3.0, -2.0, 1.0)),
        "rock" to row(listOf(60, 120, 250, 500, 1000, 2500, 5000, 10000),
            listOf(.9, 1.3, 1.6, .8, 1.2, 1.5, 1.3, .8), listOf(1.2, -1.8, -2.3, -1.5, 1.3, 1.2, -0.1, 1.5)),
        "delta" to row(listOf(60, 120, 250, 500, 1000, 2000, 4000, 8000),
            listOf(.8, 1.0, 1.2, 1.0, 1.0, 1.5, 1.55, 1.11), listOf(-5.4, -4.0, 2.9, 2.0, 0.8, -4.2, -3.2, -2.5)),
        "apex" to row(listOf(60, 100, 355, 800, 1100, 3000, 5000, 10000),
            listOf(1.0, .75, .9, 1.0, .75, .75, 1.5, 1.2), listOf(-1.8, 2.0, 3.5, 0.5, -2.0, 1.5, -2.0, 1.0)),
    )

    @Test
    fun builtInsMatchSpecTable() {
        assertEquals(listOf("default", "custom1", "cs2", "rnb", "valorant", "hiphop", "rock", "delta", "apex"),
            BuiltInPresets.all.map { it.id })
        for (p in BuiltInPresets.all) {
            assertTrue(p.builtIn)
            assertEquals("preset_${p.id}", p.name)
            assertEquals(8, p.bands.size)
            assertTrue(p.bands.all { it.type == FilterType.Peak })
            assertEquals("bands of ${p.id}", spec.getValue(p.id), p.bands)
        }
        val cs2 = BuiltInPresets.all.first { it.id == "cs2" }
        assertEquals(EqBand(FilterType.Peak, 60, 0.7, -4.0), cs2.bands[0])
        assertEquals(EqBand(FilterType.Peak, 6800, 1.2, 1.0), cs2.bands[7])
        assertEquals(EqBand(FilterType.Peak, 355, 0.9, 3.5), BuiltInPresets.all.first { it.id == "apex" }.bands[2])
    }
}
