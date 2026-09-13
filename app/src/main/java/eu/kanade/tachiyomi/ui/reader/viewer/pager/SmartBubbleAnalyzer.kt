package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Smart Bubble V2 detector.
 *
 * Primary path: bundled Google ML Kit OCR. We navigate to actual recognized dialogue/caption text
 * instead of guessing from white shapes, which removes the largest source of V1 false targets.
 *
 * V1.4's local shape/text detector is kept as an offline fallback if OCR cannot find usable text
 * on a page. All returned regions are normalized to the page size.
 */

internal object SmartBubbleAnalyzer {
    suspend fun analyze(streamProvider: () -> InputStream): List<RectF> {
        val bitmap = decodeForAnalysis(streamProvider) ?: return emptyList()
        return try {
            // V2: OCR is authoritative when it finds dialogue. It cannot confuse a plain white
            // shirt/cloud/window with a speech bubble because there must be recognized text.
            val ocrRegions = runCatching { detectOcrTextRegions(bitmap) }.getOrDefault(emptyList())
            if (ocrRegions.isNotEmpty()) {
                ocrRegions
            } else {
                // Keep V1.4 as a fallback for stylised lettering that OCR genuinely cannot read.
                val fallbackBitmap = bitmap.forHeuristicFallback()
                try {
                    detect(fallbackBitmap)
                } finally {
                    if (fallbackBitmap !== bitmap) fallbackBitmap.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeForAnalysis(streamProvider: () -> InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        streamProvider().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > OCR_MAX_ANALYSIS_DIMENSION) {
            sample *= 2
        }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

        return streamProvider().use { BitmapFactory.decodeStream(it, null, options) }
    }

    private suspend fun detectOcrTextRegions(bitmap: Bitmap): List<RectF> {
        if (bitmap.width < 80 || bitmap.height < 80) return emptyList()

        val result = recognizeText(bitmap)
        if (result.textBlocks.isEmpty()) return emptyList()

        val width = bitmap.width
        val height = bitmap.height
        val regions =
            result.textBlocks.flatMap { block ->
                ocrRegionsForBlock(block, width, height)
            }

        if (regions.isEmpty()) return emptyList()

        // ML Kit can occasionally return overlapping paragraph blocks. Only collapse near-duplicates;
        // do not aggressively merge nearby dialogue balloons into one target.
        val deduped = mutableListOf<RectF>()
        for (candidate in regions.sortedByDescending { it.width() * it.height() }) {
            if (deduped.none { existing -> overlapRatio(existing, candidate) >= OCR_DUPLICATE_OVERLAP }) {
                deduped += candidate
            }
        }

        // V2.2: turn nearby OCR dialogue blocks into panel-like reading stops. A busy panel
        // should usually be one tap, not one tap per speech balloon. We deliberately keep the
        // grouping conservative: only dialogue that can comfortably share one reader viewport is
        // merged, while distant blocks remain separate stops.
        val panelStops = groupDialogueIntoPanelStops(deduped)

        return readingOrder(panelStops)
            .filter {
                it.width() in OCR_MIN_FINAL_WIDTH_RATIO..PANEL_STOP_MAX_FINAL_WIDTH_RATIO &&
                    it.height() in OCR_MIN_FINAL_HEIGHT_RATIO..PANEL_STOP_MAX_FINAL_HEIGHT_RATIO
            }
            .take(MAX_PANEL_STOPS_PER_PAGE)
    }

    /**
     * ML Kit occasionally treats several visually separate bits of lettering as one TextBlock on
     * dense/complex comic pages. When that happens, using the whole block creates one oversized or
     * badly centred Smart Bubble target. Keep normal blocks intact, but split unusually large or
     * sparse blocks into small line clusters before the existing panel grouping pass.
     */
    private fun ocrRegionsForBlock(
        block: Text.TextBlock,
        width: Int,
        height: Int,
    ): List<RectF> {
        val box = block.boundingBox ?: return emptyList()
        if (!isUsefulOcrBlock(block, width, height)) return emptyList()

        val blockRect =
            RectF(
                box.left.toFloat() / width,
                box.top.toFloat() / height,
                box.right.toFloat() / width,
                box.bottom.toFloat() / height,
            )

        val rawLines =
            block.lines.mapNotNull { line ->
                val lineBox = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim()
                val visible = text.count { !it.isWhitespace() }
                val alphaNumeric = text.count { it.isLetterOrDigit() }
                if (visible < OCR_LINE_MIN_VISIBLE_CHARS || alphaNumeric < OCR_LINE_MIN_ALPHANUMERIC_CHARS) {
                    return@mapNotNull null
                }

                RectF(
                    lineBox.left.toFloat() / width,
                    lineBox.top.toFloat() / height,
                    lineBox.right.toFloat() / width,
                    lineBox.bottom.toFloat() / height,
                )
            }

        if (rawLines.size < 2) return listOf(padOcrRegion(blockRect))

        val lineClusters = clusterOcrLines(rawLines)
        if (lineClusters.size < 2) return listOf(padOcrRegion(blockRect))

        val blockArea = (blockRect.width() * blockRect.height()).coerceAtLeast(0.0001f)
        val lineArea = rawLines.sumOf { (it.width() * it.height()).toDouble() }.toFloat()
        val lineCoverage = lineArea / blockArea
        val unusuallyLarge =
            blockRect.width() >= OCR_COMPLEX_BLOCK_WIDTH_RATIO ||
                blockRect.height() >= OCR_COMPLEX_BLOCK_HEIGHT_RATIO ||
                blockArea >= OCR_COMPLEX_BLOCK_AREA_RATIO
        val unusuallySparse = lineCoverage <= OCR_COMPLEX_BLOCK_MAX_LINE_COVERAGE

        return if (unusuallyLarge || unusuallySparse) {
            lineClusters.map(::padOcrRegion)
        } else {
            listOf(padOcrRegion(blockRect))
        }
    }

    private fun clusterOcrLines(lines: List<RectF>): List<RectF> {
        val groups = mutableListOf<RectF>()
        for (line in lines.sortedBy { it.top }) {
            var bestIndex = -1
            var bestDistance = Float.MAX_VALUE

            val firstIndex = (groups.size - OCR_LINE_CLUSTER_LOOKBACK).coerceAtLeast(0)
            for (index in groups.lastIndex downTo firstIndex) {
                val group = groups[index]
                val verticalGap = verticalGap(group, line)
                val horizontalOverlap = horizontalOverlapRatio(group, line)
                val centerDistance = abs(group.centerX() - line.centerX())
                val union = combineUnion(group, line)

                val belongsTogether =
                    verticalGap <= OCR_LINE_CLUSTER_MAX_VERTICAL_GAP &&
                        (horizontalOverlap >= OCR_LINE_CLUSTER_MIN_HORIZONTAL_OVERLAP ||
                            centerDistance <= OCR_LINE_CLUSTER_CENTER_X_TOLERANCE) &&
                        union.height() <= OCR_LINE_CLUSTER_MAX_HEIGHT_RATIO &&
                        union.width() <= OCR_LINE_CLUSTER_MAX_WIDTH_RATIO
                if (!belongsTogether) continue

                val distance = verticalGap + centerDistance * 0.35f
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }

            if (bestIndex >= 0) {
                groups[bestIndex].set(combineUnion(groups[bestIndex], line))
            } else {
                groups += RectF(line)
            }
        }
        return groups
    }

    private fun padOcrRegion(rect: RectF): RectF {
        val padX = max(rect.width() * OCR_PADDING_X_FRACTION, OCR_MIN_PADDING_X_RATIO)
        val padY = max(rect.height() * OCR_PADDING_Y_FRACTION, OCR_MIN_PADDING_Y_RATIO)
        return RectF(
            (rect.left - padX).coerceIn(0f, 1f),
            (rect.top - padY).coerceIn(0f, 1f),
            (rect.right + padX).coerceIn(0f, 1f),
            (rect.bottom + padY).coerceIn(0f, 1f),
        )
    }

    private fun isUsefulOcrBlock(block: Text.TextBlock, width: Int, height: Int): Boolean {
        val box = block.boundingBox ?: return false
        val text = block.text.trim()
        val visibleChars = text.count { !it.isWhitespace() }
        val alphaNumeric = text.count { it.isLetterOrDigit() }
        if (visibleChars < OCR_MIN_VISIBLE_CHARS || alphaNumeric < OCR_MIN_ALPHANUMERIC_CHARS) return false

        val widthRatio = box.width().toFloat() / width
        val heightRatio = box.height().toFloat() / height
        val areaRatio = widthRatio * heightRatio
        if (widthRatio !in OCR_RAW_MIN_WIDTH_RATIO..OCR_RAW_MAX_WIDTH_RATIO) return false
        if (heightRatio !in OCR_RAW_MIN_HEIGHT_RATIO..OCR_RAW_MAX_HEIGHT_RATIO) return false
        if (areaRatio > OCR_RAW_MAX_AREA_RATIO) return false

        // Reject mostly-symbol noise while still allowing short comic dialogue such as “NO!” or “OK”.
        val alphaNumericFraction = alphaNumeric.toFloat() / visibleChars.coerceAtLeast(1)
        if (alphaNumericFraction < OCR_MIN_ALPHANUMERIC_FRACTION) return false

        return true
    }

    private suspend fun recognizeText(bitmap: Bitmap): Text =
        suspendCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    recognizer.close()
                    continuation.resume(result)
                }
                .addOnFailureListener { error ->
                    recognizer.close()
                    continuation.resumeWithException(error)
                }
        }

    private fun Bitmap.forHeuristicFallback(): Bitmap {
        val longest = max(width, height)
        if (longest <= HEURISTIC_MAX_ANALYSIS_DIMENSION) return this
        val scale = HEURISTIC_MAX_ANALYSIS_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun detect(bitmap: Bitmap): List<RectF> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 40 || height < 40) return emptyList()

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Build dark letter/word components once. V1.4 also uses them to validate bright
        // candidates so plain white artwork does not become a Smart Bubble target.
        val darkComponents = findLetterLikeComponents(pixels, width, height, ::isTextDark)
        val balloonRegions = detectBrightBalloonRegions(pixels, width, height, darkComponents)
        val textRegions = detectTextRegions(pixels, width, height, darkComponents)
        val inverseTextRegions = detectInverseTextRegions(pixels, width, height)

        val combined = mutableListOf<RectF>()
        combined += balloonRegions
        for (textRegion in textRegions + inverseTextRegions) {
            val alreadyCovered =
                combined.any { existing ->
                    overlapRatio(existing, textRegion) >= TEXT_BALLOON_OVERLAP ||
                        containsCenter(existing, textRegion)
                }
            if (!alreadyCovered) combined += textRegion
        }

        if (combined.isEmpty()) return emptyList()

        return readingOrder(mergeNearby(combined))
            .filter {
                it.width() in MIN_FINAL_WIDTH_RATIO..MAX_FINAL_WIDTH_RATIO &&
                    it.height() in MIN_FINAL_HEIGHT_RATIO..MAX_FINAL_HEIGHT_RATIO
            }
            .take(MAX_BUBBLES_PER_PAGE)
    }

