package io.github.r415uk3.atkhorizon.data

import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import io.github.r415uk3.atkhorizon.protocol.encodable
import io.github.r415uk3.atkhorizon.protocol.inUiRange
import kotlin.math.roundToLong
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class ImportedPreset(val name: String, val bands: List<EqBand>)

/** Why a preset file was refused. Band numbers are 0-based (`filterIndex`). */
sealed interface ImportError {
    data object NotJson : ImportError
    data object TooLarge : ImportError
    data object Unreadable : ImportError
    /** Valid JSON, but not the Hub preset layout (missing or mistyped fields). */
    data object WrongShape : ImportError
    data object WrongType : ImportError
    data class WrongFilterCount(val count: Int) : ImportError
    /** `filterIndex` values are not 0..7 each exactly once. */
    data object BadFilterIndex : ImportError
    data class UnknownFilterType(val band: Int) : ImportError
    data class BandOutOfRange(val band: Int) : ImportError
    data class BandUnstable(val band: Int) : ImportError
}

sealed interface ImportResult {
    data class Ok(val preset: ImportedPreset) : ImportResult
    data class Failed(val error: ImportError) : ImportResult
}

@Serializable
private data class HubFilter(
    val filterIndex: Int,
    val filterType: Int,
    val filterFc: Double,
    val filterQ: Double,
    val filterGain: Double,
    // filterCoeffBytes, filterGainOffset, eqIndex and eqNumber are ignored on purpose: Hub exports
    // can carry stale coefficient bytes, so everything is recomputed from the parameters.
)

@Serializable
private data class HubPreset(
    val name: String? = null,
    val filters: List<HubFilter>,
    val type: String? = null,
)

private val hubJson = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }

private const val MAX_NAME = 40
private const val BANDS = 8

/**
 * Parses an ATK Hub `.eq` export. Values are clamped to the UI limits first ([EqBand.clamped]), then
 * validated: so fc 0 or Q 0 import as 20 Hz / Q 0.1, while a band that is still out of range or
 * gives an unstable filter rejects the file. [fallbackName] (the file name without extension) is
 * used when the file has no usable name; if that is blank too the returned name is blank and the caller
 * substitutes a localized default.
 */
fun parseHubEq(json: String, fallbackName: String = ""): ImportResult {
    fun fail(e: ImportError) = ImportResult.Failed(e)
    val root = try {
        hubJson.parseToJsonElement(json.removePrefix("\uFEFF"))
    } catch (_: SerializationException) {
        return fail(ImportError.NotJson)
    } catch (_: IllegalArgumentException) {
        return fail(ImportError.NotJson)
    }
    if (root !is JsonObject) return fail(ImportError.WrongShape)
    val file = try {
        hubJson.decodeFromJsonElement(HubPreset.serializer(), root)
    } catch (_: SerializationException) {
        return fail(ImportError.WrongShape)
    } catch (_: IllegalArgumentException) {
        return fail(ImportError.WrongShape)
    }
    if (file.type != null && file.type != "eq-preset") return fail(ImportError.WrongType)
    if (file.filters.size != BANDS) return fail(ImportError.WrongFilterCount(file.filters.size))
    if (file.filters.map { it.filterIndex }.sorted() != (0 until BANDS).toList()) return fail(ImportError.BadFilterIndex)

    val bands = file.filters.sortedBy { it.filterIndex }.map { f ->
        val type = FilterType.entries.firstOrNull { it.code == f.filterType }
            ?: return fail(ImportError.UnknownFilterType(f.filterIndex))
        // NaN Q/gain survive clamping and are caught by inUiRange (BandOutOfRange); fc must be finite to round.
        if (!f.filterFc.isFinite()) return fail(ImportError.WrongShape)
        EqBand(type, f.filterFc.roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(), f.filterQ, f.filterGain)
            .clamped()
            .also { b ->
                if (!b.inUiRange()) return fail(ImportError.BandOutOfRange(f.filterIndex))
                if (!b.encodable()) return fail(ImportError.BandUnstable(f.filterIndex))
            }
    }
    val name = file.name?.trim().orEmpty().ifEmpty { fallbackName.trim() }.take(MAX_NAME).trim()
    return ImportResult.Ok(ImportedPreset(name, bands))
}
