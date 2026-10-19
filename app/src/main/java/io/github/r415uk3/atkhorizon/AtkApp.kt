package io.github.r415uk3.atkhorizon

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.r415uk3.atkhorizon.data.SettingsRepository
import io.github.r415uk3.atkhorizon.data.ThemeMode
import io.github.r415uk3.atkhorizon.data.WallpaperFiles
import io.github.r415uk3.atkhorizon.data.WallpaperLoader
import io.github.r415uk3.atkhorizon.usb.HorizonConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * Owns what must outlive an activity (configuration changes, backgrounding): one USB connector and
 * one DataStore, on an app-wide main-thread scope.
 */
class AtkApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val dataStore: DataStore<Preferences> get() = settingsDataStore
    lateinit var connector: HorizonConnector
        private set
    /** The decoded wallpaper, kept for the process so activity recreation never flashes. */
    lateinit var wallpaper: WallpaperLoader<ImageBitmap>
        private set

    /** True between the activity's onStart and a (non-configuration-change) onStop. */
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        connector = HorizonConnector(this, appScope)
        val wallpaperFile = WallpaperFiles(filesDir).file
        wallpaper = WallpaperLoader(appScope, SettingsRepository(dataStore).wallpaperVersion, Dispatchers.IO) {
            BitmapFactory.decodeFile(wallpaperFile.path)?.asImageBitmap()
        }
        // The persisted theme also drives the app's night mode, so the window background, splash
        // and system-bar icons match the chosen theme from the first frame (no wrong-colour flash).
        val uiMode = getSystemService(UiModeManager::class.java)
        appScope.launch {
            SettingsRepository(dataStore).themeMode.distinctUntilChanged().collect { m ->
                uiMode.setApplicationNightMode(
                    when (m) {
                        ThemeMode.Auto -> UiModeManager.MODE_NIGHT_AUTO
                        ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                    },
                )
            }
        }
    }

    /** Claims the headset while the UI is visible (spec decision 2a). */
    fun onUiStarted() {
        foreground = true
        connector.open()
    }

    /**
     * Saves pending edits to flash, then releases the claim so the inline buttons work again.
     * [flushPending] runs here, on the app scope, and the ViewModel's commits are non-cancellable
     * once started, so clearing the ViewModel (activity finishing) does not cut the flash-save
     * short. The release is skipped if the UI came back meanwhile.
     */
    fun onUiStopped(flushPending: suspend () -> Unit) {
        foreground = false
        appScope.launch {
            flushPending()
            if (!foreground) connector.release()
        }
    }
}
