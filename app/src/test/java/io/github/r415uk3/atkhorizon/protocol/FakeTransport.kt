package io.github.r415uk3.atkhorizon.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

class FakeTransport : HidTransport {
    val sent = mutableListOf<ByteArray>()
    private val shared = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val end = ByteArray(0)
    private var failure: Throwable? = null

    /** Called after each send is recorded; may emit responses or throw. */
    var onSend: suspend (index: Int, payload: ByteArray) -> Unit = { _, _ -> }
    var closed = false

    // Subscribes synchronously to `shared`, so no emission is missed once collection starts.
    override val incoming: Flow<ByteArray> = flow {
        shared.transformWhile { if (it === end) false else { emit(it); true } }.collect { emit(it) }
        failure?.let { throw it }
    }

    override suspend fun send(payload: ByteArray) {
        sent += payload
        onSend(sent.size, payload)
    }

    suspend fun emit(payload: ByteArray) = shared.emit(payload)

    /** Ends the incoming flow normally. */
    suspend fun completeIncoming() = shared.emit(end)

    /** Ends the incoming flow with an exception. */
    suspend fun failIncoming(e: Throwable) {
        failure = e
        shared.emit(end)
    }

    override fun close() {
        closed = true
    }
}
