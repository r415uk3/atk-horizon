package io.github.r415uk3.atkhorizon.ui

import io.github.r415uk3.atkhorizon.data.ImportError
import io.github.r415uk3.atkhorizon.data.Preset
import io.github.r415uk3.atkhorizon.data.ThemeMode
import io.github.r415uk3.atkhorizon.protocol.EqBand

sealed interface ConnState {
    data object NoDevice : ConnState
    /** An ATK device other than the Horizon is attached; [pid] is its USB product id. */
    data class Unsupported(val pid: Int) : ConnState
    data object NeedsPermission : ConnState
    data object PermissionDenied : ConnState
    /** Opening the USB connection or reading the device. */
    data object Loading : ConnState
    data object Ready : ConnState
    /** The device was connected (Loading/Ready) and went away; a fresh connection re-reads it. */
    data object Disconnected : ConnState
}

sealed interface UiError {
    /** Device read-back differs from what was written; [bands] are band indices (empty for offset/NC). */
    data class VerifyFailed(val bands: List<Int>) : UiError
    data object Disconnected : UiError
    data object Timeout : UiError
    /** The requested value for [band] gives an unstable filter; it was not sent. */
    data class UnstableFilter(val band: Int) : UiError
    /** Custom presets on disk are unreadable; the store refuses to overwrite them. */
    data object PresetStorage : UiError
    /** A preset file could not be imported; nothing was saved or sent. */
    data class ImportFailed(val reason: ImportError) : UiError
    /**
     * The device reports a gain offset outside ±10 dB (implausible; not shown). [changeBlocked]: an
     * EQ change was refused before anything was written, so the wild DAC value is not written back.
     */
    data class SuspiciousOffset(val changeBlocked: Boolean) : UiError
}

data class UiState(
    val conn: ConnState = ConnState.NoDevice,
    val firmware: String? = null,
    /** What the device reports, overlaid with in-flight edits. */
    val bands: List<EqBand> = EqBand.DEFAULTS,
    val presets: List<Preset> = emptyList(),
    /** Null means "Custom". */
    val activePresetId: String? = null,
    /** Null: unknown (the device reported an implausible value). */
    val dacOffset: Double? = 0.0,
    val adcOffset: Double? = 0.0,
    val noiseCancelling: Boolean = false,
    val busy: Boolean = false,
    val error: UiError? = null,
    val themeMode: ThemeMode = ThemeMode.Auto,
)
