package io.github.r415uk3.atkhorizon.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import io.github.r415uk3.atkhorizon.protocol.HidTransport
import io.github.r415uk3.atkhorizon.protocol.HorizonDevice
import io.github.r415uk3.atkhorizon.protocol.HorizonLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job

sealed interface UsbState {
    data object NoDevice : UsbState
    /** An ATK device (VID 0x373B) other than the Horizon. */
    data class Unsupported(val pid: Int) : UsbState
    data object NeedsPermission : UsbState
    data object PermissionDenied : UsbState
    data class Connected(val device: HorizonDevice) : UsbState
}

/**
 * Tracks the Horizon over attach/detach/permission and owns the USB connection.
 *
 * [open] marks the connection as wanted: whenever the Horizon is attached with permission, the
 * HID interface is claimed (`claimInterface(iface, true)`) and the state becomes [UsbState.Connected].
 * [release] releases the interface, closes the connection and stops reconnecting; while released
 * the state reports [UsbState.NoDevice] for a permitted Horizon (the app is in the background).
 * All public methods must be called on the main thread (the receiver also runs there).
 */
class HorizonConnector(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val usb = app.getSystemService(UsbManager::class.java)
    private val permissionAction = "${app.packageName}.USB_PERMISSION"

    private val _state = MutableStateFlow<UsbState>(UsbState.NoDevice)
    val state: StateFlow<UsbState> = _state.asStateFlow()

    private var wanted = false
    private var denied = false
    private var session: Session? = null

    private class Session(
        val device: UsbDevice,
        val conn: UsbDeviceConnection,
        val iface: UsbInterface,
        val transport: HidTransport,
        val horizon: HorizonDevice,
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            Log.d(TAG, "broadcast ${intent.action} ${device?.let(::describe)}")
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> denied = false
                UsbManager.ACTION_USB_DEVICE_DETACHED ->
                    if (device != null && device.deviceName == session?.device?.deviceName) teardown()
                permissionAction -> denied = !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            }
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
        }
        // NOT_EXPORTED: only our own permission PendingIntent and protected system broadcasts
        // (attach/detach) reach this receiver.
        app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        scope.coroutineContext.job.invokeOnCompletion {
            runCatching { app.unregisterReceiver(receiver) }
            teardown()
        }
        refresh()
    }

    fun requestPermission() {
        val device = findHorizon() ?: return refresh()
        denied = false
        // Mutable so the system can add EXTRA_DEVICE / EXTRA_PERMISSION_GRANTED; explicit
        // (package-scoped) intent as Android 14+ requires for mutable PendingIntents.
        val intent = Intent(permissionAction).setPackage(app.packageName)
        val pi = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        Log.d(TAG, "requesting permission for ${describe(device)}")
        usb.requestPermission(device, pi)
        refresh()
    }

    fun open() {
        wanted = true
        refresh()
    }

    fun release() {
        wanted = false
        teardown()
        refresh()
    }

    private fun findHorizon(): UsbDevice? = usb.deviceList.values.firstOrNull {
        UsbIds.classify(it.vendorId, it.productId) == DeviceKind.Horizon
    }

    private fun isAttached(name: String) = usb.deviceList.containsKey(name)

    private fun refresh() {
        val devices = usb.deviceList.values
        val horizon = devices.firstOrNull { UsbIds.classify(it.vendorId, it.productId) == DeviceKind.Horizon }
        if (horizon == null) {
            teardown()
            val other = devices.firstNotNullOfOrNull { UsbIds.classify(it.vendorId, it.productId) as? DeviceKind.OtherAtk }
            _state.value = other?.let { UsbState.Unsupported(it.pid) } ?: UsbState.NoDevice
            return
        }
        session?.let { s ->
            if (s.device.deviceName == horizon.deviceName) { _state.value = UsbState.Connected(s.horizon); return }
            teardown()
        }
        _state.value = when {
            !usb.hasPermission(horizon) -> if (denied) UsbState.PermissionDenied else UsbState.NeedsPermission
            !wanted -> UsbState.NoDevice
            else -> connect(horizon)?.let { UsbState.Connected(it.horizon) } ?: UsbState.NoDevice
        }
    }

    private fun connect(device: UsbDevice): Session? {
        Log.d(TAG, "opening ${describe(device)}")
        val conn = usb.openDevice(device) ?: run { Log.e(TAG, "openDevice returned null"); return null }
        val ifaces = (0 until device.interfaceCount).map(device::getInterface)
        // Claim every HID interface (force: detaches the kernel driver) so its report descriptor
        // can be read; keep the chosen one and release the rest (Android reattaches their driver).
        val claimed = ifaces.filter { it.interfaceClass == HidInterfaceChooser.HID_CLASS && conn.claimInterface(it, true) }
        val infos = ifaces.map { i ->
            val desc = if (i in claimed) readReportDescriptor(conn, i) else null
            InterfaceInfo(
                id = i.id,
                interfaceClass = i.interfaceClass,
                endpoints = (0 until i.endpointCount).map(i::getEndpoint).map {
                    EndpointInfo(it.address, it.type, it.direction == UsbConstants.USB_DIR_IN)
                },
                reportDescriptor = desc,
            ).also { info ->
                Log.d(TAG, "iface id=${i.id} alt=${i.alternateSetting} class=${i.interfaceClass}/${i.interfaceSubclass}/${i.interfaceProtocol} " +
                    "claimed=${i in claimed} endpoints=" +
                    (0 until i.endpointCount).map(i::getEndpoint).joinToString {
                        "0x%02x type=%d %s mps=%d interval=%d".format(it.address, it.type, if (it.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT", it.maxPacketSize, it.interval)
                    } +
                    (desc?.let { " usagePages=${HidReportDescriptor.usagePages(it).map { p -> "0x%04x".format(p) }} " +
                        "reportIds=${HidReportDescriptor.reportIds(it).map { r -> "0x%02x".format(r) }} desc=${it.toHex()}" } ?: ""))
            }
        }
        // The hardware session (docs/hardware-notes.md item 1) confirms this pick against the log above.
        val chosen = HidInterfaceChooser.choose(infos.filter { info -> claimed.any { it.id == info.id } })
        val iface = claimed.firstOrNull { it.id == chosen?.id }
        claimed.filter { it !== iface }.forEach { conn.releaseInterface(it) }
        if (iface == null) {
            Log.e(TAG, "no claimable HID interface")
            conn.close()
            return null
        }
        Log.d(TAG, "using HID interface ${iface.id}")
        val transport = try {
            UsbHidTransport(conn, iface) { isAttached(device.deviceName) }
        } catch (e: Exception) {
            Log.e(TAG, "transport failed", e)
            conn.releaseInterface(iface)
            conn.close()
            return null
        }
        return Session(device, conn, iface, transport, HorizonDevice(HorizonLink(transport, scope))).also { session = it }
    }

    private fun readReportDescriptor(conn: UsbDeviceConnection, iface: UsbInterface): ByteArray? {
        val buf = ByteArray(1024)
        // GET_DESCRIPTOR (standard, interface recipient), type 0x22 = HID report descriptor.
        val n = conn.controlTransfer(0x81, 0x06, 0x2200, iface.id, buf, buf.size, 500)
        return if (n > 0) buf.copyOf(n) else null.also { Log.d(TAG, "iface ${iface.id}: report descriptor read failed ($n)") }
    }

    private fun teardown() {
        val s = session ?: return
        session = null
        Log.d(TAG, "releasing interface ${s.iface.id}")
        s.transport.close()
        s.conn.releaseInterface(s.iface)
        s.conn.close()
    }

    private fun describe(d: UsbDevice) =
        "%s vid=0x%04x pid=0x%04x interfaces=%d".format(d.deviceName, d.vendorId, d.productId, d.interfaceCount)
}
