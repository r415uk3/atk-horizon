package io.github.r415uk3.atkhorizon.usb

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs a blocking [open] on a serial [io] lane, one at a time, and hands the result to the caller
 * on [scope] (the main thread) unless the open was [abandon]ed meanwhile. An abandoned result is
 * always [close]d before the next open runs, so two opens never hold the device at once:
 * - abandoned while [open] runs: closed on the lane right after it returns, in the same lane task;
 * - abandoned after it returned: closed from [scope] before that start's job completes, and the
 *   next [start] joins that job before dispatching its own open.
 *
 * An [open] or [close] that throws is treated as a failed open (null) / a finished close, so a
 * USB exception never escapes into [scope] and [pendingKey] is always cleared.
 *
 * [start], [abandon] and [pendingKey] are main-thread only; [open]/[close] run on [io].
 */
internal class SerialOpener<K : Any, T : Any>(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val open: (K) -> T?,
    private val close: (T) -> Unit,
    /** Reports an exception thrown by [open] or [close] (logging only). */
    private val onError: (Exception) -> Unit = {},
) {
    private val generation = AtomicInteger()
    private var job: Job? = null

    /** The key being opened; null when no open is in flight (or it was abandoned). */
    var pendingKey: K? = null
        private set

    /** Abandons any open in flight and opens [key]; [onResult] gets the result (null = failed). */
    fun start(key: K, onResult: (T?) -> Unit) {
        abandon()
        val gen = generation.get()
        pendingKey = key
        val previous = job
        job = scope.launch {
            previous?.join()
            var abandoned = false
            val opened = withContext(io) {
                val o = try {
                    open(key)
                } catch (e: Exception) {
                    onError(e)
                    null
                }
                if (generation.get() != gen) {
                    abandoned = true
                    o?.let(::safeClose)
                    null
                } else {
                    o
                }
            }
            if (abandoned) return@launch
            if (generation.get() != gen) {
                opened?.let { o -> scope.launch(io + NonCancellable) { safeClose(o) } }
                return@launch
            }
            pendingKey = null
            onResult(opened)
        }
    }

    private fun safeClose(o: T) {
        try {
            close(o)
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun abandon() {
        if (pendingKey == null) return
        pendingKey = null
        generation.incrementAndGet()
    }
}
