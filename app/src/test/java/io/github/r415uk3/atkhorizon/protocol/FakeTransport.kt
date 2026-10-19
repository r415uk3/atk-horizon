package io.github.r415uk3.atkhorizon.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeTransport : HidTransport {
    val sent = mutableListOf<ByteArray>()
    private val flow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    /** Called after each send is recorded; may emit responses or throw. */
    var onSend: suspend (index: Int, payload: ByteArray) -> Unit = { _, _ -> }
    var closed = false

    override val incoming: Flow<ByteArray> = flow

    override suspend fun send(payload: ByteArray) {
        sent += payload
        onSend(sent.size, payload)
    }

    suspend fun emit(payload: ByteArray) = flow.emit(payload)

    override fun close() {
        closed = true
    }
}
