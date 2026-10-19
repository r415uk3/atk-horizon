package io.github.r415uk3.atkhorizon.ui.eq

import io.github.r415uk3.atkhorizon.protocol.Biquad
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import io.github.r415uk3.atkhorizon.protocol.UnstableFilterException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Magnitude response of the cascaded band biquads, from the exact coefficients the device gets. */
object FrequencyResponse {
    /** Normalised (b0, b1, b2, a1, a2) of [band], or null for Off / an unstable filter (not drawn). */
    fun coefficients(band: EqBand): DoubleArray? {
        if (band.type == FilterType.Off) return null
        val bytes = try {
            Biquad.coefficientBytes(band.type, band.fc, band.q, band.gainDb)
        } catch (_: UnstableFilterException) {
            return null
        }
        val w = DoubleArray(5) { i ->
            var v = 0
            for (k in 0 until 4) v = v or ((bytes[i * 4 + k].toInt() and 0xFF) shl (8 * k))
            v / Q30
        }
        // Wire order is b0, b1, b2, -a1, -a2.
        return doubleArrayOf(w[0], w[1], w[2], -w[3], -w[4])
    }

    fun magnitudeDb(bands: List<EqBand>, freqHz: Double): Double =
        cascadeDb(bands.mapNotNull(::coefficients), freqHz)

    /** As above with precomputed [coefficients] (one array per drawable band). */
    fun cascadeDb(coefficients: List<DoubleArray>, freqHz: Double): Double {
        val w = 2 * PI * freqHz / Biquad.SAMPLE_RATE
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        var db = 0.0
        for (k in coefficients) {
            // H(e^jw) = (b0 + b1 e^-jw + b2 e^-2jw) / (1 + a1 e^-jw + a2 e^-2jw)
            val nr = k[0] + k[1] * c1 + k[2] * c2
            val ni = -(k[1] * s1 + k[2] * s2)
            val dr = 1 + k[3] * c1 + k[4] * c2
            val di = -(k[3] * s1 + k[4] * s2)
            db += 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
        }
        return db
    }

    private const val Q30 = 1073741824.0
}

/** Plot axes: log frequency 20…20000 Hz across the width, ±12 dB down the height (0 dB centred). */
object Axis {
    const val MIN_HZ = 20.0
    const val MAX_HZ = 20000.0
    const val RANGE_DB = 12.0
    private val decades = log10(MAX_HZ / MIN_HZ)

    fun freqToX(f: Double, width: Float): Float = (log10(f / MIN_HZ) / decades * width).toFloat()

    fun xToFreq(x: Float, width: Float): Int {
        val t = (x / width).coerceIn(0f, 1f).toDouble()
        return (MIN_HZ * 10.0.pow(t * decades)).roundToInt().coerceIn(MIN_HZ.toInt(), MAX_HZ.toInt())
    }

    fun gainToY(g: Double, height: Float): Float = (height / 2 * (1 - g / RANGE_DB)).toFloat()

    /** Gain at [y], clamped to ±12 and rounded to 0.1 dB. */
    fun yToGain(y: Float, height: Float): Double {
        val g = ((1 - y / (height / 2)) * RANGE_DB).coerceIn(-RANGE_DB, RANGE_DB)
        return floor(g * 10 + 0.5) / 10
    }
}