    /** Finds enclosed light balloons/caption boxes. */
    private fun detectBrightBalloonRegions(
        pixels: IntArray,
        width: Int,
        height: Int,
        darkComponents: List<InkComponent>,
    ): List<RectF> {
        val total = width * height
        val visited = BooleanArray(total)
        val stack = IntArray(total)
        val candidates = mutableListOf<RectF>()

        for (start in 0 until total) {
            if (visited[start]) continue
            visited[start] = true
            if (!isBalloonLight(pixels[start])) continue

            var stackSize = 0
            stack[stackSize++] = start

            var area = 0
            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0

            while (stackSize > 0) {
                val index = stack[--stackSize]
                val x = index % width
                val y = index / width
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                fun visit(next: Int) {
                    if (next !in 0 until total || visited[next]) return
                    visited[next] = true
                    if (isBalloonLight(pixels[next])) {
                        stack[stackSize++] = next
                    }
                }

                if (x > 0) visit(index - 1)
                if (x + 1 < width) visit(index + 1)
                if (y > 0) visit(index - width)
                if (y + 1 < height) visit(index + width)
            }

            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            val boxArea = boxWidth * boxHeight
            if (boxArea <= 0) continue

            val widthRatio = boxWidth.toFloat() / width
            val heightRatio = boxHeight.toFloat() / height
            val pageAreaRatio = boxArea.toFloat() / total
            val fillRatio = area.toFloat() / boxArea

            if (widthRatio !in LIGHT_MIN_WIDTH_RATIO..LIGHT_MAX_WIDTH_RATIO ||
                heightRatio !in LIGHT_MIN_HEIGHT_RATIO..LIGHT_MAX_HEIGHT_RATIO ||
                pageAreaRatio !in LIGHT_MIN_PAGE_AREA_RATIO..LIGHT_MAX_PAGE_AREA_RATIO ||
                fillRatio < LIGHT_MIN_FILL_RATIO
            ) {
                continue
            }

            val darkRatio = darkInkRatio(pixels, width, minX, minY, maxX, maxY)
            if (darkRatio !in MIN_DARK_INK_RATIO..MAX_DARK_INK_RATIO) continue

            // Text creates repeated light/dark changes across rows. This filters a lot of bright
            // sky/cloud/art regions while retaining caption boxes and speech balloons.
            if (inkTransitionScore(pixels, width, minX, minY, maxX, maxY) < MIN_TRANSITION_SCORE) {
                continue
            }

            // V1.4: require actual letter/word-like components *inside* the bright region.
            // Previously a white shirt, cloud, window or large pale patch with a few inked edges
            // could satisfy the brightness + transition tests even though it contained no dialogue.
            val candidateRect =
                RectF(
                    minX.toFloat(),
                    minY.toFloat(),
                    (maxX + 1).toFloat(),
                    (maxY + 1).toFloat(),
                )
            val insideComponents =
                darkComponents.filter { component ->
                    candidateRect.contains(component.rect.centerX(), component.rect.centerY())
                }
            val candidateLines =
                groupComponentsIntoLines(insideComponents, width, height)
                    .filter { line ->
                        line.componentCount >= BRIGHT_MIN_COMPONENTS_PER_LINE &&
                            line.rect.width() / width >= BRIGHT_TEXT_LINE_MIN_WIDTH_RATIO
                    }

            val isLargeBrightRegion =
                pageAreaRatio >= LARGE_BRIGHT_REGION_AREA_RATIO ||
                    widthRatio >= LARGE_BRIGHT_REGION_WIDTH_RATIO ||
                    heightRatio >= LARGE_BRIGHT_REGION_HEIGHT_RATIO
            val requiredComponents =
                if (isLargeBrightRegion) LARGE_BRIGHT_MIN_COMPONENTS else BRIGHT_MIN_COMPONENTS
            val requiredLines =
                if (isLargeBrightRegion) LARGE_BRIGHT_MIN_TEXT_LINES else BRIGHT_MIN_TEXT_LINES

            if (insideComponents.size < requiredComponents || candidateLines.size < requiredLines) {
                continue
            }

            // Border outlines alone are not enough. Proper balloons normally contain ink in their
            // interior, while many false white artwork candidates only have dark pixels at edges.
            val insetX = (boxWidth * BRIGHT_INTERIOR_INSET_FRACTION).toInt().coerceAtLeast(1)
            val insetY = (boxHeight * BRIGHT_INTERIOR_INSET_FRACTION).toInt().coerceAtLeast(1)
            val innerMinX = (minX + insetX).coerceAtMost(maxX)
            val innerMaxX = (maxX - insetX).coerceAtLeast(innerMinX)
            val innerMinY = (minY + insetY).coerceAtMost(maxY)
            val innerMaxY = (maxY - insetY).coerceAtLeast(innerMinY)
            val interiorDarkRatio =
                darkInkRatio(pixels, width, innerMinX, innerMinY, innerMaxX, innerMaxY)
            val requiredInteriorDark =
                if (isLargeBrightRegion) LARGE_BRIGHT_MIN_INTERIOR_DARK_RATIO else BRIGHT_MIN_INTERIOR_DARK_RATIO
            if (interiorDarkRatio < requiredInteriorDark) continue

            candidates +=
                paddedNormalizedRect(
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    width = width,
                    height = height,
                    padFraction = BALLOON_PADDING_FRACTION,
                    minPadding = BALLOON_MIN_PADDING,
                )
        }

        return candidates
    }

