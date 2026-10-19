package io.github.r415uk3.atkhorizon.ui

import io.github.r415uk3.atkhorizon.data.BuiltInPresets
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
    /**
     * Translatable resource names in res/[dir]/strings.xml (`translatable="false"` names live only
     * in values/). Unit tests run with the module directory as the working directory.
     */
    private fun keys(dir: String, includeUntranslatable: Boolean = false): Set<String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing $file", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        val nodes = root.childNodes
        return (0 until nodes.length).map(nodes::item)
            .filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
            .filter { includeUntranslatable || it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .map { it.attributes.getNamedItem("name").nodeValue }
            .toSet()
    }

    @Test
    fun stringsHaveRuAndEn() {
        val en = keys("values")
        val ru = keys("values-ru")
        assertTrue(en.isNotEmpty())
        assertEquals("only in EN: ${en - ru}, only in RU: ${ru - en}", en, ru)
    }

    @Test
    fun everyBuiltInPresetHasAName() {
        val en = keys("values")
        BuiltInPresets.all.forEach { assertTrue("no string for ${it.name}", it.name in en) }
        assertTrue("preset_custom_curve" in en)
    }
}
