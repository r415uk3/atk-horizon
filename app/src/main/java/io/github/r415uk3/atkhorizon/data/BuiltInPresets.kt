package io.github.r415uk3.atkhorizon.data

import io.github.r415uk3.atkhorizon.protocol.DeviceBand
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import kotlin.math.abs

/** [name] of a built-in preset is a stable resource key (e.g. "preset_cs2"); custom presets hold user text. */
data class Preset(val id: String, val name: String, val bands: List<EqBand>, val builtIn: Boolean)

/** Built-in presets from ATK Hub: all Peak, 8 bands. */
object BuiltInPresets {
    private fun preset(id: String, fc: List<Int>, q: List<Double>, gain: List<Double>): Preset {
        require(fc.size == 8 && q.size == 8 && gain.size == 8)
        return Preset(id, "preset_$id", List(8) { EqBand(FilterType.Peak, fc[it], q[it], gain[it]) }, builtIn = true)
    }

    val all: List<Preset> = listOf(
        preset("default",
            listOf(31, 62, 125, 250, 500, 1000, 2000, 4000),
            List(8) { 0.75 },
            List(8) { 0.0 }),
        preset("custom1",
            listOf(20, 50, 100, 200, 500, 3000, 8000, 20000),
            listOf(0.75, 0.75, 0.5, 0.75, 0.75, 0.25, 0.1, 0.75),
            listOf(-2.0, -1.5, -0.9, 0.0, 0.0, -0.8, 1.1, 0.0)),
        preset("cs2",
            listOf(60, 120, 250, 500, 1500, 2800, 4500, 6800),
            listOf(0.7, 0.9, 1.0, 1.0, 1.2, 1.6, 2.0, 1.2),
            listOf(-4.0, -3.0, -2.0, 0.0, 2.0, -3.0, 1.0, 1.0)),
        preset("rnb",
            listOf(60, 120, 250, 500, 1000, 2500, 5000, 10000),
            listOf(0.7, 1.3, 1.3, 1.0, 1.3, 1.4, 1.2, 0.8),
            listOf(0.0, -2.0, -0.5, 0.5, 1.5, 1.0, 0.0, 0.5)),
        preset("valorant",
            listOf(40, 80, 160, 400, 1800, 3200, 5500, 10000),
            listOf(0.7, 0.9, 1.0, 1.1, 1.3, 1.6, 1.2, 1.0),
            listOf(-5.0, 0.1, 1.9, 2.4, -2.2, -1.0, -1.5, -2.0)),
        preset("hiphop",
            listOf(40, 80, 160, 400, 1200, 2500, 6500, 10500),
            listOf(0.8, 1.0, 1.1, 1.0, 1.1, 1.2, 1.6, 0.7),
            listOf(2.5, 1.2, -2.0, -1.0, 2.5, 3.0, -2.0, 1.0)),
        preset("rock",
            listOf(60, 120, 250, 500, 1000, 2500, 5000, 10000),
            listOf(0.9, 1.3, 1.6, 0.8, 1.2, 1.5, 1.3, 0.8),
            listOf(1.2, -1.8, -2.3, -1.5, 1.3, 1.2, -0.1, 1.5)),
        preset("delta",
            listOf(60, 120, 250, 500, 1000, 2000, 4000, 8000),
            listOf(0.8, 1.0, 1.2, 1.0, 1.0, 1.5, 1.55, 1.11),
            listOf(-5.4, -4.0, 2.9, 2.0, 0.8, -4.2, -3.2, -2.5)),
        preset("apex",
            listOf(60, 100, 355, 800, 1100, 3000, 5000, 10000),
            listOf(1.0, 0.75, 0.9, 1.0, 0.75, 0.75, 1.5, 1.2),
            listOf(-1.8, 2.0, 3.5, 0.5, -2.0, 1.5, -2.0, 1.0)),
    )
}

private const val TOLERANCE = 0.01

/**
 * First preset whose bands equal the device curve: fc and type exactly, q and gain within 0.01
 * (the device parser rounds q to 2 and gain to 1 decimals). Null means "Custom".
 */
fun matchPreset(device: List<DeviceBand>, presets: List<Preset>): Preset? =
    presets.firstOrNull { p ->
        p.bands.size == device.size && p.bands.indices.all { i ->
            val a = p.bands[i]
            val d = device[i].band
            a.type == d.type && a.fc == d.fc &&
                abs(a.gainDb - d.gainDb) < TOLERANCE && abs(a.q - d.q) < TOLERANCE
        }
    }