    /**
     * Fallback pass: find small dark components, group them into text lines/blocks, then keep only
     * groups sitting on a sufficiently bright local background. This helps with off-white balloons,
     * open tails, caption boxes, and balloons whose outline breaks the bright-region pass.
     */
    private fun detectTextRegions(
        pixels: IntArray,
        width: Int,
        height: Int,
        components: List<InkComponent>,
    ): List<RectF> {
        if (components.isEmpty()) return emptyList()

        val lines = groupComponentsIntoLines(components, width, height)
            .filter { line ->
                line.componentCount >= MIN_COMPONENTS_PER_LINE &&
                    line.rect.width() / width >= TEXT_LINE_MIN_WIDTH_RATIO
            }
        if (lines.isEmpty()) return emptyList()

        val blocks = groupLinesIntoBlocks(lines, width, height)
        return blocks.mapNotNull { block ->
            val rect = block.rect
            val minX = rect.left.toInt().coerceIn(0, width - 1)
            val minY = rect.top.toInt().coerceIn(0, height - 1)
            val maxX = rect.right.toInt().coerceIn(minX, width - 1)
            val maxY = rect.bottom.toInt().coerceIn(minY, height - 1)

            val expanded = expandPixelRect(rect, width, height, TEXT_BACKGROUND_PAD_X, TEXT_BACKGROUND_PAD_Y)
            val bgMinX = expanded.left.toInt().coerceIn(0, width - 1)
            val bgMinY = expanded.top.toInt().coerceIn(0, height - 1)
            val bgMaxX = expanded.right.toInt().coerceIn(bgMinX, width - 1)
            val bgMaxY = expanded.bottom.toInt().coerceIn(bgMinY, height - 1)

            val lightFraction = lightBackgroundFraction(pixels, width, bgMinX, bgMinY, bgMaxX, bgMaxY)
            val darkRatio = darkInkRatio(pixels, width, minX, minY, maxX, maxY)
            val widthRatio = rect.width() / width
            val heightRatio = rect.height() / height

            if (lightFraction < TEXT_MIN_LIGHT_BACKGROUND ||
                darkRatio !in TEXT_MIN_DARK_RATIO..TEXT_MAX_DARK_RATIO ||
                widthRatio !in TEXT_BLOCK_MIN_WIDTH_RATIO..TEXT_BLOCK_MAX_WIDTH_RATIO ||
                heightRatio !in TEXT_BLOCK_MIN_HEIGHT_RATIO..TEXT_BLOCK_MAX_HEIGHT_RATIO
            ) {
                null
            } else {
                paddedNormalizedRect(
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    width = width,
                    height = height,
                    padFraction = TEXT_PADDING_FRACTION,
                    minPadding = TEXT_MIN_PADDING,
                )
            }
        }
    }

