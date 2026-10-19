package io.github.r415uk3.atkhorizon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { Auto, Light, Dark }

interface SettingsStore {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(m: ThemeMode)
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {
    private val key = stringPreferencesKey("theme_mode")

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[key] } ?: ThemeMode.Auto
    }

    override suspend fun setThemeMode(m: ThemeMode) {
        dataStore.edit { it[key] = m.name }
    }
}
