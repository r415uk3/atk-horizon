package io.github.r415uk3.atkhorizon.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbSelectionTest {
    private fun desc(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private val intIn = EndpointInfo(address = 0x81, type = EndpointInfo.XFER_INT, isIn = true)
    private val intOut = EndpointInfo(address = 0x02, type = EndpointInfo.XFER_INT, isIn = false)

    @Test fun classifyHorizon() = assertEquals(DeviceKind.Horizon, UsbIds.classify(0x373B, 0x120C))
    @Test fun classifyOtherAtkIsUnsupported() = assertEquals(DeviceKind.OtherAtk(0x129F), UsbIds.classify(0x373B, 0x129F))
    @Test fun classifyForeign() = assertEquals(DeviceKind.Foreign, UsbIds.classify(0x046D, 0x120C))

    @Test fun parsesUsagePagesAndReportIds() {
        // Usage Page (Consumer), Usage, Collection, Report ID 0x4B, ..., Usage Page 0xFF00 (2-byte), Report ID 1
        val d = desc(0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, 0x4B, 0x95, 0x3F, 0xC0, 0x06, 0x00, 0xFF, 0x85, 0x01)
        assertEquals(setOf(0x000C, 0xFF00), HidReportDescriptor.usagePages(d))
        assertEquals(setOf(0x4B, 0x01), HidReportDescriptor.reportIds(d))
    }

    @Test fun skipsLongItemsAndTruncation() {
        val d = desc(0xFE, 0x02, 0x10, 0x85, 0x99, 0x05, 0x0C, 0x06, 0x01)
        assertEquals(setOf(0x000C), HidReportDescriptor.usagePages(d))
        assertEquals(emptySet<Int>(), HidReportDescriptor.reportIds(d))
    }

    @Test fun chooseIgnoresNonHid() {
        val audio = InterfaceInfo(0, 1, listOf(intIn), null)
        assertNull(HidInterfaceChooser.choose(listOf(audio)))
    }

    @Test fun choosePrefersVendorReportId() {
        val buttons = InterfaceInfo(3, 3, listOf(intIn), desc(0x05, 0x0C, 0x85, 0x01))
        val vendor = InterfaceInfo(4, 3, listOf(intIn), desc(0x06, 0x00, 0xFF, 0x85, 0x4B))
        assertEquals(4, HidInterfaceChooser.choose(listOf(buttons, vendor))?.id)
    }

    @Test fun choosePrefersConsumerPageThenInterruptIn() {
        val noIn = InterfaceInfo(2, 3, listOf(intOut), desc(0x05, 0x0C))
        val consumer = InterfaceInfo(3, 3, listOf(intIn), desc(0x05, 0x0C))
        val other = InterfaceInfo(1, 3, listOf(intIn, intOut), desc(0x05, 0x01))
        assertEquals(3, HidInterfaceChooser.choose(listOf(other, noIn, consumer))?.id)
        assertEquals(1, HidInterfaceChooser.choose(listOf(other, noIn.copy(reportDescriptor = null)))?.id)
    }

    @Test fun chooseFallsBackToLowestId() {
        val a = InterfaceInfo(5, 3, listOf(intIn), null)
        val b = InterfaceInfo(2, 3, listOf(intIn), null)
        assertEquals(2, HidInterfaceChooser.choose(listOf(a, b))?.id)
    }

    @Test fun hex() = assertEquals("4b 80 0c ff", desc(0x4B, 0x80, 0x0C, 0xFF).toHex())
}
