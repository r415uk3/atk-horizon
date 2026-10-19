package io.github.r415uk3.atkhorizon.ui.eq

import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyResponseTest {
    private val flat = EqBand.DEFAULTS

    @Test
    fun flatIsZero() {
        assertTrue(abs(FrequencyResponse.magnitudeDb(flat, 1000.0)) < 0.01)
    }

    @Test
    fun peakAtCentre() {
        val bands = listOf(EqBand(FilterType.Peak, 1000, 1.0, 3.0))
        assertEquals(3.0, FrequencyResponse.magnitudeDb(bands, 1000.0), 0.05)
        assertTrue(abs(FrequencyResponse.magnitudeDb(bands, 20.0)) < 0.5)
    }

    @Test
    fun offBandsAreUnity() {
        val bands = listOf(EqBand(FilterType.Off, 1000, 1.0, 9.0))
        assertEquals(0.0, FrequencyResponse.magnitudeDb(bands, 1000.0), 1e-9)
    }

    @Test
    fun unstableBandIsSkipped() {
        // Low-frequency, high-Q HighShelf is rejected by the Biquad stability check.
        val unstable = EqBand(FilterType.HighShelf, 20, 5.0, 10.0)
        val bands = listOf(unstable, EqBand(FilterType.Peak, 1000, 1.0, 3.0))
        assertEquals(3.0, FrequencyResponse.magnitudeDb(bands, 1000.0), 0.05)
    }

    @Test
    fun lowPassAttenuatesAbove() {
        val bands = listOf(EqBand(FilterType.LowPass, 1000, 0.707, 0.0))
        assertTrue(FrequencyResponse.magnitudeDb(bands, 10000.0) < -30)
        assertEquals(0.0, FrequencyResponse.magnitudeDb(bands, 20.0), 0.1)
    }
}
