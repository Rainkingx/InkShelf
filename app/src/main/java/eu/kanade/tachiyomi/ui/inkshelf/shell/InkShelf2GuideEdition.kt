package eu.kanade.tachiyomi.ui.inkshelf.shell

/**
 * Disambiguates source editions without guessing which comic run is correct.
 * Metron's year_began is a publication start year; volume is NOT a year.
 * This helper is pure so edition-safety rules can be unit-tested.
 */
internal object InkShelf2GuideEdition {
    enum class Match { MATCHING, UNVERIFIED, OTHER_RUN, OTHER_TITLE }

    private val yearSuffix =
        Regex("""\s*\(((?:19|20)\d{2})(?:\s*[-–—]\s*(?:\d{4})?)?\)\s*$""")
    private val anyYearSuffix =
        Regex("""\s*\((?:19|20)\d{2}(?:\s*[-–—]\s*(?:\d{4})?)?\)\s*$""")
    private val volumeSuffix = Regex("""\s*\((?:vol(?:ume)?\.?\s*\d+)\)\s*$""", RegexOption.IGNORE_CASE)
    private val punctuation = Regex("""[^\p{L}\p{N}]+""")

    fun titleKey(title: String): String {
        val withoutYear = title.trim().replace(anyYearSuffix, "")
            .replace(volumeSuffix, "")
        return withoutYear.lowercase(java.util.Locale.ROOT)
            .replace(punctuation, " ").trim()
    }

    fun year(title: String): Int? =
        yearSuffix.find(title.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun classify(issue: InkShelf2GuideIssue, candidateTitle: String): Match {
        if (titleKey(issue.series) != titleKey(candidateTitle)) return Match.OTHER_TITLE
        val expected = issue.yearBegan ?: year(issue.series)
        val found = year(candidateTitle)
        if (expected == null || found == null) return Match.UNVERIFIED
        if (expected == found) return Match.MATCHING
        // A first issue can be cover-dated one year after its publication.
        // A one-year mismatch needs human verification, not silent rejection
        // or automatic reader launch.
        return if (kotlin.math.abs(expected - found) == 1) Match.UNVERIFIED else Match.OTHER_RUN
    }

    fun sourceSearchQuery(issue: InkShelf2GuideIssue): String =
        issue.series.trim().replace(anyYearSuffix, "")
}
