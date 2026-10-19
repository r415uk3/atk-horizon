package io.github.r415uk3.atkhorizon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { Auto, Light, Dark }

interface SettingsStore {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(m: ThemeMode)
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {
    private val key = stringPreferencesKey("theme_mode")

    override val themeMode: Flow<ThemeMode> = dataStore.data.safe().map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[key] } ?: ThemeMode.Auto
    }

    override suspend fun setThemeMode(m: ThemeMode) {
        dataStore.edit { it[key] = m.name }
    }
}

/** A corrupt preferences file reads as empty instead of crashing collectors. */
internal fun Flow<Preferences>.safe(): Flow<Preferences> =
    catch { if (it is IOException) emit(emptyPreferences()) else throw it }
