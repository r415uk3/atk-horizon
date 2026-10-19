package io.github.r415uk3.atkhorizon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.r415uk3.atkhorizon.data.CorruptPresetsException
import io.github.r415uk3.atkhorizon.data.PresetStore
import io.github.r415uk3.atkhorizon.data.SettingsStore
import io.github.r415uk3.atkhorizon.data.ThemeMode
import io.github.r415uk3.atkhorizon.data.matchPreset
import io.github.r415uk3.atkhorizon.protocol.Biquad
import io.github.r415uk3.atkhorizon.protocol.DeviceBand
import io.github.r415uk3.atkhorizon.protocol.DeviceGoneException
import io.github.r415uk3.atkhorizon.protocol.DeviceSnapshot
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.HorizonDevice
import io.github.r415uk3.atkhorizon.protocol.Packets
import io.github.r415uk3.atkhorizon.protocol.Responses
import io.github.r415uk3.atkhorizon.protocol.UnstableFilterException
import io.github.r415uk3.atkhorizon.protocol.VerifyException
import io.github.r415uk3.atkhorizon.usb.UsbState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns USB state, device operations and the preset/settings stores into [UiState].
 *
 * All members must be called on the main thread (the [scope]'s dispatcher in tests). All device
 * I/O of one connection goes through a single [Mutex], so live drags, debounced commits, preset
 * switches and offset writes never interleave on the wire.
 */
class HorizonViewModel(
    usbState: StateFlow<UsbState>,
    private val presetStore: PresetStore,
    private val settings: SettingsStore,
    private val requestPermission: () -> Unit,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The current connection; null when no device is connected (or it was lost). */
    private var session: Session? = null

    /** Active preset id; kept while it still matches the displayed curve, else re-matched. */
    private var activeId: String? = null

    private inner class Session(val device: HorizonDevice) {
        val job = SupervisorJob(this@HorizonViewModel.scope.coroutineContext[Job])
        /** Safety net: an unexpected exception outside [op] is surfaced, never crashes the app. */
        val cs = CoroutineScope(
            this@HorizonViewModel.scope.coroutineContext + job +
                CoroutineExceptionHandler { _, _ -> if (session === this) setError(UiError.Timeout) },
        )
        val io = Mutex()
        /** What the device last reported. Empty until the snapshot is read. */
        var deviceBands: List<DeviceBand> = emptyList()
        /** Displayed values not yet confirmed by the device (edits, preset being applied). */
        val overlay = mutableMapOf<Int, EqBand>()
        /** User edits awaiting the debounced commit. */
        val dirty = mutableMapOf<Int, EqBand>()
        /** Latest value per band awaiting a throttled live write. */
        val live = linkedMapOf<Int, EqBand>()
        var liveJob: Job? = null
        var debounceJob: Job? = null
        /** Preset select: the latest request wins, older ones are cancelled. */
        var presetJob: Job? = null
        /** Soft reset: never cancelled by a preset select (which queues behind it). */
        var resetJob: Job? = null
        var commitJob: Job? = null
        /** Last offsets / NC the device confirmed; shown again when a write fails. */
        var knownDac = 0.0
        var knownAdc = 0.0
        var knownNc = false
        var dacJob: Job? = null
        var adcJob: Job? = null
        var ncJob: Job? = null
        var busy = 0
    }

    init {
        this.scope.launch {
            presetStore.presets.collect { list ->
                _state.update { it.copy(presets = list) }
                session?.let(::publish)
            }
        }
        this.scope.launch { settings.themeMode.collect { m -> _state.update { it.copy(themeMode = m) } } }
        this.scope.launch { usbState.collect(::onUsb) }
    }

    // ---- USB state ----

    private fun onUsb(u: UsbState) {
        if (u is UsbState.Connected) {
            if (session?.device !== u.device) connect(u.device)
            return
        }
        val hadConnection = _state.value.conn.let {
            it == ConnState.Loading || it == ConnState.Ready || it == ConnState.Disconnected
        }
        endSession()
        val conn = when (u) {
            UsbState.NoDevice -> if (hadConnection) ConnState.Disconnected else ConnState.NoDevice
            is UsbState.Unsupported -> ConnState.Unsupported(u.pid)
            UsbState.NeedsPermission -> ConnState.NeedsPermission
            UsbState.PermissionDenied -> ConnState.PermissionDenied
            UsbState.Connecting -> ConnState.Loading
            is UsbState.Connected -> error("unreachable")
        }
        _state.update { it.copy(conn = conn) }
    }

    private fun connect(device: HorizonDevice) {
        endSession()
        val s = Session(device)
        session = s
        activeId = null
        _state.update { it.copy(conn = ConnState.Loading, error = null, busy = false) }
        s.cs.launch {
            op(s) { d -> applySnapshot(s, d.readSnapshot()) } ?: return@launch
            if (session !== s) return@launch
            _state.update { it.copy(conn = ConnState.Ready) }
            publish(s)
        }
    }

    private fun endSession() {
        val s = session ?: return
        session = null
        s.job.cancel()
        _state.update { it.copy(busy = false) }
    }

    private fun lostDevice(error: UiError) {
        endSession()
        _state.update { it.copy(conn = ConnState.Disconnected, error = error) }
    }

    private fun readySession(): Session? = session?.takeIf { _state.value.conn == ConnState.Ready }

    // ---- Device I/O ----

    /**
     * Runs [block] under the connection's I/O lock and yields null on failure. Device loss →
     * Disconnected. Any other failure (timeout, failed transfer, unexpected exception) → [failed]:
     * [onFail] drops the op's unconfirmed display state, then the device is re-read.
     * [VerifyException] is the caller's to handle (inside [block], under the lock).
     */
    private suspend fun <T> op(
        s: Session,
        busy: Boolean = true,
        onFail: () -> Unit = {},
        block: suspend (HorizonDevice) -> T,
    ): T? {
        if (busy) setBusy(s, +1)
        try {
            return s.io.withLock { block(s.device) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: DeviceGoneException) {
            if (session === s) lostDevice(UiError.Disconnected)
        } catch (_: Exception) {
            // TimeoutException, IOException (failed transfer, device still attached), or a bug.
            failed(s, onFail)
        } finally {
            if (busy) setBusy(s, -1)
        }
        return null
    }

    /**
     * A write may have been partly applied: show the last confirmed values at once, then re-read
     * the device once. If that fails too, the device is treated as lost.
     */
    private suspend fun failed(s: Session, onFail: () -> Unit) {
        if (session !== s) return
        if (_state.value.conn == ConnState.Loading) return lostDevice(UiError.Timeout)
        setError(UiError.Timeout)
        onFail()
        _state.update { it.copy(dacOffset = s.knownDac, adcOffset = s.knownAdc, noiseCancelling = s.knownNc) }
        publish(s)
        try {
            s.io.withLock { applySnapshot(s, s.device.readSnapshot()) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: DeviceGoneException) {
            if (session === s) lostDevice(UiError.Disconnected)
        } catch (_: Exception) {
            if (session === s) lostDevice(UiError.Timeout)
        }
    }

    private fun setBusy(s: Session, delta: Int) {
        s.busy += delta
        if (session === s) _state.update { it.copy(busy = s.busy > 0) }
    }

    private fun setError(e: UiError) = _state.update { it.copy(error = e) }

    private fun applySnapshot(s: Session, snap: DeviceSnapshot) {
        s.deviceBands = snap.bands
        s.knownDac = snap.dacOffset
        s.knownAdc = snap.adcOffset
        s.knownNc = snap.noiseCancelling
        if (session !== s) return
        _state.update {
            it.copy(
                firmware = snap.firmware,
                dacOffset = snap.dacOffset,
                adcOffset = snap.adcOffset,
                noiseCancelling = snap.noiseCancelling,
            )
        }
        publish(s)
    }

    private fun mergeBands(s: Session, indices: Collection<Int>, back: List<DeviceBand>) {
        val merged = s.deviceBands.toMutableList()
        indices.zip(back).forEach { (i, b) -> if (i in merged.indices) merged[i] = b }
        s.deviceBands = merged
    }

    /** After a verify failure: show what the device reports. Runs under the I/O lock. */
    private suspend fun showDeviceAfterVerify(s: Session, d: HorizonDevice, e: VerifyException) {
        applySnapshot(s, d.readSnapshot())
        setError(UiError.VerifyFailed(e.mismatchedBands))
    }

    /** Recomputes displayed bands and the active preset. */
    private fun publish(s: Session) {
        if (session !== s || s.deviceBands.isEmpty()) return
        val shown = s.deviceBands.mapIndexed { i, d ->
            s.overlay[i]?.let { b -> runCatching { Responses.band(Packets.writeBand(i, b)) }.getOrNull() } ?: d
        }
        val presets = _state.value.presets
        val current = presets.firstOrNull { it.id == activeId }
        activeId = if (current != null && matchPreset(shown, listOf(current)) != null) current.id
        else matchPreset(shown, presets)?.id
        val bands = s.deviceBands.mapIndexed { i, d -> s.overlay[i] ?: d.band }
        _state.update { it.copy(bands = bands, activePresetId = activeId) }
    }

    /** Within the UI limits (a device band may hold e.g. fc 0 set by another app). */
    private fun inUiRange(b: EqBand): Boolean =
        b.fc in 20..20000 && b.q in 0.1..10.0 && b.gainDb in -10.0..10.0

    private fun encodable(b: EqBand): Boolean = try {
        Biquad.coefficientBytes(b.type, b.fc, b.q, b.gainDb)
        true
    } catch (_: UnstableFilterException) {
        false
    }

    // ---- Band edits ----

    /** Called continuously while dragging: throttled live write (latest wins) + debounced commit. */
    fun onBandDrag(index: Int, band: EqBand) = edit(index, band, live = true)

    fun onBandDragEnd(index: Int) {
        val s = readySession() ?: return
        scheduleCommit(s)
    }

    /** Edit from the band panel: debounced commit only. */
    fun setBand(index: Int, band: EqBand) = edit(index, band, live = false)

    private fun edit(index: Int, band: EqBand, live: Boolean) {
        val s = readySession() ?: return
        if (index !in s.deviceBands.indices) return
        val b = band.clamped()
        if (!encodable(b)) return rejectUnstable(s, index)
        s.overlay[index] = b
        s.dirty[index] = b
        if (live) {
            s.live[index] = b
            ensureLiveSender(s)
        }
        scheduleCommit(s)
        publish(s)
    }

    /**
     * The unstable value is not sent; the band shows the device's value. If the band was edited
     * (its live value may be in device RAM), the device value is re-sent, but only when it is
     * itself a valid, encodable UI value.
     */
    private fun rejectUnstable(s: Session, index: Int) {
        setError(UiError.UnstableFilter(index))
        s.live.remove(index)
        val dev = s.deviceBands[index].band
        if (index in s.dirty && inUiRange(dev) && encodable(dev)) {
            s.overlay[index] = dev
            s.dirty[index] = dev
            s.live[index] = dev
            ensureLiveSender(s)
            scheduleCommit(s)
        } else {
            s.dirty.remove(index)
            s.overlay.remove(index)
        }
        publish(s)
    }

    private fun ensureLiveSender(s: Session) {
        if (s.liveJob?.isActive == true) return
        s.liveJob = s.cs.launch {
            while (s.live.isNotEmpty()) {
                val batch = s.live.toMap()
                s.live.clear()
                op(s, busy = false) { d -> batch.forEach { (i, b) -> d.liveBand(i, b) } }
                delay(LIVE_THROTTLE_MS)
            }
        }
    }

    private fun scheduleCommit(s: Session) {
        s.debounceJob?.cancel()
        s.debounceJob = s.cs.launch {
            delay(COMMIT_DEBOUNCE_MS)
            // Separate job: a later edit restarts the debounce but never cancels a running commit.
            s.commitJob = s.cs.launch { commit(s) }
        }
    }

    /**
     * Commits pending edits now (skipping the debounce) and waits for it, so edits reach flash
     * before the USB claim is released (called from onStop). No-op when nothing is pending.
     */
    suspend fun flushPending() {
        val s = session ?: return
        val debouncing = s.debounceJob?.isActive == true
        s.debounceJob?.cancel()
        val job = if (debouncing || s.dirty.isNotEmpty()) {
            s.cs.launch { commit(s) }.also { s.commitJob = it }
        } else {
            s.commitJob
        }
        job?.join()
    }

    private suspend fun commit(s: Session) {
        var batch = emptyMap<Int, EqBand>()
        op(s, onFail = { settle(s, batch) }) { d ->
            batch = s.dirty.toMap()
            if (batch.isEmpty()) return@op
            batch.keys.forEach { s.live.remove(it) }
            try {
                val back = d.commitBands(batch)
                mergeBands(s, batch.keys, back)
                settle(s, batch)
                publish(s)
            } catch (e: VerifyException) {
                settle(s, batch)
                showDeviceAfterVerify(s, d, e)
            }
        }
    }

    /** Drops edits that the commit covered, unless the user changed them again meanwhile. */
    private fun settle(s: Session, batch: Map<Int, EqBand>) {
        for ((i, b) in batch) if (s.dirty[i] == b) {
            s.dirty.remove(i)
            s.overlay.remove(i)
        }
    }

    // ---- Presets ----

    fun selectPreset(id: String) {
        val s = readySession() ?: return
        val p = _state.value.presets.firstOrNull { it.id == id } ?: return
        val bad = p.bands.indexOfFirst { !encodable(it) }
        if (bad >= 0) return setError(UiError.UnstableFilter(bad))
        s.debounceJob?.cancel()
        s.live.clear()
        s.dirty.clear()
        s.overlay.clear()
        p.bands.forEachIndexed { i, b -> s.overlay[i] = b }
        activeId = p.id
        publish(s)
        s.presetJob?.cancel()
        s.presetJob = s.cs.launch {
            op(s, onFail = { s.overlay.keys.retainAll(s.dirty.keys) }) { d ->
                try {
                    val back = d.applyPreset(p.bands)
                    mergeBands(s, p.bands.indices.toList(), back)
                    s.overlay.keys.retainAll(s.dirty.keys)
                    publish(s)
                } catch (e: VerifyException) {
                    s.overlay.keys.retainAll(s.dirty.keys)
                    showDeviceAfterVerify(s, d, e)
                }
            }
        }
    }

    fun saveAsPreset(name: String) {
        readySession() ?: return
        val bands = _state.value.bands
        scope.launch {
            storage {
                val p = presetStore.create(name, bands)
                activeId = p.id
                _state.update { it.copy(activePresetId = p.id) }
            }
        }
    }

    fun renamePreset(id: String, name: String) {
        scope.launch { storage { presetStore.rename(id, name) } }
    }

    fun deletePreset(id: String) {
        scope.launch { storage { presetStore.delete(id) } }
    }

    private suspend fun storage(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: CorruptPresetsException) {
            setError(UiError.PresetStorage)
        }
    }

    // ---- Offsets, NC, reset ----

    fun setNoiseCancelling(on: Boolean) {
        val s = readySession() ?: return
        _state.update { it.copy(noiseCancelling = on) }
        s.ncJob?.cancel()
        s.ncJob = s.cs.launch {
            verified(s, onOk = { s.knownNc = on; _state.update { it.copy(noiseCancelling = on) } }) {
                it.setNoiseCancelling(on)
            }
        }
    }

    fun setDacOffset(db: Double) {
        val s = readySession() ?: return
        _state.update { it.copy(dacOffset = db) }
        s.dacJob?.cancel()
        s.dacJob = s.cs.launch {
            verified(s, onOk = { s.knownDac = db; _state.update { it.copy(dacOffset = db) } }) { it.setDacOffset(db) }
        }
    }

    fun setAdcOffset(db: Double) {
        val s = readySession() ?: return
        _state.update { it.copy(adcOffset = db) }
        s.adcJob?.cancel()
        s.adcJob = s.cs.launch {
            verified(s, onOk = { s.knownAdc = db; _state.update { it.copy(adcOffset = db) } }) { it.setAdcOffset(db) }
        }
    }

    private suspend fun verified(s: Session, onOk: () -> Unit, block: suspend (HorizonDevice) -> Unit) {
        op(s) { d ->
            try {
                block(d)
                onOk()
            } catch (e: VerifyException) {
                showDeviceAfterVerify(s, d, e)
            }
        }
    }

    /** Not cancellable by [selectPreset]: a preset chosen meanwhile is applied after the reset. */
    fun softReset() {
        val s = readySession() ?: return
        if (s.resetJob?.isActive == true) return
        s.debounceJob?.cancel()
        s.live.clear()
        s.dirty.clear()
        s.overlay.clear()
        s.presetJob?.cancel()
        s.resetJob = s.cs.launch {
            op(s) { d ->
                try {
                    applySnapshot(s, d.softReset())
                } catch (e: VerifyException) {
                    showDeviceAfterVerify(s, d, e)
                }
            }
        }
    }

    // ---- Misc ----

    fun setThemeMode(m: ThemeMode) {
        scope.launch { settings.setThemeMode(m) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun retryPermission() = requestPermission()

    private companion object {
        const val LIVE_THROTTLE_MS = 60L
        const val COMMIT_DEBOUNCE_MS = 500L
    }
}
