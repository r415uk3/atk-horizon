package io.github.r415uk3.atkhorizon.protocol

import java.io.IOException
import kotlinx.coroutines.flow.Flow

/** Moves 63-byte report payloads (no report ID) to and from the device. */
interface HidTransport {
    suspend fun send(payload: ByteArray)

    /**
     * Incoming report payloads. Must be hot and subscribable synchronously: a collector started
     * with CoroutineStart.UNDISPATCHED (as HorizonLink does) is registered before the collect call
     * first suspends, so no reply to a later [send] can be missed. When the device is gone or the
     * transport is closed, the flow completes or throws [DeviceGoneException], also for late
     * subscribers.
     */
    val incoming: Flow<ByteArray>
    fun close()
}

class DeviceGoneException : IOException("HID device is gone")
