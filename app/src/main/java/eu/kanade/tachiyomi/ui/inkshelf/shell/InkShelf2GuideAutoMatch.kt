package eu.kanade.tachiyomi.ui.inkshelf.shell

import java.util.Locale

/**
 * Source title and edition matching for event issues. The guide records
 * publication year separately: its UI title needn't use the extension's
 * exact spelling, 'Vol. 3' suffix or '&' versus 'and'.
 *
 * Never accept a different, known publication year (e.g. House of M 2015
 * instead of the required 2005 series).
 */
internal object InkShelf2GuideAutoMatch {
    private val trailingYear = Regex("""\s*\((?:19|20)\d{2}(?:[-–—](?:(?:19|20)\d{2})?)?\)\s*$""")
    private val volume = Regex("""\s+vol(?:ume)?\.?\s*\d+\b""", RegexOption.IGNORE_CASE)
    private val punctuation = Regex("""[^\p{L}\p{N}]+""")
    private val publicationYear = Regex("""\(((?:19|20)\d{2})(?:[-–—].*?)?\)\s*$""")

    fun seriesName(title: String): String = title.trim()
        .replace(trailingYear, "")
        .replace(volume, "")
        .trim()

    fun key(title: String): String = seriesName(title)
        .lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(punctuation, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .removePrefix("the ")

    fun year(title: String): Int? =
        publicationYear.find(title.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()

    /** Zero rejects; larger values are stronger evidence for this edition. */
    fun rank(issue: InkShelf2GuideIssue, candidateTitle: String): Int {
        if (key(issue.series) != key(candidateTitle)) return 0
        val expectedYear = issue.yearBegan ?: year(issue.series)
        val actualYear = year(candidateTitle)
        if (expectedYear != null && actualYear != null && expectedYear != actualYear) return 0
        return when {
            expectedYear != null && actualYear == expectedYear -> 3
            expectedYear == null && actualYear == null -> 2
            else -> 1
        }
    }

    fun queries(issue: InkShelf2GuideIssue): List<String> {
        val base = seriesName(issue.series)
        val year = issue.yearBegan ?: year(issue.series)
        return listOfNotNull(
            year?.let { base + " (" + it + ")" },
            issue.series,
            base,
        ).map { it.trim() }.filter { it.length >= 2 }.distinct().take(3)
    }
}
