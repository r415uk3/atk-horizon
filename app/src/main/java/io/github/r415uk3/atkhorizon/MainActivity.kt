package io.github.r415uk3.atkhorizon

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.r415uk3.atkhorizon.data.PresetRepository
import io.github.r415uk3.atkhorizon.data.SettingsRepository
import io.github.r415uk3.atkhorizon.ui.AppShell
import io.github.r415uk3.atkhorizon.ui.HorizonViewModel
import io.github.r415uk3.atkhorizon.ui.theme.HorizonTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as AtkApp

    private val vm: HorizonViewModel by viewModels {
        viewModelFactory {
            initializer {
                HorizonViewModel(
                    usbState = app.connector.state,
                    presetStore = PresetRepository(app.dataStore),
                    settings = SettingsRepository(app.dataStore),
                    requestPermission = app.connector::requestPermission,
                    reconnect = app.connector::reconnect,
                    onDeviceLost = app.connector::drop,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System-bar icon colours follow the app's night mode, which AtkApp keeps equal to the theme.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsState()
            HorizonTheme(state.themeMode) { AppShell(vm, app.wallpaper.state) }
        }
        holdFirstFrameForWallpaper()
    }

    /**
     * On a cold start the wallpaper setting and image load asynchronously; hold the first frame
     * (at most [WALLPAPER_WAIT_MS]) so the solid background does not flash before the photo.
     */
    private fun holdFirstFrameForWallpaper() {
        val content = findViewById<View>(android.R.id.content)
        val start = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = app.wallpaper.state.value != null || SystemClock.uptimeMillis() - start > WALLPAPER_WAIT_MS
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this) else content.postInvalidateOnAnimation()
                    return ready
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        app.onUiStarted()
    }

    override fun onStop() {
        super.onStop()
        // A configuration change recreates the activity at once; keep the claim across it.
        if (!isChangingConfigurations) app.onUiStopped(vm::flushPending)
    }
}

private const val WALLPAPER_WAIT_MS = 500L
