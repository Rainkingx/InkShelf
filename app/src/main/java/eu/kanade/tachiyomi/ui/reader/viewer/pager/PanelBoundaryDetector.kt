package eu.kanade.tachiyomi.ui.reader.viewer.pager

import kotlin.math.max
import kotlin.math.min

/**
 * Experimental, conservative Western-comic gutter detector.
 *
 * Pure pixel geometry, no ML dependency. Only split at narrow, nearly solid
 * white or black bands that span the current rectangle and have artwork on
 * both sides. Horizontal cuts take precedence over vertical cuts so rows
 * read left to right. Return null when a reliable multi-panel layout cannot
 * be established; the original dialogue detector remains authoritative.
 */
internal object PanelBoundaryDetector {
    data class Panel(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private data class Cut(val start: Int, val end: Int, val horizontal: Boolean)

    fun detect(pixels: IntArray, width: Int, height: Int): List<Panel>? {
        if (width < 80 || height < 80 || pixels.size.toLong() != width.toLong() * height) return null
        val minWidth = max(12, (width * 0.13f).toInt())
        val minHeight = max(12, (height * 0.10f).toInt())
        val page = trimMargins(pixels, width, Panel(0, 0, width, height))
        if (page.width < minWidth * 2 && page.height < minHeight * 2) return null
        val panels = mutableListOf<Panel>()

        fun split(panel: Panel, depth: Int): Boolean {
            if (panels.size >= MAX_PANELS) return false
            if (panel.width < minWidth || panel.height < minHeight || !hasArtwork(pixels, width, panel)) return false
            val cut = findCut(pixels, width, panel, true, minHeight)
                ?: findCut(pixels, width, panel, false, minWidth)
            if (cut == null) {
                panels += panel
                return true
            }
            if (depth >= MAX_DEPTH) return false
            val first = if (cut.horizontal) panel.copy(bottom = cut.start) else panel.copy(right = cut.start)
            val second = if (cut.horizontal) panel.copy(top = cut.end) else panel.copy(left = cut.end)
            return split(first, depth + 1) && split(second, depth + 1)
        }

        return if (split(page, 0) && panels.size in 2..MAX_PANELS) panels else null
    }

    private fun trimMargins(pixels: IntArray, stride: Int, page: Panel): Panel {
        var box = page
        val maxY = (page.height * 0.08f).toInt()
        val maxX = (page.width * 0.08f).toInt()
        // Do not strip an unlimited blank region or the entire page.
        repeat(maxY) {
            if (solidLine(pixels, stride, box, box.top, true) != 0) box = box.copy(top = box.top + 1)
        }
        repeat(maxY) {
            if (solidLine(pixels, stride, box, box.bottom - 1, true) != 0) box = box.copy(bottom = box.bottom - 1)
        }
        repeat(maxX) {
            if (solidLine(pixels, stride, box, box.left, false) != 0) box = box.copy(left = box.left + 1)
        }
        repeat(maxX) {
            if (solidLine(pixels, stride, box, box.right - 1, false) != 0) box = box.copy(right = box.right - 1)
        }
        return box
    }

    private fun findCut(
        pixels: IntArray,
        stride: Int,
        box: Panel,
        horizontal: Boolean,
        minimum: Int,
    ): Cut? {
        val start = if (horizontal) box.top else box.left
        val end = if (horizontal) box.bottom else box.right
        if (end - start < minimum * 2 + 2) return null
        val maxBand = max(2, ((end - start) * 0.075f).toInt())
        var pos = start + minimum
        var best: Cut? = null
        while (pos < end - minimum) {
            val tone = solidLine(pixels, stride, box, pos, horizontal)
            if (tone == 0) {
                pos++
                continue
            }
            val bandStart = pos
            while (pos < end && solidLine(pixels, stride, box, pos, horizontal) == tone) pos++
            val bandEnd = pos
            val size = bandEnd - bandStart
            // Reject truncated edge bands and broad white/black scenery.
            val startsInsideBand = solidLine(pixels, stride, box, bandStart - 1, horizontal) == tone
            if (startsInsideBand || size !in 2..maxBand || end - bandEnd < minimum) continue
            val before = artworkBeside(pixels, stride, box, bandStart, horizontal, -1, tone)
            val after = artworkBeside(pixels, stride, box, bandEnd, horizontal, 1, tone)
            if (!before || !after) continue
            val previous = best
            if (previous == null || size > previous.end - previous.start) {
                best = Cut(bandStart, bandEnd, horizontal)
            }
        }
        return best
    }

    /** 1 = near white, -1 = near black, 0 = mixed artwork. */
    private fun solidLine(pixels: IntArray, stride: Int, box: Panel, pos: Int, horizontal: Boolean): Int {
        val start = if (horizontal) box.left else box.top
        val end = if (horizontal) box.right else box.bottom
        var white = 0
        var black = 0
        for (along in start until end) {
            val pixel = if (horizontal) pixels[pos * stride + along] else pixels[along * stride + pos]
            when (tone(pixel)) {
                1 -> white++
                -1 -> black++
            }
        }
        val count = end - start
        return when {
            white * 100 >= count * 98 -> 1
            black * 100 >= count * 98 -> -1
            else -> 0
        }
    }

    private fun artworkBeside(
        pixels: IntArray,
        stride: Int,
        box: Panel,
        edge: Int,
        horizontal: Boolean,
        direction: Int,
        gutterTone: Int,
    ): Boolean {
        val start = if (horizontal) box.left else box.top
        val end = if (horizontal) box.right else box.bottom
        val minPos = if (horizontal) box.top else box.left
        val maxPos = if (horizontal) box.bottom else box.right
        var different = 0
        var count = 0
        for (offset in 0 until 5) {
            val pos = if (direction < 0) edge - 1 - offset else edge + offset
            if (pos !in minPos until maxPos) continue
            for (along in start until end) {
                val pixel = if (horizontal) pixels[pos * stride + along] else pixels[along * stride + pos]
                if (tone(pixel) != gutterTone) different++
                count++
            }
        }
        return count > 0 && different * 100 >= count * 35
    }

    private fun hasArtwork(pixels: IntArray, stride: Int, box: Panel): Boolean {
        var white = 0
        var black = 0
        var count = 0
        for (y in box.top until box.bottom step 2) {
            for (x in box.left until box.right step 2) {
                when (tone(pixels[y * stride + x])) {
                    1 -> white++
                    -1 -> black++
                }
                count++
            }
        }
        return count > 0 && (count - max(white, black)) * 100 >= count * 12
    }

    private fun tone(pixel: Int): Int {
        val r = (pixel ushr 16) and 255
        val g = (pixel ushr 8) and 255
        val b = pixel and 255
        val low = min(r, min(g, b))
        val high = max(r, max(g, b))
        return when {
            low >= 235 -> 1
            high <= 24 -> -1
            else -> 0
        }
    }

    private const val MAX_PANELS = 12
    private const val MAX_DEPTH = 8
}
