package io.github.r415uk3.atkhorizon.ui.screens

import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.data.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PresetNamesTest {
    @Test
    fun everyBuiltInPresetHasALocalizedName() {
        BuiltInPresets.all.forEach { assertNotNull(it.name, builtInNameRes(it.name)) }
        assertEquals(BuiltInPresets.all.size, BuiltInPresets.all.mapNotNull { builtInNameRes(it.name) }.toSet().size)
        assertEquals(R.string.preset_cs2, builtInNameRes("preset_cs2"))
        assertNull(builtInNameRes("My preset"))
    }
}
