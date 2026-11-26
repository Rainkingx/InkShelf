package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelBoundaryDetectorTest {
    private val width = 240
    private val height = 320
    private val art = 0xff8b4580.toInt()
    private val white = 0xffffffff.toInt()
    private val black = 0xff000000.toInt()

    private fun page(color: Int = art) = IntArray(width * height) { color }
    private fun fill(pixels: IntArray, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        for (y in top until bottom) for (x in left until right) pixels[y * width + x] = color
    }

    @Test fun whiteGuttersFollowWesternReadingOrder() {
        val pixels = page()
        fill(pixels, 0, 156, width, 164, white)
        fill(pixels, 116, 0, 124, 156, white)
        fill(pixels, 116, 164, 124, height, white)
        assertEquals(
            listOf(
                PanelBoundaryDetector.Panel(0, 0, 116, 156),
                PanelBoundaryDetector.Panel(124, 0, 240, 156),
                PanelBoundaryDetector.Panel(0, 164, 116, 320),
                PanelBoundaryDetector.Panel(124, 164, 240, 320),
            ),
            PanelBoundaryDetector.detect(pixels, width, height),
        )
    }

    @Test fun blackGutterSplitsPage() {
        val pixels = page()
        fill(pixels, 0, 156, width, 164, black)
        assertEquals(
            listOf(
                PanelBoundaryDetector.Panel(0, 0, width, 156),
                PanelBoundaryDetector.Panel(0, 164, width, height),
            ),
            PanelBoundaryDetector.detect(pixels, width, height),
        )
    }

    @Test fun partialGutterDoesNotSplitArtwork() {
        val pixels = page()
        fill(pixels, 20, 156, 220, 164, white)
        assertNull(PanelBoundaryDetector.detect(pixels, width, height))
    }

    @Test fun wideBlankBandIsNotAConfidentGutter() {
        val pixels = page()
        fill(pixels, 0, 120, width, 200, white)
        assertNull(PanelBoundaryDetector.detect(pixels, width, height))
    }

    @Test fun blankPageAndFullBleedArtworkFallBack() {
        assertNull(PanelBoundaryDetector.detect(page(white), width, height))
        assertNull(PanelBoundaryDetector.detect(page(), width, height))
    }

    @Test fun outerWhiteMarginsAreNotPanels() {
        val pixels = page(white)
        fill(pixels, 10, 10, 230, 155, art)
        fill(pixels, 10, 165, 230, 310, art)
        assertEquals(
            listOf(
                PanelBoundaryDetector.Panel(10, 10, 230, 155),
                PanelBoundaryDetector.Panel(10, 165, 230, 310),
            ),
            PanelBoundaryDetector.detect(pixels, width, height),
        )
    }
    @Test fun smallBreakInVerticalGutterKeepsColumnsTogether() {
        val pixels = page()
        fill(pixels, 116, 0, 124, height, white)
        fill(pixels, 116, 145, 124, 159, art)
        assertEquals(
            listOf(
                PanelBoundaryDetector.Panel(0, 0, 116, height),
                PanelBoundaryDetector.Panel(124, 0, 240, height),
            ),
            PanelBoundaryDetector.detect(pixels, width, height),
        )
    }

    @Test fun staggeredPanelsFollowColumnBeforeCrossingGutter() {
        val pixels = page()
        fill(pixels, 116, 0, 124, height, white)
        fill(pixels, 0, 120, 116, 128, white)
        fill(pixels, 124, 190, width, 198, white)
        assertEquals(
            listOf(
                PanelBoundaryDetector.Panel(0, 0, 116, 120),
                PanelBoundaryDetector.Panel(0, 128, 116, height),
                PanelBoundaryDetector.Panel(124, 0, 240, 190),
                PanelBoundaryDetector.Panel(124, 198, 240, height),
            ),
            PanelBoundaryDetector.detect(pixels, width, height),
        )
    }
}
