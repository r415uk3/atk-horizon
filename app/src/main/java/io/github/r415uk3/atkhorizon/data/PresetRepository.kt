package io.github.r415uk3.atkhorizon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

interface PresetStore {
    /** Built-ins first, then custom presets in creation order. */
    val presets: Flow<List<Preset>>
    suspend fun create(name: String, bands: List<EqBand>): Preset

    /** No-op for built-ins and unknown ids. */
    suspend fun rename(id: String, name: String)

    /** No-op for built-ins and unknown ids. */
    suspend fun delete(id: String)
}

@Serializable
private data class BandDto(val type: Int, val fc: Int, val q: Double, val gain: Double)

@Serializable
private data class PresetDto(val id: String, val name: String, val bands: List<BandDto>)

/** JSON codec for custom presets (kept separate from the protocol types). */
internal object PresetJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PresetDto.serializer())

    fun encode(presets: List<Preset>): String = json.encodeToString(
        serializer,
        presets.map { p ->
            PresetDto(p.id, p.name, p.bands.map { BandDto(it.type.code, it.fc, it.q, it.gainDb) })
        },
    )

    /** Corrupt or missing data yields an empty list rather than a crash. */
    fun decode(text: String?): List<Preset> {
        if (text.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(serializer, text).map { d ->
                Preset(
                    d.id, d.name,
                    d.bands.map { EqBand(FilterType.fromCode(it.type), it.fc, it.q, it.gain) },
                    builtIn = false,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class PresetRepository(private val dataStore: DataStore<Preferences>) : PresetStore {
    private val key = stringPreferencesKey("custom_presets")

    override val presets: Flow<List<Preset>> =
        dataStore.data.map { BuiltInPresets.all + PresetJson.decode(it[key]) }

    override suspend fun create(name: String, bands: List<EqBand>): Preset {
        val p = Preset("custom-" + UUID.randomUUID(), name, bands, builtIn = false)
        update { it + p }
        return p
    }

    override suspend fun rename(id: String, name: String) =
        update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    private suspend fun update(f: (List<Preset>) -> List<Preset>) {
        dataStore.edit { prefs -> prefs[key] = PresetJson.encode(f(PresetJson.decode(prefs[key]))) }
    }
}
