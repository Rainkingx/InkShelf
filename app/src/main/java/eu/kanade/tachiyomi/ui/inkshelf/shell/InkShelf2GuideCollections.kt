package eu.kanade.tachiyomi.ui.inkshelf.shell

import java.util.Locale

/**
 * Small manually verified bibliography for collected-edition guide testing.
 *
 * This identifies what the PUBLISHED collection reprints; it does not claim
 * that a particular installed comic extension serves those exact pages.
 * References:
 * https://books.apple.com/gb/book/civil-war/id550155881
 * https://www.comics.org/issue/374780/
 */
internal object InkShelf2GuideCollections {
    internal data class IncludedIssues(
        val series: String,
        val startYear: Int,
        val first: Int,
        val last: Int,
    ) {
        val issueCount: Int get() = last - first + 1
        val label: String get() = series + " #" + first +
            (if (last > first) "–" + last else "")
    }

    internal data class Collection(
        val id: String,
        val event: String,
        val title: String,
        val publishedYear: Int,
        val query: String,
        val included: List<IncludedIssues>,
        val referenceUrl: String,
    ) {
        val issueCount: Int get() = included.sumOf { it.issueCount }
    }

    private val roadToCivilWar = Collection(
        id = "marvel-civil-war-2006-road",
        event = "Civil War",
        title = "Civil War: The Road to Civil War",
        publishedYear = 2007,
        query = "Road to Civil War",
        included = listOf(
            IncludedIssues("Amazing Spider-Man (1999)", 1999, 529, 531),
            IncludedIssues("Fantastic Four", 1997, 536, 537),
            IncludedIssues("New Avengers: Illuminati", 2006, 1, 1),
        ),
        referenceUrl = "https://www.comics.org/issue/374780/",
    )

    /** Only the correct 2006 Civil War event receives this reference. */
    fun forGuide(guide: InkShelf2Guide): List<Collection> {
        val event = InkShelf2GuideFocus.identify(guide) ?: return emptyList()
        return if (event.title == "Civil War" && event.year == 2006 &&
            event.publisher == "marvel"
        ) listOf(roadToCivilWar) else emptyList()
    }

    internal enum class SourceMatch { TITLE_MATCH, POSSIBLE, OTHER }

    private val separator = Regex("""[^\p{L}\p{N}]+""")
    private val editionTerms = Regex(
        """\b(?:19|20)\d{2}\b|\b(?:tpb|trade paperback|hardcover|hc|collected edition)\b""",
    )
    private fun key(title: String): String =
        title.lowercase(Locale.ROOT).replace(separator, " ").trim()

    /** Match the collection TITLE only; no proof of internal contents. */
    fun classifySourceTitle(collection: Collection, title: String): SourceMatch {
        val raw = key(title)
        val base = key(raw.replace(editionTerms, ""))
            .replace(Regex("""\s+"""), " ").trim()
        val canonical = key(collection.title)
        if (base == canonical || base == "the road to civil war" ||
            base == "road to civil war"
        ) return SourceMatch.TITLE_MATCH
        // A source might append a volume or issue number, but that may also
        // identify a DIFFERENT collection. Require human inspection.
        return if (raw.contains("road to civil war")) SourceMatch.POSSIBLE
        else SourceMatch.OTHER
    }

    /**
     * Extra confidence only when a source description explicitly contains
     * ALL three collected issue groups. Never use a title alone as proof.
     */
    fun sourceDescriptionConfirmsContents(collection: Collection, description: String?): Boolean {
        if (collection.id != roadToCivilWar.id || description.isNullOrBlank()) return false
        val text = description.lowercase(Locale.ROOT)
            .replace(Regex("""<[^>]*>"""), " ")
            .replace(Regex("""&(?:amp|nbsp);"""), " ")
        val spiderMan = Regex(
            """amazing\s+spider[\s-]?man.{0,75}#?\s*529\s*[-–—]\s*531""",
        ).containsMatchIn(text)
        val fantasticFour = Regex(
            """fantastic\s+four.{0,75}#?\s*536\s*[-–—]\s*537""",
        ).containsMatchIn(text)
        val illuminati = Regex(
            """(?:new\s+avengers\s*[:\-]?\s*)?illuminati.{0,65}(?:#\s*1\b|one[\s-]?shot|special)""",
        ).containsMatchIn(text)
        return spiderMan && fantasticFour && illuminati
    }
}
