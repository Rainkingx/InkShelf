package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.manga.InkShelfComicIssueGaps

/**
 * Find the requested crossover issue within an installed extension's actual
 * chapter list. Sources can publish issues separately or as grouped entries.
 * Never guess when numbering is absent or several entries claim the same issue.
 */
internal object InkShelf2GuideChapterFinder {
    internal data class Found(val chapter: Chapter, val grouped: Boolean)

    private val numberedRange = Regex(
        """(?:^|[\s:(])#\s*0*(\d{1,3})\s*[-–—]\s*#?\s*0*(\d{1,3})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    private val nonRegular = Regex(
        """\b(annual|special|bonus|preview|one[ -]?shot|fcbd|variant)\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun inCombinedEntry(chapter: Chapter, number: Int): Boolean {
        if (nonRegular.containsMatchIn(chapter.name)) return false
        val match = numberedRange.find(chapter.name) ?: return false
        val first = match.groupValues[1].toIntOrNull() ?: return false
        val last = match.groupValues[2].toIntOrNull() ?: return false
        return first in 1..999 && last in 1..999 &&
            last > first && last - first <= 75 &&
            number in first..last
    }

    fun find(issue: InkShelf2GuideIssue, chapters: List<Chapter>): Found? {
        val number = issue.number.trim().removePrefix("#").toIntOrNull()
            ?.takeIf { it in 1..999 } ?: return null

        val exact = chapters.filter { chapter ->
            !nonRegular.containsMatchIn(chapter.name) &&
                InkShelf2IssueNumbers.fromChapter(chapter) == number &&
                !inCombinedEntry(chapter, number)
        }
        if (exact.size == 1) return Found(exact.single(), grouped = false)
        if (exact.size > 1) return null

        val bundled = chapters.filter { chapter ->
            inCombinedEntry(chapter, number) ||
                InkShelfComicIssueGaps.coveredNumbers(chapter).let {
                    it.size > 1 && number in it
                }
        }
        return if (bundled.size == 1) Found(bundled.single(), grouped = true) else null
    }
}
