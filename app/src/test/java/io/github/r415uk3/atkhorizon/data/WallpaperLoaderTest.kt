package io.github.r415uk3.atkhorizon.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperLoaderTest {
    @Test
    fun unknownUntilTheFlagIsReadThenFollowsIt() = runTest {
        val versions = MutableStateFlow<Long?>(null)
        val decoded = mutableListOf<Long>()
        val loader = WallpaperLoader(backgroundScope, versions, StandardTestDispatcher(testScheduler)) { v ->
            decoded += v
            "image-$v"
        }
        // Nothing known before the first read: the activity may hold its first frame.
        assertNull(loader.state.value)
        runCurrent()
        assertEquals(WallpaperImage<String>(null, null), loader.state.value)

        versions.value = 5L
        runCurrent()
        assertEquals(WallpaperImage(5L, "image-5"), loader.state.value)

        // Same version again (e.g. an unrelated settings change) does not decode again.
        versions.value = 5L
        runCurrent()
        assertEquals(listOf(5L), decoded)

        versions.value = null
        runCurrent()
        assertEquals(WallpaperImage<String>(null, null), loader.state.value)
    }

    @Test
    fun aFailedDecodeShowsNoImage() = runTest {
        val loader = WallpaperLoader(backgroundScope, MutableStateFlow<Long?>(1L), StandardTestDispatcher(testScheduler)) {
            throw OutOfMemoryError("too big")
        }
        runCurrent()
        assertEquals(WallpaperImage<String>(1L, null), loader.state.value)
    }
}
