package io.github.r415uk3.atkhorizon.protocol

import java.util.concurrent.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HorizonLinkTest {
    private fun resp(vararg head: Int) = ByteArray(63).also { b -> head.forEachIndexed { i, v -> b[i] = v.toByte() } }

    @Test
    fun requestReturnsMatchingResponse() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        val r = resp(0x80, 0x0c, 1, 2, 3)
        t.onSend = { _, _ -> t.emit(r) }
        assertArrayEquals(r, link.request(Packets.readFirmware()))
    }

    @Test
    fun ignoresZeroAndUnmatched() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        val good = resp(0x80, 0x0c, 9)
        t.onSend = { _, _ ->
            t.emit(resp(0x00, 0x0c))
            t.emit(resp(0x01, 0x09))
            t.emit(good)
        }
        assertArrayEquals(good, link.request(Packets.readFirmware()))
        assertEquals(1, t.sent.size)
    }

    @Test
    fun retriesThenTimesOut() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        val e = runCatching { link.request(Packets.readFirmware()) }.exceptionOrNull()
        assertTrue(e is TimeoutException)
        assertEquals(4, t.sent.size)
        assertEquals(2000, currentTime)
    }

    @Test
    fun retrySucceeds() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        val r = resp(0x80, 0x0c, 7)
        t.onSend = { n, _ -> if (n == 2) t.emit(r) }
        assertArrayEquals(r, link.request(Packets.readFirmware()))
        assertEquals(2, t.sent.size)
        assertEquals(500, currentTime)
    }

    @Test
    fun requestsAreSerialized() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        val a = async { link.request(Packets.readFirmware()) }
        val b = async { link.request(Packets.readBand(0)) }
        delay(100)
        assertEquals(1, t.sent.size)
        t.emit(resp(0x80, 0x0c))
        delay(100)
        assertEquals(2, t.sent.size)
        assertEquals(0x09, t.sent[1][1].toInt())
        t.emit(resp(0x80, 0x09, 0x02))
        assertEquals(0x0c, a.await()[1].toInt())
        assertEquals(0x09, b.await()[1].toInt())
    }

    @Test
    fun transportErrorPropagates() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        t.onSend = { _, _ -> throw DeviceGoneException() }
        val e = runCatching { link.request(Packets.readFirmware()) }.exceptionOrNull()
        assertTrue(e is DeviceGoneException)
        assertEquals(1, t.sent.size)
    }

    @Test
    fun incomingCompletionFailsInFlightAndLaterCalls() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        val inFlight = async { runCatching { link.request(Packets.readFirmware()) } }
        delay(100)
        t.completeIncoming()
        assertTrue(inFlight.await().exceptionOrNull() is DeviceGoneException)
        assertEquals(100, currentTime)
        assertTrue(runCatching { link.request(Packets.readFirmware()) }.exceptionOrNull() is DeviceGoneException)
        assertTrue(runCatching { link.write(Packets.readFirmware()) }.exceptionOrNull() is DeviceGoneException)
        assertEquals(1, t.sent.size)
    }

    @Test
    fun incomingErrorMapsToDeviceGoneWithoutCrashingScope() = runTest {
        val t = FakeTransport()
        val link = HorizonLink(t, backgroundScope)
        val inFlight = async { runCatching { link.request(Packets.readFirmware()) } }
        delay(100)
        val boom = java.io.IOException("boom")
        t.failIncoming(boom)
        val e = inFlight.await().exceptionOrNull()
        assertTrue(e is DeviceGoneException)
        assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it === boom })
        assertTrue(runCatching { link.write(Packets.readFirmware()) }.exceptionOrNull() is DeviceGoneException)
    }
}
