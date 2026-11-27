package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Locale

/**
 * Curates existing J2K source-backed records without inventing URLs,
 * review ratings, or publisher data. Acclaimed is editorially selected,
 * not a claim that a title has a verified numeric score.
 */
internal object InkShelf2DiscoverCuration {
    data class Shelf(
        val title: String,
        val description: String,
        val comics: List<Manga>,
        val reasons: Map<String, String> = emptyMap(),
    )

    private val yearTag = Regex(
        """\s*[\(\[]((?:19|20)\d{2})(?:\s*[-–—]\s*(?:(?:19|20)\d{2}|ongoing|present)?)?\s*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val punctuation = Regex("""[^\p{L}\p{N}]+""")
    private val acclaimedTitles = listOf(
        "watchmen", "the sandman", "saga", "maus", "the dark knight returns",
        "v for vendetta", "bone", "daytripper", "y the last man",
        "the vision", "hawkeye", "locke key", "fables", "monstress",
        "the walking dead", "invincible", "hellboy", "blankets", "persepolis",
        "all star superman", "mister miracle", "batman year one",
        "the killing joke", "the nice house on the lake",
    )
    private val gemTitles = listOf(
        "east of west", "black science", "descender", "ascender",
        "gideon falls", "seven to eternity", "lazarus", "chew",
        "the sixth gun", "fear agent", "once future", "paper girls",
        "the department of truth", "die", "radiant black",
        "eight billion genies", "something is killing the children",
        "the wicked the divine", "the last god", "little bird", "ultramega",
    )

    private data class Category(
        val title: String,
        val description: String,
        val titleTerms: List<String> = emptyList(),
        val tagTerms: List<String> = emptyList(),
    )
    private val categories = listOf(
        Category(
            "Marvel Universe", "Marvel characters and teams",
            titleTerms = listOf("x men", "wolverine", "spider man", "spiderman",
                "avengers", "iron man", "captain america", "thor", "hulk",
                "deadpool", "fantastic four", "daredevil", "silver surfer",
                "doctor strange", "black panther", "moon knight", "venom",
                "scarlet witch", "punisher", "magneto", "x force",
                "guardians of the galaxy"),
            tagTerms = listOf("marvel comics"),
        ),
        Category(
            "DC Universe", "DC characters and teams",
            titleTerms = listOf("batman", "superman", "wonder woman",
                "justice league", "nightwing", "aquaman", "green lantern",
                "the flash", "supergirl", "batgirl", "harley quinn",
                "teen titans", "constantine", "shazam", "swamp thing", "the sandman"),
            tagTerms = listOf("dc comics"),
        ),
        Category(
            "Indie Spotlight", "Independent and creator-owned stories",
            titleTerms = listOf("saga", "invincible", "spawn", "monstress",
                "hellboy", "descender", "black science", "east of west",
                "radiant black", "paper girls", "the walking dead",
                "gideon falls", "chew", "lazarus", "die",
                "something is killing the children"),
            tagTerms = listOf("image comics", "dark horse", "boom studios",
                "idw", "indie", "independent"),
        ),
        Category(
            "Horror", "Monsters and darker stories",
            titleTerms = listOf("hellboy", "swamp thing", "gideon falls",
                "the walking dead", "something is killing the children"),
            tagTerms = listOf("horror", "supernatural"),
        ),
        Category(
            "Sci-Fi", "Space, futures and science-fiction worlds",
            titleTerms = listOf("saga", "descender", "black science",
                "east of west", "silver surfer", "guardians of the galaxy"),
            tagTerms = listOf("sci fi", "science fiction", "cyberpunk", "space"),
        ),
        Category(
            "Fantasy", "Magic, myth and fantasy",
            titleTerms = listOf("the sandman", "bone", "monstress", "fables",
                "once future", "doctor strange", "the wicked the divine"),
            tagTerms = listOf("fantasy", "magic", "mythology"),
        ),
        Category(
            "Mystery & Crime", "Mysteries, detectives and thrillers",
            titleTerms = listOf("criminal", "the department of truth",
                "gotham central", "sleeper", "fatale", "the fade out", "scalped"),
            tagTerms = listOf("mystery", "detective", "crime", "thriller", "noir"),
        ),
        Category(
            "X-Men Essentials", "Mutant adventures",
            titleTerms = listOf("x men", "wolverine", "magneto",
                "x force", "new mutants", "deadpool"),
        ),
        Category(
            "Cosmic Epics", "Big cosmic adventures",
            titleTerms = listOf("silver surfer", "galactus", "nova",
                "thanos", "guardians of the galaxy", "saga", "annihilation"),
            tagTerms = listOf("cosmic", "space opera"),
        ),
    )

    fun identity(comic: Manga): String = "${comic.source}:${comic.url}"
    private fun normalized(text: String): String =
        text.lowercase(Locale.ROOT).replace(punctuation, " ").trim()
    private fun titleKey(comic: Manga): String =
        normalized(comic.title.replace(yearTag, " "))
    private fun editionKey(comic: Manga): String = normalized(comic.title)
    private fun seriesYear(comic: Manga): Int =
        yearTag.find(comic.title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
    private fun matchesPhrase(text: String, term: String): Boolean =
        " ${normalized(term)} " in " ${normalized(text)} "
    private fun matchCategory(comic: Manga, category: Category): Boolean {
        val title = titleKey(comic)
        val tags = listOfNotNull(comic.genre, comic.originalGenre).joinToString(" ")
        return category.titleTerms.any { matchesPhrase(title, it) } ||
            category.tagTerms.any { matchesPhrase(tags, it) }
    }

    fun build(
        discover: InkShelf2Engine.Discover?,
        library: List<InkShelf2LibraryItem>,
        enabledSourceIds: Set<Long>,
    ): List<Shelf> {
        if (enabledSourceIds.isEmpty()) return emptyList()
        val trending = discover?.trending.orEmpty()
        val newest = discover?.newest.orEmpty()
        val personal = discover?.recommendationDetails.orEmpty().map { it.manga }
        val saved = library.map { it.manga }
        // Existing cache and library only. Dedupe same edition across sources.
        val pool = (personal + trending + newest + saved + discover?.curatedGems.orEmpty()).asSequence()
            .filter { it.source in enabledSourceIds && it.url.isNotBlank() }
            .filter { !it.thumbnail_url.isNullOrBlank() && it.title.isNotBlank() }
            // Different publication years are distinct editions and must
            // remain individually accessible on Browse All.
            .distinctBy(::editionKey)
            .toList()
        val acclaimed = acclaimedTitles.mapNotNull { title ->
            // Prefer the original/earlier publication run when several
            // editions share an otherwise identical series name.
            pool.filter { titleKey(it) == normalized(title) }.minByOrNull(::seriesYear)
        }.distinctBy(::titleKey)
        val acclaimedKeys = acclaimed.map(::titleKey).toSet()
        val trendingKeys = trending.map(::titleKey).toSet()
        val indie = categories.first { it.title == "Indie Spotlight" }
        val savedKeys = saved.map(::titleKey).toSet()
        val gemPool = pool.filter {
            titleKey(it) !in acclaimedKeys && titleKey(it) !in savedKeys &&
                (InkShelf2CuratedCatalogue.gemTitle(it.title) ||
                    gemTitles.any { title -> titleKey(it) == normalized(title) } ||
                    matchCategory(it, indie))
        }.distinctBy(::titleKey)
        // Do not discard all the titles already present in the other shelves.
        // Prefer lesser-seen titles, but keep a useful shelf on small catalogues.
        val fresh = gemPool.filterNot { titleKey(it) in trendingKeys }
        val eligible = (if (fresh.size >= 12) fresh else gemPool).sortedBy(::titleKey)
        val offset = if (eligible.isEmpty()) 0 else Math.floorMod(InkShelf2DailyRotation.localDayStamp(), eligible.size)
        val gems = (eligible.drop(offset) + eligible.take(offset)).take(48)
        val featured = listOf(
            Shelf(
                "Critically Acclaimed",
                "Celebrated series · editor's selection, not review scores",
                acclaimed,
                acclaimed.associate { identity(it) to "Editorial selection" },
            ),
            Shelf(
                "Hidden Gems",
                "Independent and overlooked comics · Rotates daily",
                gems,
                gems.associate { identity(it) to "Picked for deeper discovery" },
            ),
        ).filter { it.comics.isNotEmpty() }
        val collections = categories.mapNotNull { category ->
            val comics = pool.filter { matchCategory(it, category) }.take(60)
            if (comics.isEmpty()) null
            else Shelf(category.title, category.description, comics)
        }
        return featured + collections +
            listOf(Shelf("Browse All", "Cached Discover and Library comics", pool))
                .filter { it.comics.isNotEmpty() }
    }
}
