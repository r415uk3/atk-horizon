package io.github.r415uk3.atkhorizon.data

import io.github.r415uk3.atkhorizon.protocol.EqBand
import io.github.r415uk3.atkhorizon.protocol.FilterType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetStoreTest {
    private val bands = EqBand.DEFAULTS.map { it.copy(type = FilterType.HighShelf, gainDb = 1.5, q = 0.33) }

    @Test
    fun inMemoryCreateRenameDelete() = runTest {
        val s = InMemoryPresetStore()
        assertEquals(BuiltInPresets.all, s.presets.first())
        val a = s.create("A", bands)
        val b = s.create("B", bands)
        assertTrue(a.id.startsWith("custom-"))
        assertFalse(a.builtIn)
        assertEquals(BuiltInPresets.all + listOf(a, b), s.presets.first())
        s.rename(a.id, "A2")
        assertEquals("A2", s.presets.first().first { it.id == a.id }.name)
        s.delete(a.id)
        assertEquals(BuiltInPresets.all + listOf(b), s.presets.first())
    }

    @Test
    fun inMemoryBuiltInsCannotBeDeletedOrRenamed() = runTest {
        val s = InMemoryPresetStore()
        s.delete("cs2")
        s.rename("cs2", "Hacked")
        assertEquals(BuiltInPresets.all, s.presets.first())
    }

    @Test
    fun jsonRoundTrip() {
        val p = Preset("custom-1", "Мой \"пресет\"", bands, false)
        assertEquals(listOf(p), PresetJson.decode(PresetJson.encode(listOf(p))))
        assertTrue(PresetJson.encode(listOf(p)).contains("\"type\":3"))
    }

    @Test
    fun jsonDecodeOfGarbageIsEmpty() {
        assertEquals(emptyList<Preset>(), PresetJson.decode("not json"))
        assertEquals(emptyList<Preset>(), PresetJson.decode(null))
    }

    @Test
    fun inMemorySettings() = runTest {
        val s = InMemorySettingsStore()
        assertEquals(ThemeMode.Auto, s.themeMode.first())
        s.setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, s.themeMode.first())
    }

    @Test
    fun writesAfterGarbageThrowAndLeaveRawUntouched() {
        val garbage = "{corrupt"
        val p = Preset("custom-1", "A", bands, false)
        listOf<(List<Preset>) -> List<Preset>>({ it + p }, { l -> l.map { it.copy(name = "x") } }, { l -> l.filterNot { it.id == "custom-1" } })
            .forEach { f ->
                try {
                    PresetJson.update(garbage, f)
                    org.junit.Assert.fail("expected CorruptPresetsException")
                } catch (_: CorruptPresetsException) {
                }
            }
        // reads still degrade to empty
        assertEquals(emptyList<Preset>(), PresetJson.decode(garbage))
    }

    @Test
    fun updateOfMissingOrValidRawWorks() {
        val p = Preset("custom-1", "A", bands, false)
        val raw = PresetJson.update(null, { it + p })
        assertEquals(listOf(p), PresetJson.decode(raw))
        assertEquals(emptyList<Preset>(), PresetJson.decode(PresetJson.update(raw, { l -> l.filterNot { it.id == "custom-1" } })))
    }
}