    /**
     * Inverse fallback for balloons/captions such as dark teal boxes with white lettering.
     * It deliberately requires both letter-like light components and a mostly dark/coloured local
     * background so white highlights in the artwork do not become navigation targets too easily.
     */
    private fun detectInverseTextRegions(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): List<RectF> {
        val components = findLetterLikeComponents(pixels, width, height, ::isTextLight)
        if (components.isEmpty()) return emptyList()

        val lines =
            groupComponentsIntoLines(components, width, height)
                .filter { line ->
                    line.componentCount >= INVERSE_MIN_COMPONENTS_PER_LINE &&
                        line.rect.width() / width >= INVERSE_LINE_MIN_WIDTH_RATIO
                }
        if (lines.isEmpty()) return emptyList()

        val blocks = groupLinesIntoBlocks(lines, width, height)
        return blocks.mapNotNull { block ->
            val rect = block.rect
            val widthRatio = rect.width() / width
            val heightRatio = rect.height() / height
            if (widthRatio !in INVERSE_BLOCK_MIN_WIDTH_RATIO..INVERSE_BLOCK_MAX_WIDTH_RATIO ||
                heightRatio !in INVERSE_BLOCK_MIN_HEIGHT_RATIO..INVERSE_BLOCK_MAX_HEIGHT_RATIO
            ) {
                return@mapNotNull null
            }

            val expanded =
                expandPixelRect(
                    rect,
                    width,
                    height,
                    INVERSE_BACKGROUND_PAD_X,
                    INVERSE_BACKGROUND_PAD_Y,
                )
            val minX = expanded.left.toInt().coerceIn(0, width - 1)
            val minY = expanded.top.toInt().coerceIn(0, height - 1)
            val maxX = expanded.right.toInt().coerceIn(minX, width - 1)
            val maxY = expanded.bottom.toInt().coerceIn(minY, height - 1)

            if (darkOrColouredBackgroundFraction(pixels, width, minX, minY, maxX, maxY) < INVERSE_MIN_BACKGROUND_FRACTION) {
                null
            } else {
                paddedNormalizedRect(
                    minX = rect.left.toInt().coerceIn(0, width - 1),
                    minY = rect.top.toInt().coerceIn(0, height - 1),
                    maxX = rect.right.toInt().coerceIn(0, width - 1),
                    maxY = rect.bottom.toInt().coerceIn(0, height - 1),
                    width = width,
                    height = height,
                    padFraction = INVERSE_PADDING_FRACTION,
                    minPadding = INVERSE_MIN_PADDING,
                )
            }
        }
    }

    private data class InkComponent(
        val rect: RectF,
        val area: Int,
    )

    private data class TextLine(
        val rect: RectF,
        var componentCount: Int,
        var inkArea: Int,
    )

    private data class TextBlock(
        val rect: RectF,
        var lineCount: Int,
    )

    private fun findLetterLikeComponents(
        pixels: IntArray,
        width: Int,
        height: Int,
        matchesInk: (Int) -> Boolean,
    ): List<InkComponent> {
        val total = width * height
        val visited = BooleanArray(total)
        val stack = IntArray(total)
        val result = mutableListOf<InkComponent>()

        for (start in 0 until total) {
            if (visited[start]) continue
            visited[start] = true
            if (!matchesInk(pixels[start])) continue

            var stackSize = 0
            stack[stackSize++] = start
            var area = 0
            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0

            while (stackSize > 0) {
                val index = stack[--stackSize]
                val x = index % width
                val y = index / width
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                fun visit(nx: Int, ny: Int) {
                    if (nx !in 0 until width || ny !in 0 until height) return
                    val next = ny * width + nx
                    if (visited[next]) return
                    visited[next] = true
                    if (matchesInk(pixels[next])) stack[stackSize++] = next
                }

                // 8-connected so anti-aliased/diagonal letter strokes stay together.
                visit(x - 1, y - 1)
                visit(x, y - 1)
                visit(x + 1, y - 1)
                visit(x - 1, y)
                visit(x + 1, y)
                visit(x - 1, y + 1)
                visit(x, y + 1)
                visit(x + 1, y + 1)
            }

            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            val boxArea = boxWidth * boxHeight
            if (boxArea <= 0) continue

            val widthRatio = boxWidth.toFloat() / width
            val heightRatio = boxHeight.toFloat() / height
            val areaRatio = area.toFloat() / total
            val fillRatio = area.toFloat() / boxArea

            if (area < LETTER_MIN_AREA ||
                widthRatio !in LETTER_MIN_WIDTH_RATIO..LETTER_MAX_WIDTH_RATIO ||
                heightRatio !in LETTER_MIN_HEIGHT_RATIO..LETTER_MAX_HEIGHT_RATIO ||
                areaRatio > LETTER_MAX_PAGE_AREA_RATIO ||
                fillRatio < LETTER_MIN_FILL_RATIO
            ) {
                continue
            }

            result += InkComponent(RectF(minX.toFloat(), minY.toFloat(), (maxX + 1).toFloat(), (maxY + 1).toFloat()), area)
        }

        return result
    }

    private fun groupComponentsIntoLines(
        components: List<InkComponent>,
        width: Int,
        height: Int,
    ): List<TextLine> {
        val lines = mutableListOf<TextLine>()
        val ordered = components.sortedWith(compareBy<InkComponent> { it.rect.centerY() }.thenBy { it.rect.left })

        for (component in ordered) {
            val best =
                lines
                    .filter { line -> sameTextLine(line.rect, component.rect, width, height) }
                    .minByOrNull { line -> horizontalGap(line.rect, component.rect) + abs(line.rect.centerY() - component.rect.centerY()) }

            if (best == null) {
                lines += TextLine(RectF(component.rect), 1, component.area)
            } else {
                best.rect.union(component.rect)
                best.componentCount++
                best.inkArea += component.area
            }
        }
        return lines
    }

