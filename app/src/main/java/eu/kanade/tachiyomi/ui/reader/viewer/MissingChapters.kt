package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlin.math.floor

private val pattern = Regex("""\d+""")

/**
 * Matches a leading merged chapter-number list/range, e.g. "#001,#002", "#000,001" or "#005-#008".
 * For sources/series that combine chapters into one entry
 */
private val chapterNumberListPattern = Regex("""^\s*#(\d+(?:\.\d+)?)(?:\s*([,-])\s*#?\d+(?:\.\d+)?)+""")
private val numberPattern = Regex("""\d+(?:\.\d+)?""")

/**
 * Parses a leading [chapterNumberListPattern] out of a chapter name into the set of
 * chapter numbers it covers. A comma for individual extra chapters; dash means an inclusive range.
 * Empty if no extras
 */
private fun parseExtraChapterNumbers(name: String): Set<Float> {
    val match = chapterNumberListPattern.find(name) ?: return emptySet()
    val numbers = numberPattern.findAll(match.value).map { it.value.toFloat() }.toList()
    if (numbers.size < 2) return emptySet()
    return if (match.groupValues[2] == "-") {
        (numbers.min().toInt()..numbers.max().toInt()).map { it.toFloat() }.toSet()
    } else {
        numbers.toSet()
    }
}

fun hasMissingChapters(
    higherReaderChapter: ReaderChapter?,
    lowerReaderChapter: ReaderChapter?,
): Boolean {
    if (higherReaderChapter == null || lowerReaderChapter == null) return false
    return hasMissingChapters(higherReaderChapter.chapter, lowerReaderChapter.chapter)
}

fun hasMissingChapters(
    higherChapter: Chapter?,
    lowerChapter: Chapter?,
): Boolean {
    if (higherChapter == null || lowerChapter == null) return false
    // Check if name contains a number that is potential chapter number
    if (!pattern.containsMatchIn(higherChapter.name) || !pattern.containsMatchIn(lowerChapter.name)) return false
    // Check if potential chapter number was recognized as chapter number
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return false
    return calculateChapterDifference(higherChapter, lowerChapter) > 0f
}

fun hasMissingChapters(
    higherChapterNumber: Float,
    lowerChapterNumber: Float,
): Boolean {
    if (higherChapterNumber < 0f || lowerChapterNumber < 0f) return false
    return calculateChapterDifference(higherChapterNumber, lowerChapterNumber) > 0f
}

fun calculateChapterDifference(
    higherReaderChapter: ReaderChapter?,
    lowerReaderChapter: ReaderChapter?,
): Float {
    if (higherReaderChapter == null || lowerReaderChapter == null) return 0f
    return calculateChapterDifference(higherReaderChapter.chapter, lowerReaderChapter.chapter)
}

fun calculateChapterDifference(
    higherChapter: Chapter?,
    lowerChapter: Chapter?,
): Float {
    if (higherChapter == null || lowerChapter == null) return 0f
    // Check if name contains a number that is potential chapter number
    if (!pattern.containsMatchIn(higherChapter.name) || !pattern.containsMatchIn(lowerChapter.name)) return 0f
    // Check if potential chapter number was recognized as chapter number
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return 0f
    val rawDifference = calculateChapterDifference(higherChapter.chapter_number, lowerChapter.chapter_number)
    if (rawDifference <= 0f) return rawDifference

    // Only parse merged chapter-number lists/ranges once a gap is actually found, since that's rare.
    val covered = parseExtraChapterNumbers(lowerChapter.name) + parseExtraChapterNumbers(higherChapter.name)
    if (covered.isEmpty()) return rawDifference

    val gapStart = floor(lowerChapter.chapter_number).toInt() + 1
    val gapEnd = floor(higherChapter.chapter_number).toInt() - 1
    return (gapStart..gapEnd).count { it.toFloat() !in covered }.toFloat()
}

fun calculateChapterDifference(
    higherChapterNumber: Float,
    lowerChapterNumber: Float,
): Float {
    if (higherChapterNumber < 0f || lowerChapterNumber < 0f) return 0f
    return floor(higherChapterNumber) - floor(lowerChapterNumber) - 1f
}

/**
 * Total missing chapter count across a full chapter list. Merge-aware, so it stays consistent with
 * the per-gap logic used to build the "Missing N chapters" divider items.
 */
fun missingChaptersCount(chapters: List<Chapter>): Int {
    val chaptersByFloor =
        chapters
            .filter { it.isRecognizedNumber }
            .associateBy { floor(it.chapter_number).toInt() }
    val floors = chaptersByFloor.keys.sorted()
    var missingCount = 0
    for (i in 1 until floors.size) {
        missingCount += calculateChapterDifference(chaptersByFloor.getValue(floors[i]), chaptersByFloor.getValue(floors[i - 1])).toInt()
    }
    return missingCount
}

/**
 * True if [candidateChapter] looks like a mis-numbered bonus/extra chapter rather than a genuine
 * gap in the main numbering: [prevChapter] and [nextChapter] are themselves (near-)consecutive,
 * but [candidateChapter] sits far outside that pair's number range. Sources label these chapters
 * inconsistently (e.g. "ex - Bonus Chapter 38" between chapters 174 and 175), so this only catches
 * a single stray chapter wedged between two chapters that are truly adjacent to each other.
 */
fun isChapterNumberOutlier(
    prevChapter: Chapter,
    candidateChapter: Chapter,
    nextChapter: Chapter,
): Boolean {
    if (!prevChapter.isRecognizedNumber || !candidateChapter.isRecognizedNumber || !nextChapter.isRecognizedNumber) {
        return false
    }
    val outerHigher = maxOf(prevChapter.chapter_number, nextChapter.chapter_number)
    val outerLower = minOf(prevChapter.chapter_number, nextChapter.chapter_number)
    // prevChapter and nextChapter must themselves be (near-)consecutive for candidateChapter to be
    // considered a stray outlier rather than part of a genuinely large gap.
    if (floor(outerHigher) - floor(outerLower) > 1f) return false
    return candidateChapter.chapter_number < outerLower - 1f || candidateChapter.chapter_number > outerHigher + 1f
}
