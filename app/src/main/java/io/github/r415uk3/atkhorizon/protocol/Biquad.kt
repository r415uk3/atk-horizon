package io.github.r415uk3.atkhorizon.protocol

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType(val code: Int) {
    Off(0), LowShelf(1), Peak(2), HighShelf(3), LowPass(4), HighPass(5);

    companion object {
        fun fromCode(c: Int): FilterType = entries.firstOrNull { it.code == c } ?: Off
    }
}

class UnstableFilterException(msg: String) : IllegalArgumentException(msg)

/** 1:1 port of the ATK Hub biquad coefficient math (96 kHz, Q30 fixed point, little-endian). */
object Biquad {
    const val SAMPLE_RATE = 96000

    fun coefficientBytes(type: FilterType, fc: Int, q: Double, gainDb: Double): ByteArray {
        val f = fc.toDouble()
        val fs = SAMPLE_RATE.toDouble()
        return when (type) {
            FilterType.Off -> ByteArray(20).also { it[3] = 1 }
            FilterType.Peak -> peak(f, q, gainDb, fs)
            FilterType.LowShelf -> lowShelf(f, gainDb, fs)
            FilterType.HighShelf -> highShelf(f, q, gainDb, fs)
            FilterType.LowPass -> lowPass(f, q, fs)
            FilterType.HighPass -> highPass(f, q, fs)
        }
    }

    // JS Math.round(x * (1 << 30)) == floor(x + 0.5)
    private fun q30(x: Double): Long = floor(x * (1 shl 30) + 0.5).toLong()

    /** a = [1, a1, a2] denominator, b = [b0, b1, b2]; output order [b0, b1, b2, -a1, -a2]. */
    private fun pack(a: DoubleArray, b: DoubleArray): ByteArray {
        val words = longArrayOf(q30(b[0]), q30(b[1]), q30(b[2]), -q30(a[1]), -q30(a[2]))
        val out = ByteArray(20)
        for (i in words.indices) {
            val v = words[i].toInt() // wrap to int32 like JS ToInt32
            for (k in 0 until 4) out[i * 4 + k] = (v shr (8 * k)).toByte()
        }
        return out
    }

    private fun peak(fc: Double, q: Double, gain: Double, fs: Double): ByteArray {
        val o = sqrt(10.0.pow(gain / 20))
        val r = 2 * PI * fc / fs
        val l = sin(r) / (2 * q)
        val c = 1 + l * o
        val d = -2 * cos(r)
        val v = 1 - l * o
        val f = 1 + l / o
        val p = -2 * cos(r)
        val h = 1 - l / o
        return pack(doubleArrayOf(1.0, p / f, h / f), doubleArrayOf(c / f, d / f, v / f))
    }

    private fun lowShelf(fc: Double, gain: Double, fs: Double): ByteArray {
        val o = 10.0.pow(gain / 40)
        val r = 2 * PI * fc / fs
        val cr = cos(r)
        val l = sin(r) / 2 * sqrt((o + 1 / o) * (1 / 0.8 - 1) + 2)
        val so = sqrt(o)
        val c = o * (o + 1 - (o - 1) * cr + 2 * so * l)
        val d = 2 * o * (o - 1 - (o + 1) * cr)
        val v = o * (o + 1 - (o - 1) * cr - 2 * so * l)
        val f = o + 1 + (o - 1) * cr + 2 * so * l
        val p = -2 * (o - 1 + (o + 1) * cr)
        val h = o + 1 + (o - 1) * cr - 2 * so * l
        return pack(doubleArrayOf(1.0, p / f, h / f), doubleArrayOf(c / f, d / f, v / f))
    }

    private fun highShelf(fcIn: Double, qIn: Double, gainIn: Double, fs: Double): ByteArray {
        val nyq = fs / 2
        val fc = max(20.0, min(fcIn, nyq * 0.99))
        val q = max(0.5, min(5.0, qIn))
        val gain = max(-12.0, min(12.0, gainIn)) * 0.85

        val i = 10.0.pow(abs(gain) / 40)
        val o = sqrt(i)
        val r = 2 * PI * fc / fs
        val l = cos(r)
        val c = sin(r) / 2 * sqrt((i + 1 / i) * (1 / q - 1) + 2)
        val d = i * (i + 1 + (i - 1) * l + 2 * o * c)
        val v = -2 * i * (i - 1 + (i + 1) * l)
        val f = i * (i + 1 + (i - 1) * l - 2 * o * c)
        val p = i + 1 - (i - 1) * l + 2 * o * c
        val h = 2 * (i - 1 - (i + 1) * l)
        val g = i + 1 - (i - 1) * l - 2 * o * c
        val s = if (gain >= 0) 1.0 else -1.0
        val b = doubleArrayOf(s * d / p, s * v / p, s * f / p)
        val a = doubleArrayOf(1.0, h / p, g / p)

        val radius = sqrt(a[2])
        if (radius >= 0.999) throw UnstableFilterException("Unstable filter: pole radius=$radius")
        return pack(a, b)
    }

    private fun lowPass(fc: Double, q: Double, fs: Double): ByteArray {
        val o = 2 * PI * fc / fs
        val r = sin(o) / (2 * q)
        val l = (1 - cos(o)) / 2
        val c = 1 - cos(o)
        val d = (1 - cos(o)) / 2
        val v = 1 + r
        val f = -2 * cos(o)
        val p = 1 - r
        return pack(doubleArrayOf(1.0, f / v, p / v), doubleArrayOf(l / v, c / v, d / v))
    }

    private fun highPass(fc: Double, q: Double, fs: Double): ByteArray {
        val o = 2 * PI * fc / fs
        val r = sin(o) / (2 * q)
        val l = (1 + cos(o)) / 2
        val c = -(1 + cos(o))
        val d = (1 + cos(o)) / 2
        val v = 1 + r
        val f = -2 * cos(o)
        val p = 1 - r
        return pack(doubleArrayOf(1.0, f / v, p / v), doubleArrayOf(l / v, c / v, d / v))
    }
}
