package io.github.r415uk3.atkhorizon.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException

class WallpaperTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun maxSideIsTheLongerScreenSideCappedAt2560() {
        assertEquals(2400, wallpaperMaxSide(1080, 2400))
        assertEquals(2400, wallpaperMaxSide(2400, 1080))
        assertEquals(2560, wallpaperMaxSide(1440, 3200))
        // A nonsense screen size still gives a usable bound.
        assertEquals(2560, wallpaperMaxSide(0, 0))
    }

    @Test
    fun targetSizeKeepsAspectAndNeverUpscales() {
        assertEquals(2400 to 1800, wallpaperTargetSize(4000, 3000, 2400))
        assertEquals(1800 to 2400, wallpaperTargetSize(3000, 4000, 2400))
        assertEquals(1000 to 800, wallpaperTargetSize(1000, 800, 2400))
        assertEquals(2400 to 2400, wallpaperTargetSize(5000, 5000, 2400))
        // Extreme panoramas keep at least one pixel on the short side.
        assertEquals(2400 to 1, wallpaperTargetSize(100_000, 10, 2400))
    }

    @Test
    fun copyLimitedStopsAboveTheLimit() {
        val dest = tmp.newFile()
        assertTrue(copyLimited(ByteArrayInputStream(ByteArray(10) { it.toByte() }), dest, maxBytes = 10))
        assertArrayEquals(ByteArray(10) { it.toByte() }, dest.readBytes())
        assertFalse(copyLimited(ByteArrayInputStream(ByteArray(11)), dest, maxBytes = 10))
    }

    @Test
    fun writeAtomicallyReplacesOnlyOnSuccess() {
        val dir = tmp.newFolder()
        val files = WallpaperFiles(dir)
        assertFalse(files.exists)
        files.writeAtomically { it.write(byteArrayOf(1, 2, 3)) }
        assertArrayEquals(byteArrayOf(1, 2, 3), files.file.readBytes())

        try {
            files.writeAtomically {
                it.write(byteArrayOf(9))
                throw IOException("disk full")
            }
        } catch (_: IOException) {
        }
        // The old wallpaper survives a failed write and no temp file is left behind.
        assertArrayEquals(byteArrayOf(1, 2, 3), files.file.readBytes())
        assertEquals(listOf(files.file.name), dir.list()!!.toList())

        files.delete()
        assertFalse(files.exists)
        files.delete() // deleting twice is fine
    }

    @Test
    fun inMemoryWallpaperVersionRoundTrip() = runTest {
        val s = InMemorySettingsStore()
        assertNull(s.wallpaperVersion.first())
        s.setWallpaperVersion(42L)
        assertEquals(42L, s.wallpaperVersion.first())
        s.setWallpaperVersion(null)
        assertNull(s.wallpaperVersion.first())
    }

    @Test
    fun repositoryWallpaperVersionRoundTrip() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("s.preferences_pb").also { it.delete() } }
        val repo = SettingsRepository(store)
        assertNull(repo.wallpaperVersion.first())
        repo.setWallpaperVersion(1234L)
        assertEquals(1234L, repo.wallpaperVersion.first())
        // The theme is independent of the wallpaper.
        repo.setThemeMode(ThemeMode.Dark)
        assertEquals(1234L, repo.wallpaperVersion.first())
        assertEquals(ThemeMode.Dark, repo.themeMode.first())
        repo.setWallpaperVersion(null)
        assertNull(repo.wallpaperVersion.first())
        assertEquals(ThemeMode.Dark, repo.themeMode.first())
    }
}