    private fun sameTextLine(
        line: RectF,
        item: RectF,
        width: Int,
        height: Int,
    ): Boolean {
        val yTolerance = max(height * LINE_CENTER_Y_TOLERANCE, max(line.height(), item.height()) * LINE_HEIGHT_MULTIPLIER)
        if (abs(line.centerY() - item.centerY()) > yTolerance) return false

        val gap = horizontalGap(line, item)
        val gapLimit = max(width * LINE_MAX_GAP_RATIO, max(line.height(), item.height()) * LINE_GAP_HEIGHT_MULTIPLIER)
        return gap <= gapLimit
    }

    private fun groupLinesIntoBlocks(
        lines: List<TextLine>,
        width: Int,
        height: Int,
    ): List<TextBlock> {
        val blocks = mutableListOf<TextBlock>()
        for (line in lines.sortedBy { it.rect.top }) {
            val best =
                blocks
                    .filter { block -> sameTextBlock(block.rect, line.rect, width, height) }
                    .minByOrNull { block -> verticalGap(block.rect, line.rect) + abs(block.rect.centerX() - line.rect.centerX()) * 0.15f }

            if (best == null) {
                blocks += TextBlock(RectF(line.rect), 1)
            } else {
                best.rect.union(line.rect)
                best.lineCount++
            }
        }
        return blocks
    }

    private fun sameTextBlock(
        block: RectF,
        line: RectF,
        width: Int,
        height: Int,
    ): Boolean {
        val gap = verticalGap(block, line)
        val maxGap = max(height * BLOCK_MAX_VERTICAL_GAP_RATIO, line.height() * BLOCK_LINE_HEIGHT_MULTIPLIER)
        if (gap > maxGap) return false

        val horizontalOverlap = horizontalOverlapRatio(block, line)
        val centersClose = abs(block.centerX() - line.centerX()) <= width * BLOCK_CENTER_X_TOLERANCE
        if (!centersClose && horizontalOverlap < BLOCK_MIN_HORIZONTAL_OVERLAP) return false

        val union = RectF(block).apply { union(line) }
        return union.width() / width <= TEXT_BLOCK_MAX_WIDTH_RATIO &&
            union.height() / height <= TEXT_BLOCK_MAX_HEIGHT_RATIO
    }

    private fun isBalloonLight(pixel: Int): Boolean {
        val r = pixel shr 16 and 0xff
        val g = pixel shr 8 and 0xff
        val b = pixel and 0xff
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        return luminance(pixel) >= LIGHT_LUMINANCE && maxChannel - minChannel <= MAX_LIGHT_CHROMA
    }

    private fun isTextDark(pixel: Int): Boolean = luminance(pixel) <= TEXT_DARK_LUMINANCE

    private fun isTextLight(pixel: Int): Boolean {
        val r = pixel shr 16 and 0xff
        val g = pixel shr 8 and 0xff
        val b = pixel and 0xff
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        return luminance(pixel) >= INVERSE_TEXT_MIN_LUMINANCE &&
            maxChannel - minChannel <= INVERSE_TEXT_MAX_CHROMA
    }

