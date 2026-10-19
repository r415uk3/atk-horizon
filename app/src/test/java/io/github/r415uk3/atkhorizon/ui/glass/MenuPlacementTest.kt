package io.github.r415uk3.atkhorizon.ui.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuPlacementTest {
    private val window = IntSize(1000, 2000)
    private val menu = IntSize(400, 300)

    private fun place(anchor: IntRect, menu: IntSize = this.menu, rtl: Boolean = false) =
        placeMenu(anchor, menu, window, margin = 40, gap = 10, rtl = rtl)

    @Test fun opensBelowLeftAligned() {
        val p = place(IntRect(100, 200, 300, 280))
        assertEquals(IntOffset(100, 290), p.offset)
        assertFalse(p.above)
    }

    @Test fun flipsAboveWhenNoRoomBelow() {
        val p = place(IntRect(100, 1800, 300, 1880))
        assertEquals(IntOffset(100, 1800 - 10 - 300), p.offset)
        assertTrue(p.above)
    }

    @Test fun clampsToRightMargin() {
        val p = place(IntRect(900, 200, 980, 280))
        assertEquals(1000 - 40 - 400, p.offset.x)
    }

    @Test fun clampsToLeftMarginForOffscreenAnchor() {
        // A pill scrolled partly out of the row.
        val p = place(IntRect(-50, 200, 30, 280))
        assertEquals(40, p.offset.x)
    }

    @Test fun fitsNeitherSidePicksLargerRoomAndClamps() {
        val tall = IntSize(400, 1500)
        val p = place(IntRect(100, 700, 300, 780), tall)
        // Below: 2000-40-790 = 1170 room; above: 700-10-40 = 650 → stays below, clamped up.
        assertFalse(p.above)
        assertEquals(2000 - 40 - 1500, p.offset.y)
    }

    @Test fun menuLargerThanWindowSticksToMargins() {
        val p = place(IntRect(100, 100, 200, 150), IntSize(2000, 3000))
        assertEquals(IntOffset(40, 40), p.offset)
    }

    @Test fun zeroSizeAnchorStillPlacesInsideMargins() {
        val p = place(IntRect(0, 0, 0, 0))
        assertEquals(IntOffset(40, 40), p.offset)
        assertFalse(p.above)
    }

    @Test fun zeroSizeOrHiddenAnchorCannotAnchor() {
        val pill = Rect(100f, 200f, 300f, 280f)
        assertTrue(canAnchor(pill, pill))
        assertTrue(canAnchor(pill, Rect(250f, 200f, 300f, 280f)))
        assertFalse(canAnchor(Rect(0f, 0f, 0f, 0f), Rect(0f, 0f, 0f, 0f)))
        assertFalse(canAnchor(pill, Rect.Zero))
    }

    @Test fun rtlAlignsToAnchorEnd() {
        val p = place(IntRect(500, 200, 700, 280), rtl = true)
        assertEquals(700 - 400, p.offset.x)
    }

    @Test fun pivotFollowsAnchorCentre() {
        assertEquals(0.25f, place(IntRect(100, 200, 300, 280)).pivotX, 0.001f)
        assertEquals(0.75f, place(IntRect(500, 200, 700, 280), rtl = true).pivotX, 0.001f)
    }
}
