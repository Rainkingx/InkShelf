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
        if (issue.number.equals("Full", ignoreCase = true)) {
            val special = issue.issueLabel
                ?.takeUnless { it.equals("One-shot", true) }
                ?.trim()?.takeIf { it.isNotBlank() }
            if (special != null) {
                val matches = chapters.filter {
                    InkShelf2GuideAutoMatch.matchesSpecialLabel(it.name, special)
                }
                if (matches.size == 1) {
                    return Found(matches.single(), grouped = false)
                }
                // Never open an arbitrary issue when a named special is missing.
                return null
            }
            // A genuine standalone comic may label its only chapter "Full",
            // "One-shot" or simply use the title, not issue #1.
            if (chapters.size == 1) return Found(chapters.single(), grouped = false)
            val standalone = chapters.filterNot { nonRegular.containsMatchIn(it.name) }
            return if (standalone.size == 1) Found(standalone.single(), grouped = false) else null
        }
        val number = issue.number.trim().removePrefix("#").toIntOrNull()
            ?.takeIf { it in 0..999 } ?: return null

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
