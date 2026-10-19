package io.github.r415uk3.atkhorizon.protocol

import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class HorizonLink(
    private val transport: HidTransport,
    scope: CoroutineScope,
    private val timeoutMs: Long = 500,
    private val retries: Int = 3,
) {
    private val mutex = Mutex()
    private val inbox = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        // UNDISPATCHED: subscribe to incoming before any request can be sent.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { inbox.trySend(it) }
        }
    }

    suspend fun write(p: ByteArray) = mutex.withLock { transport.send(p) }

    suspend fun request(p: ByteArray): ByteArray = mutex.withLock {
        while (inbox.tryReceive().isSuccess) { /* drop stale reports */ }
        repeat(retries + 1) {
            transport.send(p)
            val r = withTimeoutOrNull(timeoutMs) {
                var m: ByteArray
                do {
                    m = inbox.receive()
                } while (!(m.size >= 2 && m[0].toInt() != 0 && m[0] == p[0] && m[1] == p[1]))
                m
            }
            if (r != null) return@withLock r
        }
        throw TimeoutException("No response after ${retries + 1} attempts")
    }

    suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
}
