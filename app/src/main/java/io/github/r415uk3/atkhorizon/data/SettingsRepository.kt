package io.github.r415uk3.atkhorizon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { Auto, Light, Dark }

interface SettingsStore {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(m: ThemeMode)

    /**
     * Changes whenever a new wallpaper is saved (the file itself is `filesDir/wallpaper.jpg`, see
     * [WallpaperFiles]); null when there is none. Only this flag is stored, never a URI.
     */
    val wallpaperVersion: Flow<Long?>
    suspend fun setWallpaperVersion(v: Long?)
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val wallpaperKey = longPreferencesKey("wallpaper_version")

    override val themeMode: Flow<ThemeMode> = dataStore.data.safe().map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.Auto
    }

    override suspend fun setThemeMode(m: ThemeMode) {
        dataStore.edit { it[themeKey] = m.name }
    }

    override val wallpaperVersion: Flow<Long?> = dataStore.data.safe().map { it[wallpaperKey] }

    override suspend fun setWallpaperVersion(v: Long?) {
        dataStore.edit { if (v == null) it.remove(wallpaperKey) else it[wallpaperKey] = v }
    }
}

/** A corrupt preferences file reads as empty instead of crashing collectors. */
internal fun Flow<Preferences>.safe(): Flow<Preferences> =
    catch { if (it is IOException) emit(emptyPreferences()) else throw it }
