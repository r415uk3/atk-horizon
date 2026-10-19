package io.github.r415uk3.atkhorizon.usb

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerialOpenerTest {
    @Test
    fun abandonedOpenIsClosedBeforeTheNextOpen() = runTest {
        val events = mutableListOf<String>()
        val results = mutableListOf<String?>()
        lateinit var opener: SerialOpener<String, String>
        opener = SerialOpener(
            scope = this,
            io = StandardTestDispatcher(testScheduler),
            open = { key ->
                events += "open $key"
                // onStop + onStart arrive while the first open is still running on the lane.
                if (key == "a") {
                    opener.abandon()
                    opener.start("b") { results += it }
                }
                key
            },
            close = { events += "close $it" },
        )
        opener.start("a") { results += it }
        advanceUntilIdle()
        assertEquals(listOf("open a", "close a", "open b"), events)
        assertEquals(listOf("b"), results)
    }

    @Test
    fun abandonAfterOpenReturnsStillClosesBeforeTheNextOpen() = runTest {
        val events = mutableListOf<String>()
        val results = mutableListOf<String?>()
        // A hand-stepped lane, so the lane and "main" can be advanced independently.
        val lane = ManualDispatcher()
        val opener = SerialOpener<String, String>(
            this, lane,
            open = { events += "open $it"; it }, close = { events += "close $it" },
        )
        opener.start("a") { results += it }
        testScheduler.runCurrent() // job "a" dispatches to the lane
        lane.runAll() // "a" opened; its result is queued for main but not delivered yet
        assertEquals(listOf("open a"), events)
        opener.abandon()
        opener.start("b") { results += it }
        repeat(10) {
            testScheduler.runCurrent()
            lane.runAll()
        }
        assertEquals(listOf("open a", "close a", "open b"), events)
        assertEquals(listOf("b"), results)
    }

    @Test
    fun resultDeliveredWhenNotAbandoned() = runTest {
        val opener = SerialOpener<String, String>(this, StandardTestDispatcher(testScheduler), open = { null }, close = {})
        val results = mutableListOf<String?>()
        opener.start("a") { results += it }
        assertEquals("a", opener.pendingKey)
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), results)
        assertEquals(null, opener.pendingKey)
    }

    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
}
