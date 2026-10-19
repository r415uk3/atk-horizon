package io.github.r415uk3.atkhorizon.ui

import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.data.ImportError as IE
import io.github.r415uk3.atkhorizon.ui.screens.statusContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShellMappingTest {
    @Test
    fun everyErrorHasItsOwnMessage() {
        val errors = listOf(
            UiError.VerifyFailed(listOf(1)), UiError.Disconnected, UiError.Timeout,
            UiError.UnstableFilter(2), UiError.PresetStorage,
        )
        val ids = errors.map { errorMessage(it).first }
        assertEquals(errors.size, ids.toSet().size)
        assertEquals(R.string.error_preset_storage to null, errorMessage(UiError.PresetStorage))
        // Band numbers are shown 1-based.
        assertEquals(R.string.error_unstable_filter to 3, errorMessage(UiError.UnstableFilter(2)))
    }

    @Test
    fun everyImportErrorHasItsOwnMessage() {
        val all = listOf(
            IE.NotJson, IE.TooLarge, IE.Unreadable, IE.WrongShape, IE.WrongType, IE.WrongFilterCount(9), IE.BadFilterIndex,
            IE.UnknownFilterType(2), IE.BandOutOfRange(2), IE.BandUnstable(2),
        )
        assertEquals(all.size, all.map { errorMessage(UiError.ImportFailed(it)).first }.toSet().size)
        assertEquals(R.string.import_error_band_range to 3, errorMessage(UiError.ImportFailed(IE.BandOutOfRange(2))))
    }

    @Test
    fun statusScreensForEveryNonReadyState() {
        assertNull(statusContent(ConnState.Ready))
        assertNull(statusContent(ConnState.Loading))
        listOf(ConnState.NoDevice, ConnState.NeedsPermission, ConnState.PermissionDenied, ConnState.Disconnected)
            .forEach { assertNotNull(it.toString(), statusContent(it)) }
        val unsupported = statusContent(ConnState.Unsupported(0x129F))!!
        assertEquals(R.string.status_unsupported_title, unsupported.title)
        assertEquals("129F", unsupported.titleArg)
        // Permission states and Disconnected offer a button; the rest need a physical action.
        assertNotNull(statusContent(ConnState.NeedsPermission)!!.action)
        assertNotNull(statusContent(ConnState.PermissionDenied)!!.action)
        assertEquals(R.string.status_disconnected_action, statusContent(ConnState.Disconnected)!!.action)
        assertNull(statusContent(ConnState.NoDevice)!!.action)
        assertNull(statusContent(ConnState.Unsupported(0x129F))!!.action)
    }
}
