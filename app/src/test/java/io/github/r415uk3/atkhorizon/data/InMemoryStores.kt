package io.github.r415uk3.atkhorizon.data

import io.github.r415uk3.atkhorizon.protocol.EqBand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Test fake with the same semantics as [PresetRepository]. */
class InMemoryPresetStore(initialCustom: List<Preset> = emptyList()) : PresetStore {
    private val custom = MutableStateFlow(initialCustom)

    override val presets: Flow<List<Preset>> = custom.map { BuiltInPresets.all + it }

    override suspend fun create(name: String, bands: List<EqBand>): Preset {
        val p = Preset("custom-" + UUID.randomUUID(), name, bands, builtIn = false)
        custom.update { it + p }
        return p
    }

    override suspend fun rename(id: String, name: String) =
        custom.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun delete(id: String) = custom.update { list -> list.filterNot { it.id == id } }
}

class InMemorySettingsStore(initial: ThemeMode = ThemeMode.Auto) : SettingsStore {
    private val mode = MutableStateFlow(initial)
    override val themeMode: Flow<ThemeMode> = mode
    override suspend fun setThemeMode(m: ThemeMode) {
        mode.value = m
    }
}