    private fun luminance(pixel: Int): Int {
        val r = pixel shr 16 and 0xff
        val g = pixel shr 8 and 0xff
        val b = pixel and 0xff
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun darkInkRatio(
        pixels: IntArray,
        width: Int,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
    ): Float {
        val boxWidth = maxX - minX + 1
        val boxHeight = maxY - minY + 1
        val step = max(1, min(boxWidth, boxHeight) / 80)

        var dark = 0
        var sampled = 0
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                if (luminance(pixels[y * width + x]) <= DARK_LUMINANCE) dark++
                sampled++
                x += step
            }
            y += step
        }
        return if (sampled == 0) 0f else dark.toFloat() / sampled
    }

    private fun lightBackgroundFraction(
        pixels: IntArray,
        width: Int,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
    ): Float {
        val step = max(1, min(maxX - minX + 1, maxY - minY + 1) / 70)
        var light = 0
        var sampled = 0
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                if (luminance(pixels[y * width + x]) >= TEXT_BACKGROUND_LUMINANCE) light++
                sampled++
                x += step
            }
            y += step
        }
        return if (sampled == 0) 0f else light.toFloat() / sampled
    }

    private fun darkOrColouredBackgroundFraction(
        pixels: IntArray,
        width: Int,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
    ): Float {
        val step = max(1, min(maxX - minX + 1, maxY - minY + 1) / 70)
        var matching = 0
        var sampled = 0
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                val pixel = pixels[y * width + x]
                val r = pixel shr 16 and 0xff
                val g = pixel shr 8 and 0xff
                val b = pixel and 0xff
                val maxChannel = max(r, max(g, b))
                val minChannel = min(r, min(g, b))
                val lum = luminance(pixel)
                val chroma = maxChannel - minChannel
                if (lum <= INVERSE_BACKGROUND_MAX_LUMINANCE ||
                    (lum <= INVERSE_COLOURED_MAX_LUMINANCE && chroma >= INVERSE_BACKGROUND_MIN_CHROMA)
                ) {
                    matching++
                }
                sampled++
                x += step
            }
            y += step
        }
        return if (sampled == 0) 0f else matching.toFloat() / sampled
    }

    private fun inkTransitionScore(
        pixels: IntArray,
        width: Int,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
    ): Float {
        val boxHeight = maxY - minY + 1
        val yStep = max(1, boxHeight / 36)
        val xStep = max(1, (maxX - minX + 1) / 120)
        var rows = 0
        var transitions = 0

        var y = minY
        while (y <= maxY) {
            var x = minX
            var previousDark = luminance(pixels[y * width + x]) <= TRANSITION_DARK_LUMINANCE
            var rowTransitions = 0
            x += xStep
            while (x <= maxX) {
                val dark = luminance(pixels[y * width + x]) <= TRANSITION_DARK_LUMINANCE
                if (dark != previousDark) rowTransitions++
                previousDark = dark
                x += xStep
            }
            transitions += rowTransitions
            rows++
            y += yStep
        }
        return if (rows == 0) 0f else transitions.toFloat() / rows
    }

    private fun paddedNormalizedRect(
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
        width: Int,
        height: Int,
        padFraction: Float,
        minPadding: Float,
    ): RectF {
        val left = minX.toFloat() / width
        val top = minY.toFloat() / height
        val right = (maxX + 1).toFloat() / width
        val bottom = (maxY + 1).toFloat() / height

        val padX = max(minPadding, (right - left) * padFraction)
        val padY = max(minPadding, (bottom - top) * padFraction)
        return RectF(
            (left - padX).coerceAtLeast(0f),
            (top - padY).coerceAtLeast(0f),
            (right + padX).coerceAtMost(1f),
            (bottom + padY).coerceAtMost(1f),
        )
    }

    private fun expandPixelRect(
        rect: RectF,
        width: Int,
        height: Int,
        padXRatio: Float,
        padYRatio: Float,
    ): RectF =
        RectF(
            (rect.left - width * padXRatio).coerceAtLeast(0f),
            (rect.top - height * padYRatio).coerceAtLeast(0f),
            (rect.right + width * padXRatio).coerceAtMost(width.toFloat()),
            (rect.bottom + height * padYRatio).coerceAtMost(height.toFloat()),
        )

    private fun combineUnion(a: RectF, b: RectF): RectF = RectF(a).apply { union(b) }

    private fun mergeNearby(input: List<RectF>): List<RectF> {
        val output = mutableListOf<RectF>()
        for (candidate in input.sortedByDescending { it.width() * it.height() }) {
            val existing =
                output.firstOrNull {
                    overlapRatio(it, candidate) >= MERGE_OVERLAP_RATIO ||
                        centersVeryClose(it, candidate)
                }
            if (existing == null) {
                output += RectF(candidate)
            } else {
                val union = combineUnion(existing, candidate)
                if (union.width() <= MAX_MERGED_WIDTH_RATIO && union.height() <= MAX_MERGED_HEIGHT_RATIO) {
                    existing.set(union)
                }
            }
        }
        return output
    }

    private fun overlapRatio(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val overlap = (right - left) * (bottom - top)
        val smaller = min(a.width() * a.height(), b.width() * b.height()).coerceAtLeast(0.0001f)
        return overlap / smaller
    }

    private fun containsCenter(container: RectF, child: RectF): Boolean =
        container.contains(child.centerX(), child.centerY())

    private fun horizontalGap(a: RectF, b: RectF): Float =
        when {
            a.right < b.left -> b.left - a.right
            b.right < a.left -> a.left - b.right
            else -> 0f
        }

    private fun verticalGap(a: RectF, b: RectF): Float =
        when {
            a.bottom < b.top -> b.top - a.bottom
            b.bottom < a.top -> a.top - b.bottom
            else -> 0f
        }

    private fun horizontalOverlapRatio(a: RectF, b: RectF): Float {
        val overlap = min(a.right, b.right) - max(a.left, b.left)
        if (overlap <= 0f) return 0f
        return overlap / min(a.width(), b.width()).coerceAtLeast(1f)
    }

    private fun centersVeryClose(a: RectF, b: RectF): Boolean =
        abs(a.centerX() - b.centerX()) < MERGE_CENTER_X &&
            abs(a.centerY() - b.centerY()) < MERGE_CENTER_Y

    /**
     * V2.2 panel-aware-ish grouping for OCR dialogue.
     *
     * This does not pretend to be a full computer-vision panel detector. Instead it uses the
     * geometry of reliable OCR targets to collapse nearby dialogue into a single reading stop
     * when all of it still fits comfortably in one viewport. This removes the tedious
     * bubble-by-bubble stepping in dialogue-heavy panels while preserving separate stops for
     * clearly separated panels.
     */
    private fun groupDialogueIntoPanelStops(input: List<RectF>): List<RectF> {
        if (input.size < 2) return input

        val groups = mutableListOf<RectF>()
        val groupTargetCounts = mutableListOf<Int>()
        for (candidate in readingOrder(input)) {
            var bestIndex = -1
            var bestDistance = Float.MAX_VALUE

            // Only look back over the most recent visual stops. This prevents a block low on the
            // page from being pulled into an older panel just because their x positions align.
            val firstIndex = (groups.size - PANEL_GROUP_LOOKBACK).coerceAtLeast(0)
            for (index in groups.lastIndex downTo firstIndex) {
                // Busy dialogue panels were becoming one very large target, which made Panel Flow
                // zoom too far out. Keep a useful amount of grouping, but split a busy panel into
                // a couple of readable beats instead of swallowing every balloon into one stop.
                if (groupTargetCounts[index] >= PANEL_GROUP_MAX_TARGETS_PER_STOP) continue

                val existing = groups[index]
                if (!canSharePanelStop(existing, candidate)) continue

                val dx = existing.centerX() - candidate.centerX()
                val dy = existing.centerY() - candidate.centerY()
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }

            if (bestIndex >= 0) {
                groups[bestIndex].set(combineUnion(groups[bestIndex], candidate))
                groupTargetCounts[bestIndex] += 1
            } else {
                groups += RectF(candidate)
                groupTargetCounts += 1
            }
        }

        // V2.3 deliberately stops after one conservative pass. V2.2's second consolidation
        // could chain several balloons together until a whole busy panel became one large stop.
        // We now merge only genuinely close dialogue pairs/clusters and preserve more individual
        // reading beats inside a panel.
        return groups
    }

    private fun canSharePanelStop(a: RectF, b: RectF): Boolean {
        val union = combineUnion(a, b)
        if (union.width() > PANEL_STOP_MAX_WIDTH_RATIO || union.height() > PANEL_STOP_MAX_HEIGHT_RATIO) {
            return false
        }
        if (union.width() * union.height() > PANEL_STOP_MAX_AREA_RATIO) return false

        val hGap = horizontalGap(a, b)
        val vGap = verticalGap(a, b)
        val hOverlap = horizontalOverlapRatio(a, b)
        val vOverlap = verticalOverlapRatio(a, b)
        val dx = abs(a.centerX() - b.centerX())
        val dy = abs(a.centerY() - b.centerY())

        // Common comic-panel dialogue layouts:
        //  - balloons/captions stacked in the same panel,
        //  - balloons beside each other across a wide panel,
        //  - tightly clustered dialogue with slight diagonal offset.
        val stacked = vGap <= PANEL_GROUP_MAX_VERTICAL_GAP && (hOverlap >= PANEL_GROUP_MIN_AXIS_OVERLAP || dx <= PANEL_GROUP_CENTER_X_TOLERANCE)
        val sideBySide = hGap <= PANEL_GROUP_MAX_HORIZONTAL_GAP && (vOverlap >= PANEL_GROUP_MIN_AXIS_OVERLAP || dy <= PANEL_GROUP_CENTER_Y_TOLERANCE)
        val tightCluster = dx <= PANEL_GROUP_TIGHT_CENTER_DISTANCE && dy <= PANEL_GROUP_TIGHT_CENTER_DISTANCE

        return stacked || sideBySide || tightCluster
    }

    private fun verticalOverlapRatio(a: RectF, b: RectF): Float {
        val overlap = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (overlap <= 0f) return 0f
        return overlap / min(a.height(), b.height()).coerceAtLeast(0.0001f)
    }

    /** Western-comic ordering: top-to-bottom, then left-to-right inside a visual row. */
    private fun readingOrder(input: List<RectF>): List<RectF> {
        val rows = mutableListOf<MutableList<RectF>>()
        for (rect in input.sortedBy { it.centerY() }) {
            val row =
                rows.firstOrNull { existing ->
                    val meanY = existing.map { it.centerY() }.average().toFloat()
                    abs(meanY - rect.centerY()) <= ROW_TOLERANCE
                }
            if (row == null) {
                rows += mutableListOf(rect)
            } else {
                row += rect
            }
        }
        return rows
            .sortedBy { row -> row.minOf { it.top } }
            .flatMap { row -> row.sortedBy { it.centerX() } }
    }

    private const val OCR_MAX_ANALYSIS_DIMENSION = 1800
    private const val HEURISTIC_MAX_ANALYSIS_DIMENSION = 1100

    private const val OCR_MIN_VISIBLE_CHARS = 2
    private const val OCR_MIN_ALPHANUMERIC_CHARS = 2
    private const val OCR_MIN_ALPHANUMERIC_FRACTION = 0.40f
    private const val OCR_RAW_MIN_WIDTH_RATIO = 0.012f
    private const val OCR_RAW_MAX_WIDTH_RATIO = 0.86f
    private const val OCR_RAW_MIN_HEIGHT_RATIO = 0.005f
    private const val OCR_RAW_MAX_HEIGHT_RATIO = 0.48f
    private const val OCR_RAW_MAX_AREA_RATIO = 0.28f
    private const val OCR_PADDING_X_FRACTION = 0.24f
    private const val OCR_PADDING_Y_FRACTION = 0.30f
    private const val OCR_MIN_PADDING_X_RATIO = 0.014f
    private const val OCR_MIN_PADDING_Y_RATIO = 0.010f
    private const val OCR_DUPLICATE_OVERLAP = 0.78f

    // V2.5 complex-panel refinement. ML Kit can occasionally make one TextBlock span separated
    // dialogue/caption areas. Split only blocks that are large or visibly sparse so ordinary
    // multi-line speech balloons remain a single stop.
    private const val OCR_LINE_MIN_VISIBLE_CHARS = 2
    private const val OCR_LINE_MIN_ALPHANUMERIC_CHARS = 1
    private const val OCR_LINE_CLUSTER_LOOKBACK = 2
    private const val OCR_LINE_CLUSTER_MAX_VERTICAL_GAP = 0.024f
    private const val OCR_LINE_CLUSTER_MIN_HORIZONTAL_OVERLAP = 0.12f
    private const val OCR_LINE_CLUSTER_CENTER_X_TOLERANCE = 0.11f
    private const val OCR_LINE_CLUSTER_MAX_WIDTH_RATIO = 0.58f
    private const val OCR_LINE_CLUSTER_MAX_HEIGHT_RATIO = 0.20f
    private const val OCR_COMPLEX_BLOCK_WIDTH_RATIO = 0.52f
    private const val OCR_COMPLEX_BLOCK_HEIGHT_RATIO = 0.18f
    private const val OCR_COMPLEX_BLOCK_AREA_RATIO = 0.065f
    private const val OCR_COMPLEX_BLOCK_MAX_LINE_COVERAGE = 0.32f
    private const val OCR_MIN_FINAL_WIDTH_RATIO = 0.025f
    private const val OCR_MAX_FINAL_WIDTH_RATIO = 0.92f
    private const val OCR_MIN_FINAL_HEIGHT_RATIO = 0.010f
    private const val OCR_MAX_FINAL_HEIGHT_RATIO = 0.56f

    // V2.2 panel-like grouping. These limits are intentionally conservative so adjacent panels
    // are less likely to collapse into one stop. The resulting union naturally causes the reader
    // to zoom out enough to show all dialogue in that panel.
    private const val PANEL_GROUP_LOOKBACK = 1
    private const val PANEL_GROUP_MAX_HORIZONTAL_GAP = 0.038f
    private const val PANEL_GROUP_MAX_VERTICAL_GAP = 0.055f
    private const val PANEL_GROUP_MIN_AXIS_OVERLAP = 0.28f
    private const val PANEL_GROUP_CENTER_X_TOLERANCE = 0.14f
    private const val PANEL_GROUP_CENTER_Y_TOLERANCE = 0.11f
    private const val PANEL_GROUP_TIGHT_CENTER_DISTANCE = 0.10f
    // V2.7: keep grouped dialogue readable. A stop may contain a small conversation cluster, but
    // not an entire dialogue-heavy panel. This preserves the panel-aware feel without forcing the
    // camera almost back to full-page scale whenever a panel contains lots of speech balloons.
    private const val PANEL_GROUP_MAX_TARGETS_PER_STOP = 3
    private const val PANEL_STOP_MAX_WIDTH_RATIO = 0.44f
    private const val PANEL_STOP_MAX_HEIGHT_RATIO = 0.31f
    private const val PANEL_STOP_MAX_AREA_RATIO = 0.105f
    private const val PANEL_STOP_MAX_FINAL_WIDTH_RATIO = 0.94f
    private const val PANEL_STOP_MAX_FINAL_HEIGHT_RATIO = 0.62f
    private const val MAX_PANEL_STOPS_PER_PAGE = 18

    // Bright-balloon pass. Broader than V1 so off-white/cream/coloured balloons survive.
    private const val LIGHT_LUMINANCE = 165
    private const val MAX_LIGHT_CHROMA = 145
    private const val LIGHT_MIN_WIDTH_RATIO = 0.035f
    private const val LIGHT_MAX_WIDTH_RATIO = 0.86f
    private const val LIGHT_MIN_HEIGHT_RATIO = 0.014f
    private const val LIGHT_MAX_HEIGHT_RATIO = 0.46f
    private const val LIGHT_MIN_PAGE_AREA_RATIO = 0.0007f
    private const val LIGHT_MAX_PAGE_AREA_RATIO = 0.30f
    private const val LIGHT_MIN_FILL_RATIO = 0.22f

    private const val DARK_LUMINANCE = 135
    private const val MIN_DARK_INK_RATIO = 0.0025f
    private const val MAX_DARK_INK_RATIO = 0.40f
    private const val TRANSITION_DARK_LUMINANCE = 145
    private const val MIN_TRANSITION_SCORE = 0.34f

    private const val BALLOON_MIN_PADDING = 0.014f
    private const val BALLOON_PADDING_FRACTION = 0.10f

    // V1.4 bright-region validation. A bright shape must contain convincing text evidence, and
    // very large pale regions are held to a stricter standard to reject clouds/clothing/windows.
    private const val BRIGHT_MIN_COMPONENTS = 3
    private const val BRIGHT_MIN_TEXT_LINES = 1
    private const val BRIGHT_MIN_COMPONENTS_PER_LINE = 2
    private const val BRIGHT_TEXT_LINE_MIN_WIDTH_RATIO = 0.012f
    private const val BRIGHT_INTERIOR_INSET_FRACTION = 0.08f
    private const val BRIGHT_MIN_INTERIOR_DARK_RATIO = 0.0045f
    private const val LARGE_BRIGHT_REGION_AREA_RATIO = 0.055f
    private const val LARGE_BRIGHT_REGION_WIDTH_RATIO = 0.62f
    private const val LARGE_BRIGHT_REGION_HEIGHT_RATIO = 0.28f
    private const val LARGE_BRIGHT_MIN_COMPONENTS = 6
    private const val LARGE_BRIGHT_MIN_TEXT_LINES = 2
    private const val LARGE_BRIGHT_MIN_INTERIOR_DARK_RATIO = 0.008f

    // Text fallback pass.
    private const val TEXT_DARK_LUMINANCE = 125
    private const val LETTER_MIN_AREA = 2
    private const val LETTER_MIN_WIDTH_RATIO = 0.0007f
    private const val LETTER_MAX_WIDTH_RATIO = 0.075f
    private const val LETTER_MIN_HEIGHT_RATIO = 0.0025f
    private const val LETTER_MAX_HEIGHT_RATIO = 0.045f
    private const val LETTER_MAX_PAGE_AREA_RATIO = 0.0025f
    private const val LETTER_MIN_FILL_RATIO = 0.07f

    private const val MIN_COMPONENTS_PER_LINE = 3
    private const val TEXT_LINE_MIN_WIDTH_RATIO = 0.018f
    private const val LINE_CENTER_Y_TOLERANCE = 0.005f
    private const val LINE_HEIGHT_MULTIPLIER = 0.72f
    private const val LINE_MAX_GAP_RATIO = 0.018f
    private const val LINE_GAP_HEIGHT_MULTIPLIER = 1.8f

    private const val BLOCK_MAX_VERTICAL_GAP_RATIO = 0.024f
    private const val BLOCK_LINE_HEIGHT_MULTIPLIER = 1.7f
    private const val BLOCK_CENTER_X_TOLERANCE = 0.13f
    private const val BLOCK_MIN_HORIZONTAL_OVERLAP = 0.12f

    private const val TEXT_BACKGROUND_PAD_X = 0.018f
    private const val TEXT_BACKGROUND_PAD_Y = 0.012f
    private const val TEXT_BACKGROUND_LUMINANCE = 145
    private const val TEXT_MIN_LIGHT_BACKGROUND = 0.38f
    private const val TEXT_MIN_DARK_RATIO = 0.008f
    private const val TEXT_MAX_DARK_RATIO = 0.48f
    private const val TEXT_BLOCK_MIN_WIDTH_RATIO = 0.025f
    private const val TEXT_BLOCK_MAX_WIDTH_RATIO = 0.70f
    private const val TEXT_BLOCK_MIN_HEIGHT_RATIO = 0.008f
    private const val TEXT_BLOCK_MAX_HEIGHT_RATIO = 0.32f
    private const val TEXT_MIN_PADDING = 0.020f
    private const val TEXT_PADDING_FRACTION = 0.30f

    // Inverse text fallback: white/light lettering on dark or strongly coloured balloons.
    private const val INVERSE_TEXT_MIN_LUMINANCE = 188
    private const val INVERSE_TEXT_MAX_CHROMA = 80
    private const val INVERSE_MIN_COMPONENTS_PER_LINE = 3
    private const val INVERSE_LINE_MIN_WIDTH_RATIO = 0.020f
    private const val INVERSE_BLOCK_MIN_WIDTH_RATIO = 0.030f
    private const val INVERSE_BLOCK_MAX_WIDTH_RATIO = 0.72f
    private const val INVERSE_BLOCK_MIN_HEIGHT_RATIO = 0.010f
    private const val INVERSE_BLOCK_MAX_HEIGHT_RATIO = 0.36f
    private const val INVERSE_BACKGROUND_PAD_X = 0.022f
    private const val INVERSE_BACKGROUND_PAD_Y = 0.016f
    private const val INVERSE_BACKGROUND_MAX_LUMINANCE = 118
    private const val INVERSE_COLOURED_MAX_LUMINANCE = 165
    private const val INVERSE_BACKGROUND_MIN_CHROMA = 42
    private const val INVERSE_MIN_BACKGROUND_FRACTION = 0.42f
    private const val INVERSE_MIN_PADDING = 0.024f
    private const val INVERSE_PADDING_FRACTION = 0.42f

    // Candidate combination and ordering.
    private const val TEXT_BALLOON_OVERLAP = 0.16f
    private const val MERGE_OVERLAP_RATIO = 0.62f
    private const val MERGE_CENTER_X = 0.028f
    private const val MERGE_CENTER_Y = 0.025f
    private const val MAX_MERGED_WIDTH_RATIO = 0.88f
    private const val MAX_MERGED_HEIGHT_RATIO = 0.48f
    private const val MIN_FINAL_WIDTH_RATIO = 0.025f
    private const val MAX_FINAL_WIDTH_RATIO = 0.90f
    private const val MIN_FINAL_HEIGHT_RATIO = 0.010f
    private const val MAX_FINAL_HEIGHT_RATIO = 0.50f
    private const val ROW_TOLERANCE = 0.070f
    private const val MAX_BUBBLES_PER_PAGE = 32
}
