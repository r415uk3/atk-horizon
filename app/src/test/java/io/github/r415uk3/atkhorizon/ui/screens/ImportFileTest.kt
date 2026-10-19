package io.github.r415uk3.atkhorizon.ui.screens

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportFileTest {
    private fun read(n: Int) = readLimited { ByteArrayInputStream(ByteArray(n) { 'a'.code.toByte() }) }

    @Test
    fun exactlyTheLimitIsAccepted() = assertEquals(64 * 1024, read(64 * 1024)!!.length)

    @Test
    fun oneByteOverTheLimitIsRefused() = assertNull(read(64 * 1024 + 1))

    @Test
    fun emptyFileReadsAsEmptyText() = assertEquals("", read(0))

    @Test(expected = IOException::class)
    fun nullStreamIsAnIoError() {
        readLimited { null }
    }

    @Test
    fun nameWithoutExtension() {
        assertEquals("Music", nameWithoutExtension("Music.eq"))
        assertEquals("my.preset", nameWithoutExtension("my.preset.eq"))
        assertEquals("Music", nameWithoutExtension("Music"))
        assertEquals("", nameWithoutExtension(null))
        assertEquals("", nameWithoutExtension("  "))
    }
}
