package io.github.r415uk3.atkhorizon.ui

import io.github.r415uk3.atkhorizon.data.BuiltInPresets
import io.github.r415uk3.atkhorizon.data.InMemoryPresetStore
import io.github.r415uk3.atkhorizon.data.InMemorySettingsStore
import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FakeHorizon
import io.github.r415uk3.atkhorizon.protocol.FilterType
import io.github.r415uk3.atkhorizon.protocol.HorizonDevice
import io.github.r415uk3.atkhorizon.protocol.HorizonLink
import io.github.r415uk3.atkhorizon.protocol.Packets
import io.github.r415uk3.atkhorizon.protocol.Responses
import io.github.r415uk3.atkhorizon.usb.UsbState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HorizonViewModelTest {
    private class Rig(val scope: TestScope, initial: UsbState? = null) {
        val fake = FakeHorizon { scope.currentTime }
        val device = HorizonDevice(HorizonLink(fake, scope.backgroundScope))
        val usb = MutableStateFlow(initial ?: UsbState.Connected(device))
        val presets = InMemoryPresetStore()
        val settings = InMemorySettingsStore()
        var permissionRequests = 0
        var reconnects = 0
        val lost = mutableListOf<HorizonDevice>()

        // Not backgroundScope: advanceUntilIdle ignores background work. Cancelled when the test ends.
        val vmScope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + Job()).also { vs ->
            scope.backgroundScope.coroutineContext.job.invokeOnCompletion { vs.cancel() }
        }
        val vm = HorizonViewModel(usb, presets, settings, { permissionRequests++ }, vmScope, { reconnects++ }, { lost += it })
        val state get() = vm.state.value

        fun newDevice(): Pair<FakeHorizon, HorizonDevice> {
            val f = FakeHorizon { scope.currentTime }
            return f to HorizonDevice(HorizonLink(f, scope.backgroundScope))
        }
    }

    private fun preset(id: String) = BuiltInPresets.all.first { it.id == id }
    private fun encoded(bands: List<EqBand>) = bands.mapIndexed { i, b -> Responses.band(Packets.writeBand(i, b)) }
    private fun saves(f: FakeHorizon) = f.log.count { it.dir == 0x01 && it.cmd == 0x01 }

    @Test
    fun loadsSnapshotOnConnect() = runTest {
        val r = Rig(this)
        r.fake.firmware = "1.2.3"
        r.fake.nc = true
        r.fake.dacOffset = 1.5
        r.fake.adcOffset = -2.0
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
        assertEquals("1.2.3", r.state.firmware)
        assertEquals(EqBand.DEFAULTS, r.state.bands)
        assertTrue(r.state.noiseCancelling)
        assertEquals(1.5, r.state.dacOffset!!, 0.0)
        assertEquals(-2.0, r.state.adcOffset!!, 0.0)
        assertEquals(BuiltInPresets.all, r.state.presets)
        assertFalse(r.state.busy)
        assertNull(r.state.error)
    }

    @Test
    fun presetMatchedOnConnect() = runTest {
        val r = Rig(this, UsbState.NoDevice)
        r.device.applyPreset(preset("cs2").bands)
        r.usb.value = UsbState.Connected(r.device)
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
        assertEquals("cs2", r.state.activePresetId)
        assertEquals(preset("cs2").bands, r.state.bands)
    }

    @Test
    fun rapidPresetSwitchSavesOnce() = runTest {
        val r = Rig(this)
        runCurrent()
        r.fake.log.clear()
        r.vm.selectPreset("cs2")
        advanceTimeBy(40)
        r.vm.selectPreset("rock")
        advanceTimeBy(40)
        r.vm.selectPreset("apex")
        assertEquals("apex", r.state.activePresetId)
        advanceUntilIdle()
        val apex = encoded(preset("apex").bands)
        assertEquals(apex, r.fake.bands)
        assertEquals(apex, r.fake.flashBands)
        assertEquals(1, saves(r.fake))
        assertEquals("apex", r.state.activePresetId)
        assertEquals(preset("apex").bands, r.state.bands)
        assertNull(r.state.error)
        assertFalse(r.state.busy)
    }

    @Test
    fun dragThrottlesLiveWritesAndSavesOnceAfterRelease() = runTest {
        val r = Rig(this)
        runCurrent()
        r.fake.log.clear()
        val start = currentTime
        repeat(40) { k ->
            r.vm.onBandDrag(3, EqBand(FilterType.Peak, 250, 0.75, k * 0.2))
            advanceTimeBy(10)
        }
        r.vm.onBandDragEnd(3)
        val last = EqBand(FilterType.Peak, 250, 0.75, 7.8)
        assertEquals(last, r.state.bands[3])
        assertNull(r.state.activePresetId)
        advanceTimeBy(450)
        runCurrent()
        // Only live writes (0x09 + 0x0A per liveBand) so far.
        val liveSends = r.fake.log.count { it.dir == 0x01 && it.cmd == 0x0A }
        assertTrue("live sends: $liveSends", liveSends in 1..8)
        assertEquals(0, saves(r.fake))
        assertEquals(last, r.fake.bands[3].band)
        advanceUntilIdle()
        assertEquals(1, saves(r.fake))
        val save = r.fake.log.single { it.dir == 0x01 && it.cmd == 0x01 }
        assertTrue(save.atMs - start >= 390 + 500 + 1000)
        assertEquals(last, r.fake.flashBands[3].band)
        assertEquals(last, r.state.bands[3])
        assertNull(r.state.error)
    }

    @Test
    fun verifyFailureSurfacesErrorAndShowsDeviceValues() = runTest {
        val r = Rig(this)
        runCurrent()
        r.fake.dropWritesFor = setOf(2)
        r.vm.setBand(2, EqBand(FilterType.Peak, 125, 2.0, 5.0))
        r.vm.setBand(4, EqBand(FilterType.Peak, 500, 2.0, -3.0))
        assertEquals(5.0, r.state.bands[2].gainDb, 0.0)
        advanceUntilIdle()
        assertEquals(UiError.VerifyFailed(listOf(2)), r.state.error)
        assertEquals(r.fake.bands.map { it.band }, r.state.bands)
        assertEquals(EqBand.DEFAULTS[2], r.state.bands[2])
        assertEquals(-3.0, r.state.bands[4].gainDb, 0.0)
        r.vm.dismissError()
        assertNull(r.state.error)
    }

    @Test
    fun unstableBandShowsErrorAndIsNotSent() = runTest {
        val r = Rig(this)
        runCurrent()
        r.fake.log.clear()
        r.vm.onBandDrag(0, EqBand(FilterType.HighShelf, 20, 5.0, 10.0))
        r.vm.onBandDragEnd(0)
        r.vm.setBand(1, EqBand(FilterType.HighShelf, 20, 10.0, -10.0))
        assertEquals(UiError.UnstableFilter(1), r.state.error)
        assertEquals(EqBand.DEFAULTS, r.state.bands)
        advanceUntilIdle()
        assertTrue(r.fake.log.none { it.dir == 0x01 })
        assertEquals(EqBand.DEFAULTS, r.state.bands)
    }

    @Test
    fun disconnectMovesToDisconnectedState() = runTest {
        val r = Rig(this)
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
        r.fake.unplugAfter = r.fake.log.size + 3
        r.vm.selectPreset("rock")
        advanceUntilIdle()
        assertEquals(ConnState.Disconnected, r.state.conn)
        assertEquals(UiError.Disconnected, r.state.error)
        assertFalse(r.state.busy)
        assertEquals(listOf(r.device), r.lost)

        // The connector notices the detach: stays Disconnected (there was a connection).
        r.usb.value = UsbState.NoDevice
        runCurrent()
        assertEquals(ConnState.Disconnected, r.state.conn)

        // Re-plug: state is re-read from the new device.
        val (f2, d2) = r.newDevice()
        f2.firmware = "2.0"
        r.usb.value = UsbState.Connected(d2)
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
        assertEquals("2.0", r.state.firmware)
        assertEquals(EqBand.DEFAULTS, r.state.bands)
        assertNull(r.state.error)

        // A plain detach while Ready also shows Disconnected.
        r.usb.value = UsbState.NoDevice
        runCurrent()
        assertEquals(ConnState.Disconnected, r.state.conn)
    }

    @Test
    fun noDeviceWithoutPriorConnection() = runTest {
        val r = Rig(this, UsbState.NoDevice)
        runCurrent()
        assertEquals(ConnState.NoDevice, r.state.conn)
        r.usb.value = UsbState.Unsupported(0x129F)
        runCurrent()
        assertEquals(ConnState.Unsupported(0x129F), r.state.conn)
    }

    @Test
    fun connectingShowsLoadingAndFailedOpenShowsDisconnected() = runTest {
        val r = Rig(this, UsbState.NoDevice)
        runCurrent()
        r.usb.value = UsbState.Connecting
        runCurrent()
        assertEquals(ConnState.Loading, r.state.conn)
        // The open failed: not "plug the headset in", but Disconnected, with a retry.
        r.usb.value = UsbState.NoDevice
        runCurrent()
        assertEquals(ConnState.Disconnected, r.state.conn)
        r.vm.retryConnect()
        assertEquals(1, r.reconnects)
    }

    @Test
    fun permissionDeniedState() = runTest {
        val r = Rig(this, UsbState.NeedsPermission)
        runCurrent()
        assertEquals(ConnState.NeedsPermission, r.state.conn)
        r.usb.value = UsbState.PermissionDenied
        runCurrent()
        assertEquals(ConnState.PermissionDenied, r.state.conn)
        r.vm.retryPermission()
        assertEquals(1, r.permissionRequests)
        r.usb.value = UsbState.Connected(r.device)
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
    }

    @Test
    fun softResetShowsDefaults() = runTest {
        val r = Rig(this, UsbState.NoDevice)
        r.device.applyPreset(preset("delta").bands)
        r.device.setDacOffset(2.0)
        r.device.setAdcOffset(-1.0)
        r.usb.value = UsbState.Connected(r.device)
        runCurrent()
        assertEquals("delta", r.state.activePresetId)
        assertEquals(2.0, r.state.dacOffset!!, 0.0)
        r.vm.softReset()
        advanceUntilIdle()
        assertEquals(EqBand.DEFAULTS, r.state.bands)
        assertEquals(0.0, r.state.dacOffset!!, 0.0)
        assertEquals(0.0, r.state.adcOffset!!, 0.0)
        assertEquals("default", r.state.activePresetId)
        assertEquals(encoded(EqBand.DEFAULTS), r.fake.flashBands)
        assertNull(r.state.error)
    }

    @Test
    fun savedPresetBecomesActive() = runTest {
        val r = Rig(this)
        runCurrent()
        r.vm.setBand(5, EqBand(FilterType.Peak, 1000, 1.0, 4.5))
        advanceUntilIdle()
        assertNull(r.state.activePresetId)
        r.vm.saveAsPreset("Mine")
        advanceUntilIdle()
        val saved = r.state.presets.single { it.name == "Mine" }
        assertEquals(saved.id, r.state.activePresetId)
        assertEquals(r.state.bands, saved.bands)

        r.vm.renamePreset(saved.id, "Mine 2")
        advanceUntilIdle()
        assertEquals("Mine 2", r.state.presets.single { it.id == saved.id }.name)
        assertEquals(saved.id, r.state.activePresetId)

        r.vm.deletePreset(saved.id)
        advanceUntilIdle()
        assertTrue(r.state.presets.none { it.id == saved.id })
        assertNull(r.state.activePresetId)
    }

    @Test
    fun savedPresetMatchingBuiltInStaysActive() = runTest {
        val r = Rig(this)
        runCurrent()
        assertEquals("default", r.state.activePresetId)
        r.vm.saveAsPreset("Flat")
        advanceUntilIdle()
        val saved = r.state.presets.single { it.name == "Flat" }
        assertEquals(saved.id, r.state.activePresetId)
    }

    @Test
    fun corruptPresetStorageSurfacesError() = runTest {
        val r = Rig(this)
        val corrupt = object : io.github.r415uk3.atkhorizon.data.PresetStore by r.presets {
            override suspend fun create(name: String, bands: List<EqBand>) =
                throw io.github.r415uk3.atkhorizon.data.CorruptPresetsException()
        }
        val vm = HorizonViewModel(r.usb, corrupt, r.settings, {}, r.vmScope)
        runCurrent()
        vm.saveAsPreset("x")
        runCurrent()
        assertEquals(UiError.PresetStorage, vm.state.value.error)
    }

    @Test
    fun timeoutSurfacesError() = runTest {
        val r = Rig(this)
        runCurrent()
        // A device that never answers: every read times out (4 x 500 ms).
        val silent = object : io.github.r415uk3.atkhorizon.protocol.HidTransport by r.fake {
            override val incoming = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>()
        }
        val dev = HorizonDevice(HorizonLink(silent, backgroundScope))
        r.usb.value = UsbState.Connected(dev)
        advanceUntilIdle()
        assertEquals(UiError.Timeout, r.state.error)
        // Never left on a spinner.
        assertEquals(ConnState.Disconnected, r.state.conn)
        assertFalse(r.state.busy)
        // The USB layer is told to drop this device (release the claim, so the inline buttons work).
        assertEquals(listOf(dev), r.lost)
        // "Try again" asks for a fresh connection; a new device object is read from scratch.
        r.vm.retryConnect()
        assertEquals(1, r.reconnects)
        val (_, d2) = r.newDevice()
        r.usb.value = UsbState.Connected(d2)
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
    }

    /** Transport wrapper over the rig's fake that can fail writes (or everything) on demand. */
    private class Flaky(val r: Rig) : io.github.r415uk3.atkhorizon.protocol.HidTransport by r.fake {
        var failWrites = false
        var failAll = false
        var throwable: () -> Exception = { java.io.IOException("USB write failed (-1)") }
        override suspend fun send(payload: ByteArray) {
            if (failAll || (failWrites && payload[0] == 0x01.toByte())) throw throwable()
            r.fake.send(payload)
        }
    }

    private fun TestScope.flakyRig(): Pair<Rig, Flaky> {
        val r = Rig(this, UsbState.NoDevice)
        val flaky = Flaky(r)
        r.usb.value = UsbState.Connected(HorizonDevice(HorizonLink(flaky, backgroundScope)))
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
        return r to flaky
    }

    @Test
    fun failedTransferSurfacesTimeoutWithoutCrashing() = runTest {
        val (r, flaky) = flakyRig()
        flaky.failWrites = true
        r.vm.selectPreset("rock")
        advanceUntilIdle()
        assertEquals(UiError.Timeout, r.state.error)
        assertEquals(ConnState.Ready, r.state.conn)
        assertFalse(r.state.busy)
        // The curve shows what is really in the device, not the preset that failed to apply.
        assertEquals(r.fake.bands.map { it.band }, r.state.bands)
        assertEquals("default", r.state.activePresetId)
    }

    @Test
    fun commitTimeoutShowsDeviceValues() = runTest {
        val (r, flaky) = flakyRig()
        r.vm.setBand(4, EqBand(FilterType.Peak, 500, 2.0, -3.0))
        assertEquals(-3.0, r.state.bands[4].gainDb, 0.0)
        flaky.failWrites = true
        advanceUntilIdle()
        assertEquals(UiError.Timeout, r.state.error)
        assertEquals(ConnState.Ready, r.state.conn)
        assertEquals(r.fake.bands.map { it.band }, r.state.bands)
        assertEquals(EqBand.DEFAULTS, r.state.bands)
        assertEquals("default", r.state.activePresetId)
        // Nothing left pending: no retry loop.
        r.fake.log.clear()
        advanceUntilIdle()
        assertTrue(r.fake.log.isEmpty())
    }

    @Test
    fun offsetAndNcTimeoutRestoreDeviceValues() = runTest {
        val (r, flaky) = flakyRig()
        flaky.failWrites = true
        r.vm.setDacOffset(1.5)
        r.vm.setAdcOffset(-2.0)
        r.vm.setNoiseCancelling(true)
        assertEquals(1.5, r.state.dacOffset!!, 0.0)
        advanceUntilIdle()
        assertEquals(UiError.Timeout, r.state.error)
        assertEquals(0.0, r.state.dacOffset!!, 0.0)
        assertEquals(0.0, r.state.adcOffset!!, 0.0)
        assertFalse(r.state.noiseCancelling)
        assertEquals(ConnState.Ready, r.state.conn)
    }

    @Test
    fun failedResyncMeansDisconnected() = runTest {
        val (r, flaky) = flakyRig()
        flaky.failAll = true
        r.vm.selectPreset("rock")
        advanceUntilIdle()
        assertEquals(ConnState.Disconnected, r.state.conn)
        assertEquals(UiError.Timeout, r.state.error)
        assertFalse(r.state.busy)
    }

    @Test
    fun unexpectedExceptionDoesNotCrash() = runTest {
        val (r, flaky) = flakyRig()
        flaky.throwable = { IllegalStateException("bug") }
        flaky.failWrites = true
        r.vm.setBand(1, EqBand(FilterType.Peak, 62, 1.0, 2.0))
        advanceUntilIdle()
        assertEquals(UiError.Timeout, r.state.error)
        assertEquals(ConnState.Ready, r.state.conn)
        assertEquals(EqBand.DEFAULTS, r.state.bands)
    }

    @Test
    fun revertToInvalidDeviceBandDoesNotCrash() = runTest {
        val r = Rig(this, UsbState.NoDevice)
        // Band 2: fc 0 (e.g. set by the official app). Band 3: an unstable high shelf.
        val fc0 = Packets.writeBand(2, EqBand(FilterType.Peak, 125, 0.75, 0.0)).also { it[27] = 0; it[28] = 0 }
        val shelf = Packets.writeBand(3, EqBand(FilterType.Peak, 20, 5.0, 10.0)).also { it[33] = FilterType.HighShelf.code.toByte() }
        r.fake.send(fc0)
        r.fake.send(shelf)
        r.usb.value = UsbState.Connected(r.device)
        runCurrent()
        assertEquals(0, r.state.bands[2].fc)
        assertEquals(FilterType.HighShelf, r.state.bands[3].type)
        r.fake.log.clear()
        for (i in 2..3) {
            r.vm.onBandDrag(i, EqBand(FilterType.Peak, 300, 1.0, 1.0))
            r.vm.onBandDrag(i, EqBand(FilterType.HighShelf, 20, 5.0, 10.0))
            assertEquals(UiError.UnstableFilter(i), r.state.error)
        }
        r.vm.onBandDragEnd(3)
        advanceUntilIdle()
        assertEquals(ConnState.Ready, r.state.conn)
        assertEquals(UiError.UnstableFilter(3), r.state.error)
        assertEquals(r.fake.bands.map { it.band }, r.state.bands)
        assertEquals(0, r.state.bands[2].fc)
        // The invalid device values were never re-sent; nothing was saved.
        assertTrue(r.fake.log.none { it.dir == 0x01 && it.cmd == 0x01 })
    }

    @Test
    fun softResetNotCancelledByPresetSelect() = runTest {
        val r = Rig(this, UsbState.NoDevice)
        r.device.setDacOffset(2.0)
        r.device.setAdcOffset(-1.0)
        r.usb.value = UsbState.Connected(r.device)
        runCurrent()
        r.vm.softReset()
        advanceTimeBy(500) // inside the reset: waiting for its flash save, offsets not yet written
        r.vm.selectPreset("rock")
        advanceUntilIdle()
        assertEquals(0.0, r.fake.dacOffset, 0.0)
        assertEquals(0.0, r.fake.adcOffset, 0.0)
        assertEquals(0.0, r.state.dacOffset!!, 0.0)
        assertEquals(0.0, r.state.adcOffset!!, 0.0)
        val rock = encoded(preset("rock").bands)
        assertEquals(rock, r.fake.flashBands)
        assertEquals(preset("rock").bands, r.state.bands)
        assertEquals("rock", r.state.activePresetId)
        assertNull(r.state.error)
    }

    @Test
    fun flushPendingCommitsBeforeDebounce() = runTest {
        val r = Rig(this)
        runCurrent()
        r.fake.log.clear()
        val edit = EqBand(FilterType.Peak, 2000, 1.2, -4.0)
        r.vm.onBandDrag(6, edit)
        advanceTimeBy(100)
        r.vm.flushPending()
        assertEquals(1, saves(r.fake))
        assertEquals(edit, r.fake.flashBands[6].band)
        advanceUntilIdle()
        assertEquals(1, saves(r.fake))
        assertEquals(edit, r.state.bands[6])
        // Nothing pending: no-op.
        r.vm.flushPending()
        advanceUntilIdle()
        assertEquals(1, saves(r.fake))
    }

    @Test
    fun flushCompletesWhenViewModelIsClearedMidCommit() = runTest {
        val r = Rig(this)
        runCurrent()
        r.fake.log.clear()
        val edit = EqBand(FilterType.Peak, 2000, 1.2, -4.0)
        r.vm.onBandDrag(6, edit)
        advanceTimeBy(100)
        // As AtkApp does: the flush runs on the app scope (here the test scope), not the VM scope.
        val flush = launch { r.vm.flushPending() }
        advanceTimeBy(300) // inside the commit's 1000 ms pause, before 0x01
        assertEquals(0, saves(r.fake))
        r.vmScope.cancel() // activity finishing: the ViewModel is cleared
        advanceUntilIdle()
        assertTrue(flush.isCompleted)
        assertEquals(1, saves(r.fake))
        assertEquals(edit, r.fake.flashBands[6].band)
    }

    @Test
    fun runningDebouncedCommitCompletesWhenViewModelIsCleared() = runTest {
        val r = Rig(this)
        runCurrent()
        r.fake.log.clear()
        val edit = EqBand(FilterType.Peak, 4000, 0.9, 3.0)
        r.vm.setBand(7, edit)
        advanceTimeBy(800) // debounce (500) elapsed, commit in its 1000 ms pause
        assertEquals(0, saves(r.fake))
        r.vmScope.cancel()
        advanceUntilIdle()
        assertEquals(1, saves(r.fake))
        assertEquals(edit, r.fake.flashBands[7].band)
    }

    @Test
    fun offsetsAndNoiseCancelling() = runTest {
        val r = Rig(this)
        runCurrent()
        r.vm.setDacOffset(1.5)
        r.vm.setAdcOffset(-0.5)
        r.vm.setNoiseCancelling(true)
        advanceUntilIdle()
        assertEquals(1.5, r.fake.dacOffset, 0.0)
        assertEquals(-0.5, r.fake.adcOffset, 0.0)
        assertTrue(r.fake.nc)
        assertEquals(1.5, r.state.dacOffset!!, 0.0)
        assertEquals(-0.5, r.state.adcOffset!!, 0.0)
        assertTrue(r.state.noiseCancelling)

        r.fake.dropNcWrites = true
        r.vm.setNoiseCancelling(false)
        advanceUntilIdle()
        assertEquals(UiError.VerifyFailed(emptyList()), r.state.error)
        assertTrue(r.state.noiseCancelling)
    }

    @Test
    fun themeModePersists() = runTest {
        val r = Rig(this)
        runCurrent()
        r.vm.setThemeMode(io.github.r415uk3.atkhorizon.data.ThemeMode.Dark)
        runCurrent()
        assertEquals(io.github.r415uk3.atkhorizon.data.ThemeMode.Dark, r.state.themeMode)
    }

    @Test
    fun implausibleOffsetsAreNotShownAndBlockEqWrites() = runTest {
        val r = Rig(this, UsbState.NoDevice)
        r.fake.dacOffset = 50.0
        r.fake.adcOffset = -1.5
        r.usb.value = UsbState.Connected(r.device)
        runCurrent()
        assertEquals(ConnState.Ready, r.state.conn)
        assertNull(r.state.dacOffset)
        assertEquals(-1.5, r.state.adcOffset!!, 0.0)
        assertEquals(UiError.SuspiciousOffset(changeBlocked = false), r.state.error)

        r.fake.log.clear()
        r.vm.setBand(2, EqBand(FilterType.Peak, 125, 2.0, 5.0))
        advanceUntilIdle()
        assertEquals(UiError.SuspiciousOffset(changeBlocked = true), r.state.error)
        assertTrue(r.fake.log.none { it.dir == 0x01 })
        assertEquals(EqBand.DEFAULTS, r.state.bands)
        assertEquals(ConnState.Ready, r.state.conn)

        r.vm.selectPreset("rock")
        advanceUntilIdle()
        assertEquals(UiError.SuspiciousOffset(changeBlocked = true), r.state.error)
        assertTrue(r.fake.log.none { it.dir == 0x01 })
        assertEquals(EqBand.DEFAULTS, r.state.bands)
        assertEquals("default", r.state.activePresetId)

        // Setting the offset explicitly is the way out; then EQ edits go through.
        r.vm.setDacOffset(0.0)
        advanceUntilIdle()
        assertEquals(0.0, r.state.dacOffset!!, 0.0)
        r.vm.selectPreset("rock")
        advanceUntilIdle()
        assertEquals(encoded(preset("rock").bands), r.fake.flashBands)
    }
}
