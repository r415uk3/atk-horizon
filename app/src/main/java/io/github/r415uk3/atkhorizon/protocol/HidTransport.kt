package io.github.r415uk3.atkhorizon.protocol

import java.io.IOException
import kotlinx.coroutines.flow.Flow

/** Moves 63-byte report payloads (no report ID) to and from the device. */
interface HidTransport {
    suspend fun send(payload: ByteArray)
    val incoming: Flow<ByteArray>
    fun close()
}

class DeviceGoneException : IOException("HID device is gone")
