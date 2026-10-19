package io.github.r415uk3.atkhorizon.data

import io.github.r415uk3.atkhorizon.protocol.Biquad
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqFileImportTest {
    private val garbage = (1..20).joinToString(",") { "${it * 7 % 256}" }

    private fun filter(
        i: Int, type: Int = 2, fc: String = "1000", q: String = "0.75", gain: String = "0",
        bytes: String = garbage,
    ) = """{"eqIndex":0,"filterIndex":$i,"filterType":$type,"filterFc":$fc,"filterQ":$q,"filterGain":$gain,""" +
        """"filterGainOffset":0,"eqNumber":0,"filterCoeffBytes":[$bytes]}"""

    private fun file(
        filters: List<String> = (0..7).map { filter(it, fc = "${100 * (it + 1)}", gain = "$it") },
        name: String? = "\"Mine\"", type: String? = "\"eq-preset\"",
    ): String = buildString {
        append("{")
        if (name != null) append("\"name\":$name,")
        append("\"filters\":[${filters.joinToString(",")}]")
        if (type != null) append(",\"type\":$type")
        append(",\"version\":1,\"extra\":true}")
    }

    private fun ok(json: String, fallback: String = "") =
        (parseHubEq(json, fallback) as ImportResult.Ok).preset

    private fun err(json: String): ImportError = (parseHubEq(json) as ImportResult.Failed).error

    @Test
    fun validFileImportsEightPeakBands() {
        val p = ok(file())
        assertEquals("Mine", p.name)
        assertEquals((0..7).map { EqBand(FilterType.Peak, 100 * (it + 1), 0.75, it.toDouble()) }, p.bands)
    }

    @Test
    fun bandsAreOrderedByFilterIndex() {
        val shuffled = listOf(3, 0, 7, 1, 6, 2, 5, 4).map { filter(it, fc = "${100 * (it + 1)}") }
        assertEquals((0..7).map { 100 * (it + 1) }, ok(file(shuffled)).bands.map { it.fc })
    }

    @Test
    fun coefficientBytesInTheFileAreIgnored() {
        // Garbage bytes (and no bytes at all) change nothing: parameters are what counts.
        val noBytes = (0..7).map { filter(it, fc = "${100 * (it + 1)}", gain = "$it").replace(Regex(",\"filterCoeffBytes\":\\[.*?\\]"), "") }
        assertEquals(ok(file()).bands, ok(file(noBytes)).bands)
    }

    @Test
    fun wrongFilterCountIsRejected() {
        assertEquals(ImportError.WrongFilterCount(7), err(file((0..6).map { filter(it) })))
        assertEquals(ImportError.WrongFilterCount(9), err(file((0..8).map { filter(it) })))
    }

    @Test
    fun duplicateOrOutOfRangeIndexIsRejected() {
        assertEquals(ImportError.BadFilterIndex, err(file((listOf(0, 1, 2, 3, 4, 5, 6, 6)).map { filter(it) })))
        assertEquals(ImportError.BadFilterIndex, err(file((listOf(1, 2, 3, 4, 5, 6, 7, 8)).map { filter(it) })))
    }

    @Test
    fun unknownFilterTypeIsRejected() {
        val f = (0..7).map { filter(it, type = if (it == 4) 9 else 2) }
        assertEquals(ImportError.UnknownFilterType(4), err(file(f)))
        assertEquals(ImportError.UnknownFilterType(0), err(file((0..7).map { filter(it, type = -1) })))
    }

    @Test
    fun allKnownTypesImport() {
        val f = (0..5).map { filter(it, type = it, fc = "1000", q = "1.0", gain = "3") } + listOf(filter(6), filter(7))
        assertEquals(FilterType.entries.toList(), ok(file(f)).bands.take(6).map { it.type })
    }

    @Test
    fun nonJsonAndWrongShapeAreRejected() {
        assertEquals(ImportError.NotJson, err("not json {"))
        assertEquals(ImportError.NotJson, err(""))
        assertEquals(ImportError.WrongShape, err("[1,2]"))
        assertEquals(ImportError.WrongShape, err("{}"))
        assertEquals(ImportError.WrongShape, err("""{"filters":"x"}"""))
        assertEquals(ImportError.WrongShape, err(file((0..7).map { filter(it, fc = "\"high\"") })))
    }

    @Test
    fun wrongTypeIsRejectedButMissingTypeIsFine() {
        assertEquals(ImportError.WrongType, err(file(type = "\"other\"")))
        assertEquals("Mine", ok(file(type = null)).name)
    }

    @Test
    fun outOfRangeValuesAreClampedThenValidated() {
        // Rule: clamp to the UI limits first (fc 20..20000, Q 0.1..10, gain -10..10, 0.1 steps), then reject
        // only what is still out of range or unstable. So these import, clamped:
        val f = (0..7).map {
            if (it == 0) filter(0, fc = "0", q = "0", gain = "99") else if (it == 1) filter(1, fc = "999999", q = "50", gain = "-99.04")
            else filter(it)
        }
        val b = ok(file(f)).bands
        assertEquals(EqBand(FilterType.Peak, 20, 0.1, 10.0), b[0])
        assertEquals(EqBand(FilterType.Peak, 20000, 10.0, -10.0), b[1])
    }

    @Test
    fun missingOrBlankNameFallsBackToTheFileName() {
        assertEquals("Music", ok(file(name = null), "Music").name)
        assertEquals("Music", ok(file(name = "\"   \""), "Music").name)
        assertEquals("Music", ok(file(name = "null"), "Music").name)
        assertEquals("Mine", ok(file(), "Music").name)
    }

    private val realFileBands = listOf(
        Triple(60, 5.0, intArrayOf(10, 252, 24, 64, 234, 116, 64, 128, 154, 207, 166, 63, 22, 139, 191, 127, 92, 52, 64, 192)),
        Triple(125, 4.0, intArrayOf(86, 86, 41, 64, 15, 113, 142, 128, 33, 80, 73, 63, 241, 142, 113, 127, 137, 89, 141, 192)),
        Triple(250, 2.0, intArrayOf(33, 216, 40, 64, 173, 213, 63, 129, 77, 170, 155, 62, 83, 42, 192, 126, 146, 125, 59, 193)),
        Triple(2500, 1.0, intArrayOf(4, 230, 185, 64, 61, 7, 116, 141, 199, 11, 95, 51, 195, 248, 139, 114, 53, 14, 231, 203)),
        Triple(5000, 1.0, intArrayOf(240, 98, 80, 65, 159, 20, 48, 155, 77, 234, 37, 41, 97, 235, 207, 100, 195, 178, 137, 213)),
        Triple(10000, 3.0, intArrayOf(216, 130, 184, 70, 243, 216, 76, 180, 71, 96, 178, 24, 13, 39, 179, 75, 226, 28, 149, 224)),
        Triple(16000, 3.0, intArrayOf(220, 224, 161, 72, 21, 203, 236, 212, 250, 136, 132, 13, 235, 52, 19, 43, 43, 150, 217, 233)),
    )

    /** Offline confirmation of the formula port against bytes from a real Hub export (band 500 Hz/0 dB is stale there and omitted). */
    @Test
    fun coefficientFormulaMatchesRealHubBytes() {
        for ((fc, gain, bytes) in realFileBands) {
            val expected = ByteArray(20) { bytes[it].toByte() }
            val actual = Biquad.coefficientBytes(FilterType.Peak, fc, 0.75, gain)
            assertTrue("fc=$fc gain=$gain", expected.contentEquals(actual))
        }
    }
}
