package io.github.r415uk3.atkhorizon.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.SystemClock
import android.util.Log
import io.github.r415uk3.atkhorizon.BuildConfig
import io.github.r415uk3.atkhorizon.protocol.DeviceGoneException
import io.github.r415uk3.atkhorizon.protocol.HidTransport
import io.github.r415uk3.atkhorizon.protocol.REPORT_ID
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val TAG = "Horizon"
private const val REPORT_SIZE = 64
private const val READ_TIMEOUT_MS = 200
private const val WRITE_TIMEOUT_MS = 500
/** Longest a transfer can take (its own timeout) plus slack: close() waits at most this long. */
private const val CLOSE_WAIT_MS = WRITE_TIMEOUT_MS + 200L

/**
 * [HidTransport] over an Android USB Host connection. The caller has already claimed [iface]
 * (HorizonConnector holds `claimInterface(iface, true)` from open() to release()).
 *
 * @param isAttached tells a read timeout (device present, -1 after 200 ms) apart from a gone
 *   device (-1 after detach); bulkTransfer returns -1 for both.
 */
class UsbHidTransport(
    private val conn: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val isAttached: () -> Boolean = { true },
) : HidTransport {
    private sealed interface Event {
        class Report(val payload: ByteArray) : Event
        data object Gone : Event
    }

    private val inEp: UsbEndpoint = endpoint(UsbConstants.USB_DIR_IN)
        ?: throw IOException("HID interface ${iface.id} has no interrupt IN endpoint")
    private val outEp: UsbEndpoint? = endpoint(UsbConstants.USB_DIR_OUT)

    @Volatile private var gone = false

    /**
     * Every transfer holds the read lock (transfers may overlap); [close] takes the write lock, so
     * it waits for in-flight transfers and none starts after it: the connection is never closed
     * under a running bulkTransfer/controlTransfer (native use-after-free).
     */
    private val transfers = ReentrantReadWriteLock()

    /** Runs a blocking transfer unless the transport is gone/closed (null then). */
    private inline fun <T : Any> transfer(block: () -> T): T? {
        val l = transfers.readLock()
        l.lock()
        try {
            return if (gone) null else block()
        } finally {
            l.unlock()
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // DROP_OLDEST keeps tryEmit non-blocking and guarantees the final Gone event is kept.
    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Hot: subscribing registers synchronously (SharedFlow), so a collector started with
     * CoroutineStart.UNDISPATCHED sees every report read after that point. Throws
     * [DeviceGoneException] once the device is gone or the transport is closed, including for
     * subscribers that arrive after that.
     */
    override val incoming: Flow<ByteArray> = events
        .onSubscription { if (gone) throw DeviceGoneException() }
        .transformWhile { e ->
            when (e) {
                is Event.Report -> { emit(e.payload); true }
                Event.Gone -> throw DeviceGoneException()
            }
        }

    private val reader: Job = scope.launch { readLoop() }

    init {
        Log.d(TAG, "transport: iface=${iface.id} in=${hexAddr(inEp)} out=${outEp?.let(::hexAddr) ?: "none (SET_REPORT)"}")
    }

    private fun endpoint(dir: Int): UsbEndpoint? = (0 until iface.endpointCount).map(iface::getEndpoint)
        .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_INT && it.direction == dir }

    private fun hexAddr(ep: UsbEndpoint) = "0x%02x".format(ep.address)

    private suspend fun readLoop() {
        val buf = ByteArray(REPORT_SIZE)
        while (scope.isActive && !gone) {
            val started = SystemClock.elapsedRealtime()
            val n = transfer { conn.bulkTransfer(inEp, buf, buf.size, READ_TIMEOUT_MS) } ?: break
            if (n < 0) {
                if (gone || !isAttached()) break
                // Timeout with the device still present. Guard against a fast-failing loop.
                if (SystemClock.elapsedRealtime() - started < 20) delay(20)
                continue
            }
            if (n == 0) continue
            if (BuildConfig.DEBUG) Log.d(TAG, "IN  ${buf.copyOf(n).toHex()}")
            if (buf[0] == REPORT_ID) {
                val payload = ByteArray(REPORT_SIZE - 1)
                buf.copyInto(payload, 0, 1, n)
                events.tryEmit(Event.Report(payload))
            }
        }
        markGone()
    }

    private fun markGone() {
        if (!gone) Log.d(TAG, "transport: device gone / closed")
        gone = true
        events.tryEmit(Event.Gone)
    }

    override suspend fun send(payload: ByteArray) = withContext(Dispatchers.IO) {
        if (gone) throw DeviceGoneException()
        val buf = ByteArray(REPORT_SIZE)
        buf[0] = REPORT_ID
        payload.copyInto(buf, 1, 0, minOf(payload.size, REPORT_SIZE - 1))
        if (BuildConfig.DEBUG) Log.d(TAG, "OUT ${buf.toHex()}")
        val n = transfer {
            if (outEp != null) {
                conn.bulkTransfer(outEp, buf, buf.size, WRITE_TIMEOUT_MS)
            } else {
                // SET_REPORT, report type Output (0x02), report ID 0x4B.
                conn.controlTransfer(0x21, 0x09, 0x0200 or REPORT_ID.toInt(), iface.id, buf, buf.size, WRITE_TIMEOUT_MS)
            }
        } ?: throw DeviceGoneException()
        if (n < 0) {
            if (gone || !isAttached()) { markGone(); throw DeviceGoneException() }
            throw IOException("USB write failed ($n)")
        }
    }

    /**
     * Stops the reader and completes [incoming] with [DeviceGoneException]. Does not release the
     * interface or close the connection (HorizonConnector owns those, right after this returns).
     * Waits (bounded by [CLOSE_WAIT_MS]) for in-flight sends/reads to finish, and no transfer
     * starts afterwards, so the connection is not closed under one.
     */
    override fun close() {
        gone = true
        events.tryEmit(Event.Gone)
        val w = transfers.writeLock()
        if (w.tryLock(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS)) w.unlock()
        else Log.e(TAG, "transport: a transfer is still running after ${CLOSE_WAIT_MS} ms")
        runBlocking { withTimeoutOrNull(READ_TIMEOUT_MS + 100L) { reader.join() } }
        scope.cancel()
    }
}
