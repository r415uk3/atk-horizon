package io.github.r415uk3.atkhorizon.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class GainSliderTest {
    @Test fun snapsToHalfDecibel() {
        assertEquals(1.5, snapOffset(1.62), 0.0)
        assertEquals(1.0, snapOffset(1.2), 0.0)
        assertEquals(-2.5, snapOffset(-2.6), 0.0)
    }

    @Test fun clampsToRange() {
        assertEquals(10.0, snapOffset(14.0), 0.0)
        assertEquals(-10.0, snapOffset(-99.0), 0.0)
    }

    @Test fun zeroIsPositiveZero() {
        assertEquals("0.0", snapOffset(-0.1).toString())
    }

    @Test fun vidPidIsHex() {
        assertEquals("0x373B / 0x120C", vidPidText())
    }
}
