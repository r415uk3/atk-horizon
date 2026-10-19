package io.github.r415uk3.atkhorizon.protocol

import kotlin.math.floor

data class EqBand(val type: FilterType, val fc: Int, val q: Double, val gainDb: Double) {
    /** Clamps to fc 20..20000, q 0.1..10, gain -10..10 and rounds gain to 0.1. */
    fun clamped(): EqBand = EqBand(
        type,
        fc.coerceIn(20, 20000),
        q.coerceIn(0.1, 10.0),
        floor(gainDb.coerceIn(-10.0, 10.0) * 10 + 0.5) / 10,
    )

    companion object {
        val DEFAULTS: List<EqBand> = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000)
            .map { EqBand(FilterType.Peak, it, 0.75, 0.0) }
    }
}

/** A band as read back from the device; raw values are kept for exact comparison. */
data class DeviceBand(val index: Int, val band: EqBand, val qRaw: Int, val gainRaw: Int, val coeff: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is DeviceBand && index == other.index && band == other.band &&
            qRaw == other.qRaw && gainRaw == other.gainRaw && coeff.contentEquals(other.coeff)

    override fun hashCode(): Int {
        var h = index
        h = 31 * h + band.hashCode()
        h = 31 * h + qRaw
        h = 31 * h + gainRaw
        h = 31 * h + coeff.contentHashCode()
        return h
    }
}
