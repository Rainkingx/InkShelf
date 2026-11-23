package eu.kanade.tachiyomi.ui.inkshelf.shell

import java.util.Locale

/**
 * A conservative, manually checked bibliography of collected story editions.
 *
 * These are contents of PUBLISHED books, not a claim that an extension has
 * the same book. Even a perfect source title is not verified until its
 * issue list or description explicitly confirms the contents.
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
        val eventYear: Int = 0,
        val scope: String = "Main event",
    ) {
        val issueCount: Int get() = included.sumOf { it.issueCount }
    }

    // Sources: Grand Comics Database, https://www.comics.org/issue/374780/
    private val roadToCivilWar = Collection(
        id = "marvel-civil-war-2006-road",
        event = "Civil War",
        title = "Civil War: The Road to Civil War",
        publishedYear = 2007,
        query = "Road to Civil War",
        included = listOf(
            IncludedIssues("Amazing Spider-Man (1999)", 1999, 529, 531),
            IncludedIssues("Fantastic Four (1998)", 1998, 536, 537),
            IncludedIssues("New Avengers: Illuminati", 2006, 1, 1),
        ),
        referenceUrl = "https://www.comics.org/issue/374780/",
        eventYear = 2006,
        scope = "Prelude",
    )

    // Marvel Premiere Collection, CIVIL WAR #1-7, 2025 edition.
    private val civilWarMain = Collection(
        id = "marvel-civil-war-2006-main",
        event = "Civil War",
        title = "Civil War",
        publishedYear = 2025,
        query = "Civil War",
        included = listOf(IncludedIssues("Civil War", 2006, 1, 7)),
        referenceUrl = "https://www.midtowncomics.com/product/2423607",
        eventYear = 2006,
    )

    // Penguin Random House / Marvel: Secret Wars by Jonathan Hickman, 2016.
    private val secretWars = Collection(
        id = "marvel-secret-wars-2015",
        event = "Secret Wars",
        title = "Secret Wars by Jonathan Hickman",
        publishedYear = 2016,
        query = "Secret Wars",
        included = listOf(IncludedIssues("Secret Wars (2015)", 2015, 1, 9)),
        referenceUrl = "https://www.penguinrandomhouse.com/books/693167/secret-wars-by-jonathan-hickman/9780785198956/",
        eventYear = 2015,
    )

    // Penguin Random House / Marvel: House of M by Brian Michael Bendis.
    private val houseOfM = Collection(
        id = "marvel-house-of-m-2005",
        event = "House of M",
        title = "House of M",
        publishedYear = 2006,
        query = "House of M",
        included = listOf(IncludedIssues("House of M (2005)", 2005, 1, 8)),
        referenceUrl = "https://www.penguinrandomhouse.com/books/709527/house-of-m-by-brian-michael-bendis/",
        eventYear = 2005,
    )

    // DC graphic novel, February 2025. A Ground Zero one-shot is included;
    // some editions also contain promotional extras.
    private val absolutePower = Collection(
        id = "dc-absolute-power-2024",
        event = "Absolute Power",
        title = "Absolute Power",
        publishedYear = 2025,
        query = "Absolute Power",
        included = listOf(
            IncludedIssues("Absolute Power (2024)", 2024, 1, 4),
            IncludedIssues("Absolute Power: Ground Zero", 2024, 1, 1),
        ),
        referenceUrl = "https://www.dc.com/graphic-novels/absolute-power-2024/absolute-power",
        eventYear = 2024,
    )

    // DC Flashpoint 2024 Edition: the original five-issue miniseries.
    private val flashpoint = Collection(
        id = "dc-flashpoint-2011",
        event = "Flashpoint",
        title = "Flashpoint (2024 Edition)",
        publishedYear = 2024,
        query = "Flashpoint",
        included = listOf(IncludedIssues("Flashpoint (2011)", 2011, 1, 5)),
        referenceUrl = "https://books.apple.com/gb/book/flashpoint-2024-edition/id6648758708",
        eventYear = 2011,
    )

    // DC's Blackest Night hardcover collects issues #0-8.
    private val blackestNight = Collection(
        id = "dc-blackest-night-2009",
        event = "Blackest Night",
        title = "Blackest Night",
        publishedYear = 2010,
        query = "Blackest Night",
        included = listOf(IncludedIssues("Blackest Night (2009)", 2009, 0, 8)),
        referenceUrl = "https://www.dc.com/graphic-novels/blackest-night-2009/blackest-night",
        eventYear = 2009,
    )

    // DC's Crisis on Infinite Earths HC, the original twelve-issue event.
    private val crisis = Collection(
        id = "dc-crisis-on-infinite-earths-1985",
        event = "Crisis on Infinite Earths",
        title = "Crisis on Infinite Earths",
        publishedYear = 2003,
        query = "Crisis on Infinite Earths",
        included = listOf(IncludedIssues("Crisis on Infinite Earths (1985)", 1985, 1, 12)),
        referenceUrl = "https://www.dc.com/graphic-novels/crisis-on-infinite-earths-1985/crisis-on-infinite-earths",
        eventYear = 1985,
    )

    // DC Infinite Crisis hardcover, seven original miniseries issues.
    private val infiniteCrisis = Collection(
        id = "dc-infinite-crisis-2005",
        event = "Infinite Crisis",
        title = "Infinite Crisis",
        publishedYear = 2006,
        query = "Infinite Crisis",
        included = listOf(IncludedIssues("Infinite Crisis (2005)", 2005, 1, 7)),
        referenceUrl = "https://www.dc.com/graphic-novels/infinite-crisis-2005/infinite-crisis",
        eventYear = 2005,
    )

    private val catalogue = listOf(
        roadToCivilWar, civilWarMain, secretWars, houseOfM,
        absolutePower, flashpoint, blackestNight, crisis, infiniteCrisis,
    )

    /** Never attach editions to a similarly named event of another year. */
    fun forGuide(guide: InkShelf2Guide): List<Collection> {
        val event = InkShelf2GuideFocus.identify(guide) ?: return emptyList()
        return catalogue.filter { it.event == event.title && it.eventYear == event.year &&
            event.publisher.equals(if (it.id.startsWith("marvel")) "marvel" else "dc", true)
        }
    }

    internal enum class SourceMatch { TITLE_MATCH, POSSIBLE, OTHER }

    private val separator = Regex("""[^\p{L}\p{N}]+""")
    private val foundYear = Regex("""\b(?:19|20)\d{2}\b""")
    private val editionWords = Regex(
        """\b(?:tpb|trade|paperback|hardcover|hc|omnibus|graphic|novel|collected|collection|edition|vol|volume)\b""",
    )
    private fun key(title: String): String =
        title.lowercase(Locale.ROOT).replace(separator, " ").trim()

    /**
     * A source listing is NOT guaranteed to represent a collected edition.
     * Be strict about other-year runs; classify uncertain versions as POSSIBLE.
     */
    fun classifySourceTitle(collection: Collection, title: String): SourceMatch {
        val raw = key(title)
        val years = foundYear.findAll(raw).mapNotNull { it.value.toIntOrNull() }.toSet()
        if (years.any { it != collection.eventYear && it != collection.publishedYear }) {
            return SourceMatch.OTHER
        }
        val withoutYear = raw.replace(foundYear, " ")
            .replace(Regex("""\s+"""), " ").trim()
        val base = withoutYear.replace(editionWords, " ")
            .replace(Regex("""\b(?:by|marvel|dc)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        val rawBase = key(collection.title).replace(editionWords, " ")
            .replace(Regex("""\b(?:by|marvel|dc)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        val alternate = when (collection.id) {
            roadToCivilWar.id -> listOf("road to civil war", "the road to civil war")
            secretWars.id -> listOf("secret wars")
            flashpoint.id -> listOf("flashpoint")
            else -> emptyList()
        }
        val candidateTitles = (listOf(rawBase) + alternate).filter { it.isNotBlank() }
        val exact = candidateTitles.any { base == it }
        val partial = candidateTitles.any { phrase ->
            // Don't surface unrelated tie-ins such as "House of M: Iron
            // Man" or "Civil War: The Road to Civil War" as the MAIN TPB.
            // Only longer titles with actual collection-like suffixes.
            withoutYear.startsWith("$phrase ") &&
                Regex("""\b(?:tpb|trade|paperback|hardcover|hc|omnibus|graphic|novel|collected|complete|collection|edition|vol|volume)\b""")
                    .containsMatchIn(withoutYear.removePrefix(phrase).trim())
        }
        if (!exact && !partial) return SourceMatch.OTHER
        // A distinctive full book title may be a confident title match, but
        // a bare event/series name alone must NOT be mistaken for a TPB.
        val explicitCollected = editionWords.containsMatchIn(raw)
        val distinctive = collection.id == roadToCivilWar.id && exact
        return if (exact && (explicitCollected || distinctive)) SourceMatch.TITLE_MATCH
        else SourceMatch.POSSIBLE
    }

    /**
     * Confirm only when the source summary explicitly spells out every core
     * issue group. Missing details are "unknown", never a negative finding.
     * The standalone published manifest remains distinct from source claims.
     */
    fun sourceDescriptionConfirmsContents(collection: Collection, description: String?): Boolean {
        if (description.isNullOrBlank()) return false
        val text = description.lowercase(Locale.ROOT)
            .replace(Regex("""<[^>]*>"""), " ")
            .replace(Regex("""&(?:amp|nbsp);"""), " ")
        if (collection.id == roadToCivilWar.id) {
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
        return collection.included.all { group ->
            val name = group.series.replace(Regex("""\s*\(\d{4}\)"""), "")
                .lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]+"""), """[\s:–-]+""")
            val numbers = Regex.escape(group.first.toString()) +
                if (group.last != group.first) """\s*[-–—]\s*#?\s*""" +
                    Regex.escape(group.last.toString()) else ""
            Regex(name + """.{0,85}#?\s*""" + numbers + """\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
    }
}
