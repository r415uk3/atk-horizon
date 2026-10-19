package io.github.r415uk3.atkhorizon.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class BiquadTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it) }

    @Test fun peak1k() = assertEquals("6d fe b4 40 03 bf b1 83 44 8a dd 3b fd 40 4e 7c 4f 77 6d c3", hex(Biquad.coefficientBytes(FilterType.Peak, 1000, 1.0, 3.0)))
    @Test fun peakFlat() = assertEquals("00 00 00 40 98 54 2c 80 a7 bc d3 3f 68 ab d3 7f 59 43 2c c0", hex(Biquad.coefficientBytes(FilterType.Peak, 31, 0.75, 0.0)))
    @Test fun peakCut() = assertEquals("44 b9 ea 3f a5 8e 73 80 8c f8 a1 3f 5b 71 8c 7f 31 4e 73 c0", hex(Biquad.coefficientBytes(FilterType.Peak, 60, 0.7, -4.0)))
    @Test fun lowshelf() = assertEquals("2c 9b 13 40 67 88 97 80 94 bd 55 3f 25 a1 68 7f cc d0 96 c0", hex(Biquad.coefficientBytes(FilterType.LowShelf, 100, 0.7, 4.0)))
    @Test fun highshelfCut() = assertEquals("92 5c f5 ae 1b 24 7b 66 25 a0 03 dd 4c 86 af 4a 86 9a c4 e7", hex(Biquad.coefficientBytes(FilterType.HighShelf, 8000, 0.7, -3.0)))
    @Test fun highshelfBoost() = assertEquals("6e a3 0a 51 e5 db 84 99 db 5f fc 22 4c 86 af 4a 86 9a c4 e7", hex(Biquad.coefficientBytes(FilterType.HighShelf, 8000, 0.7, 3.0)))
    @Test fun lowpass() = assertEquals("7b 60 9f 04 f6 c0 3e 09 7b 60 9f 04 52 c7 fc 46 c2 b6 85 e6", hex(Biquad.coefficientBytes(FilterType.LowPass, 10000, 0.707, 0.0)))
    @Test fun highpass() = assertEquals("83 b1 e1 3f f9 9c 3c 80 83 b1 e1 3f ae 54 c3 7f a0 8e 3c c0", hex(Biquad.coefficientBytes(FilterType.HighPass, 40, 0.707, 0.0)))
    @Test fun off() = assertEquals("00 00 00 01 " + List(16) { "00" }.joinToString(" "), hex(Biquad.coefficientBytes(FilterType.Off, 1000, 1.0, 0.0)))
    @Test(expected = UnstableFilterException::class) fun highshelfUnstableThrows() { Biquad.coefficientBytes(FilterType.HighShelf, 20, 5.0, 12.0) }
    @Test fun fromCodeUnknownIsOff() = assertEquals(FilterType.Off, FilterType.fromCode(99))
}
