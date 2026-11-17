package eu.kanade.tachiyomi.ui.manga

import eu.kanade.tachiyomi.data.database.models.Chapter

/**
 * Western comics have annuals, specials and publication years that J2K's generic
 * manga chapter-number parser can mistake for numbered issues (e.g. Annual 2026).
 * Compare only confidently identified regular issues, not arbitrary title numbers.
 */
internal object InkShelfComicIssueGaps {
    private val extraWords =
        Regex(
            """\b(annual|special|bonus|preview|prologue|epilogue|one[ -]?shot|fcbd|free comic book day|variant)\b""",
            RegexOption.IGNORE_CASE,
        )
    private val explicitIssue =
        Regex(
            """(?:^|[\s:(])(?:issue|chapter|ch\.?|#)\s*#?\s*(\d{1,3})(?!\d)""",
            RegexOption.IGNORE_CASE,
        )
    private val plainIssue = Regex("""^\s*#?(\d{1,3})(?:\.\d+)?\s*$""")

    /** Null means the entry's number is ambiguous and should not create a gap. */
    fun issueNumber(chapter: Chapter): Int? {
        val name = chapter.name
        if (extraWords.containsMatchIn(name)) return null

        val fromName = explicitIssue.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: plainIssue.matchEntire(name)?.groupValues?.getOrNull(1)?.toIntOrNull()

        // A parsed "2026" is usually a publication year, not 2,026 missing issues.
        val number = fromName ?: chapter.chapter_number
            .takeIf { it >= 1f && it < 1000f && it % 1f == 0f }
            ?.toInt()
        return number?.takeIf { it in 1..999 }
    }

    fun gap(lower: Chapter, higher: Chapter): Int {
        val lowerNumber = issueNumber(lower) ?: return 0
        val higherNumber = issueNumber(higher) ?: return 0
        val missing = higherNumber - lowerNumber - 1
        // A huge jump is more likely a new numbering run than genuinely missing issues.
        return missing.takeIf { it in 1..100 } ?: 0
    }

    fun countMissing(chapters: List<Chapter>): Int {
        val numbered = chapters.mapNotNull(::issueNumber).distinct().sorted()
        return numbered.zipWithNext().sumOf { (a, b) ->
            (b - a - 1).takeIf { it in 1..100 } ?: 0
        }
    }
}
