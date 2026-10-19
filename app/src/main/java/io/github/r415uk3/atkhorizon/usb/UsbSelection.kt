package io.github.r415uk3.atkhorizon.usb

/** Pure (Android-free) USB identification and HID interface selection, unit-tested. */

sealed interface DeviceKind {
    data object Horizon : DeviceKind
    /** An ATK device that is not the Horizon (e.g. KTMicro PID 0x129F): shown as unsupported. */
    data class OtherAtk(val pid: Int) : DeviceKind
    data object Foreign : DeviceKind
}

object UsbIds {
    const val VENDOR_ATK = 0x373B
    const val PRODUCT_HORIZON = 0x120C

    fun classify(vendorId: Int, productId: Int): DeviceKind = when {
        vendorId != VENDOR_ATK -> DeviceKind.Foreign
        productId == PRODUCT_HORIZON -> DeviceKind.Horizon
        else -> DeviceKind.OtherAtk(productId)
    }
}

/** Mirrors the parts of android.hardware.usb.UsbEndpoint the chooser needs. */
data class EndpointInfo(val address: Int, val type: Int, val isIn: Boolean) {
    companion object {
        /** Same value as UsbConstants.USB_ENDPOINT_XFER_INT. */
        const val XFER_INT = 3
    }
}

/** Mirrors android.hardware.usb.UsbInterface plus the HID report descriptor, if it could be read. */
class InterfaceInfo(
    val id: Int,
    val interfaceClass: Int,
    val endpoints: List<EndpointInfo>,
    val reportDescriptor: ByteArray?,
) {
    fun copy(reportDescriptor: ByteArray?) = InterfaceInfo(id, interfaceClass, endpoints, reportDescriptor)
    val hasInterruptIn get() = endpoints.any { it.type == EndpointInfo.XFER_INT && it.isIn }
    val hasInterruptOut get() = endpoints.any { it.type == EndpointInfo.XFER_INT && !it.isIn }
}

/** Minimal HID report descriptor walker: collects Usage Page and Report ID global items. */
object HidReportDescriptor {
    fun usagePages(d: ByteArray): Set<Int> = globals(d, tag = 0x0)
    fun reportIds(d: ByteArray): Set<Int> = globals(d, tag = 0x8)

    private fun globals(d: ByteArray, tag: Int): Set<Int> {
        val out = linkedSetOf<Int>()
        var i = 0
        while (i < d.size) {
            val prefix = d[i].toInt() and 0xFF
            if (prefix == 0xFE) { // long item: FE, size, tag, data...
                if (i + 1 >= d.size) break
                i += 3 + (d[i + 1].toInt() and 0xFF)
                continue
            }
            val size = when (prefix and 0x3) { 3 -> 4; else -> prefix and 0x3 }
            if (i + size >= d.size) break // truncated item
            val type = (prefix shr 2) and 0x3
            if (type == 1 && (prefix shr 4) == tag) {
                var v = 0
                for (k in 0 until size) v = v or ((d[i + 1 + k].toInt() and 0xFF) shl (8 * k))
                out += v
            }
            i += 1 + size
        }
        return out
    }
}

object HidInterfaceChooser {
    const val HID_CLASS = 3
    const val USAGE_PAGE_CONSUMER = 0x000C
    const val VENDOR_REPORT_ID = 0x4B

    /**
     * Picks the HID interface carrying the vendor report. Ranking: declares report ID 0x4B,
     * then usage page 0x000C (per spec), then has an interrupt IN endpoint, then an interrupt
     * OUT endpoint, then the lowest interface number. The hardware session (docs/hardware-notes.md,
     * item 1) confirms this choice against the real descriptors logged by HorizonConnector.
     */
    fun choose(interfaces: List<InterfaceInfo>): InterfaceInfo? =
        interfaces.filter { it.interfaceClass == HID_CLASS }.sortedWith(
            compareByDescending<InterfaceInfo> { it.reportDescriptor?.let { d -> VENDOR_REPORT_ID in HidReportDescriptor.reportIds(d) } == true }
                .thenByDescending { it.reportDescriptor?.let { d -> USAGE_PAGE_CONSUMER in HidReportDescriptor.usagePages(d) } == true }
                .thenByDescending { it.hasInterruptIn }
                .thenByDescending { it.hasInterruptOut }
                .thenBy { it.id },
        ).firstOrNull()
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
