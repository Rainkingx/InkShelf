package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Chapter
import java.util.Locale

/**
 * Distinguishes an extension's real issue number from J2K's internal chapter
 * order/number. Sources frequently offset their chapter_number field when
 * specials, annuals or extras appear in the same feed.
 */
internal object InkShelf2IssueNumbers {
    private val explicitlyNumbered = Regex(
        """(?i)(?:^|\b)(?:issue|chapter|ch\.?|#)\s*#?\s*(\d{1,3})(?!\d)""",
    )
    private val leadingNumber = Regex("""^\s*#?(\d{1,3})(?!\d)(?:\b|\s|$)""")
    private val specialWords = listOf(
        "annual", "special", "one-shot", "one shot",
        "free comic book day", "preview", "prologue",
    )

    fun fromChapter(chapter: Chapter): Int? {
        val name = chapter.name.trim()
        val lower = name.lowercase(Locale.ROOT)
        if (specialWords.any { it in lower }) return null
        val fromName = explicitlyNumbered.find(name)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?: leadingNumber.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromName != null) return fromName.takeIf { it in 1..999 }
        val n = chapter.chapter_number
        return if (n >= 1f && n <= 999f && n % 1f == 0f) n.toInt() else null
    }

    fun anchor(chapters: List<Chapter>): Int? =
        chapters.asSequence().mapNotNull(::fromChapter).maxOrNull()
}
