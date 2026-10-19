package io.github.r415uk3.atkhorizon.ui.eq

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AxisMappingTest {
    @Test
    fun axisRoundTrip() {
        val f = Axis.xToFreq(Axis.freqToX(1000.0, 300f), 300f)
        assertTrue("got $f", abs(f - 1000) <= 10)
    }

    @Test
    fun axisEnds() {
        assertEquals(0f, Axis.freqToX(20.0, 300f), 1e-3f)
        assertEquals(300f, Axis.freqToX(20000.0, 300f), 1e-3f)
        assertEquals(20, Axis.xToFreq(0f, 300f))
        assertEquals(20000, Axis.xToFreq(300f, 300f))
    }

    @Test
    fun xToFreqClampsOutside() {
        assertEquals(20, Axis.xToFreq(-50f, 300f))
        assertEquals(20000, Axis.xToFreq(400f, 300f))
    }

    @Test
    fun gainRoundTrip() {
        assertEquals(100f, Axis.gainToY(0.0, 200f), 1e-3f)
        assertEquals(0f, Axis.gainToY(12.0, 200f), 1e-3f)
        assertEquals(200f, Axis.gainToY(-12.0, 200f), 1e-3f)
        for (g in listOf(-12.0, -7.3, 0.0, 0.1, 4.5, 12.0)) {
            assertEquals(g, Axis.yToGain(Axis.gainToY(g, 200f), 200f), 1e-9)
        }
    }

    @Test
    fun yToGainRoundsToTenth() {
        val g = Axis.yToGain(Axis.gainToY(3.04, 200f), 200f)
        assertEquals(3.0, g, 1e-9)
    }
}
