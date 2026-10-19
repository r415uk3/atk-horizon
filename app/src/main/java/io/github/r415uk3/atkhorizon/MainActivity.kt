package io.github.r415uk3.atkhorizon

import android.os.Bundle
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
                    reconnect = app.connector::open,
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
            HorizonTheme(state.themeMode) { AppShell(vm) }
        }
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
