package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Calendar
import java.util.Locale

/**
 * Daily picks come exclusively from real installed-source J2K records.
 * Rotation is deterministic per local calendar day, so returning to Home
 * does not reshuffle cards. No extra searches or fabricated comic entries.
 */
internal object InkShelf2DailyRotation {
    data class Pick(val manga: Manga, val reason: String)
    private data class Ranked(val manga: Manga, val reason: String, val score: Int)

    private val yearTag =
        Regex("""[\(\[](?:19|20)\d{2}(?:\s*[-–]\s*(?:19|20)\d{2})?\s*[\)\]]""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(nonWord, " ").trim()

    fun seriesKey(manga: Manga): String = InkShelf2ComicIdentity.normalizedTitle(manga.title).ifBlank { normalize(manga.title.replace(yearTag, " ")) }

    internal fun isInvincible(manga: Manga): Boolean {
        val key = seriesKey(manga)
        return key == "invincible" || key.startsWith("invincible universe") ||
            key.startsWith("invincible presents") || key.startsWith("invincible compendium")
    }

    private fun matches(value: String, terms: List<String>): Boolean {
        val normalized = " ${normalize(value)} "
        return terms.any { " ${normalize(it)} " in normalized }
    }

    private val mutantNames = listOf("x men", "xmen", "wolverine", "x force", "x factor", "new mutants",
        "magneto", "weapon x", "deadpool", "generation x", "x 23", "uncanny avengers", "astonishing x men",
        "legion of x", "knights of x", "way of x", "house of x", "powers of x", "fall of x",
        "rise of the powers of x", "new x men", "x terminators", "x statix", "x corp",
        "phoenix", "rogue and gambit", "rogue gambit", "cable and deadpool")
    private val mutantExact = setOf("excalibur", "cable", "marauders", "gambit", "storm", "rogue", "psylocke",
        "bishop", "nightcrawler", "cyclops", "iceman", "dazzler", "x man", "emma frost", "mystique",
        "hellions", "fallen angels", "exiles", "x of swords", "sabretooth", "magik")
    private val indieNames = listOf("saga", "monstress", "hellboy", "radiant black", "black science",
        "the walking dead", "invincible", "spawn", "descender", "ascender", "east of west", "paper girls",
        "the department of truth", "gideon falls", "lazarus", "the sixth gun", "something is killing the children",
        "eight billion genies", "once and future", "once future", "black hammer", "bone", "chew",
        "fear agent", "seven to eternity", "the wicked and the divine", "ultramega", "little bird",
        "the many deaths of laila starr", "harrow county", "the weatherman", "the manhattan projects",
        "the autumnal", "the good asian", "the new world", "the spire", "kaya", "the sacrificers",
        "do a powerbomb", "the magic order", "the hunger and the dusk", "geiger", "junkyard joe",
        "rogue sun", "local man", "wytches", "kill or be killed", "fatale", "the fade out", "reckless")
    private val indiePublishers = setOf("image comics", "image", "dark horse comics", "dark horse",
        "boom studios", "boom", "idw publishing", "idw", "dynamite entertainment", "dynamite",
        "vault comics", "aftershock comics", "oni press", "scout comics", "awa studios",
        "abstract studio", "self published", "creator owned", "independent comics")
    private val fantasticNames = listOf("fantastic four", "fantastic 4", "silver surfer", "doctor doom",
        "dr doom", "future foundation", "fantastic force", "human torch", "invisible woman",
        "mister fantastic", "mr fantastic", "galactus", "ben grimm", "reed richards",
        "sue storm", "johnny storm", "franklin richards", "valeria richards", "marvel two in one")
    private val spiderNames = listOf("spider man", "spiderman", "spider verse", "miles morales",
        "venom", "carnage", "spider gwen", "spider woman", "spider punk", "spider noir",
        "spider girl", "spider boy", "spider ham", "scarlet spider", "ghost spider",
        "spider uk", "spider gwen ghost spider", "agent venom", "anti venom", "black cat")
    private val superNames = listOf("superman", "supergirl", "superboy", "super sons", "supersons",
        "action comics", "superwoman", "power girl", "man of steel", "man of tomorrow",
        "krypto", "lois lane", "jimmy olsen", "steel")

    private fun key(title: String): String = InkShelf2ComicIdentity.normalizedTitle(title.replace("&", " and "))
    internal fun isSupermanFamily(title: String): Boolean {
        val name = key(title)
        return name == "steel" || matches(name, superNames.filterNot { it == "steel" })
    }

    /** Shared rule for both catalogue ingestion and daily selection. Genre labels
     * never establish a character/franchise. Wednesday requires actual Latest provenance. */
    internal fun themeReason(day: Int, title: String, tags: String = "", fromLatest: Boolean = false): String? {
        val name = key(title)
        if (name.isBlank()) return null
        return when (day) {
            Calendar.MONDAY -> if (matches(name, mutantNames) || name in mutantExact)
                "From the X-Men and mutant universe" else null
            Calendar.TUESDAY -> {
                val publisherTags = tags.split(',', ';', '|').map(::normalize).toSet()
                val known = indieNames.any { name == normalize(it) || name.startsWith(normalize(it) + " ") } ||
                    name in setOf("die", "coda", "criminal", "the deviant")
                if (known || publisherTags.any { it in indiePublishers })
                    "Independent and creator-owned comics" else null
            }
            Calendar.WEDNESDAY -> if (fromLatest) "Recently surfaced from your sources" else null
            Calendar.THURSDAY -> InkShelf2ComicIdentity.year(title)?.takeIf { it in 1900..1999 }
                ?.let { "Originally published in $it" }
            Calendar.FRIDAY -> if (matches(name, fantasticNames) || name in setOf("ff", "the thing", "thing", "doom", "the inhumans", "inhumans", "the heralds of galactus"))
                "From the Fantastic Four universe" else null
            Calendar.SATURDAY -> if (matches(name, spiderNames) || name in setOf("silk", "symbiote spider man", "toxin", "scream curse of carnage"))
                "From the Spider-Man universe" else null
            Calendar.SUNDAY -> if (isSupermanFamily(title)) "From the Superman universe" else null
            else -> null
        }
    }

    internal fun matchesTheme(day: Int, manga: Manga, fromLatest: Boolean = false): Boolean =
        themeReason(day, manga.title, listOfNotNull(manga.genre, manga.originalGenre).joinToString(", "), fromLatest) != null

    private fun relevance(day: Int, manga: Manga, fromLatest: Boolean): Pair<Int, String>? =
        themeReason(day, manga.title, listOfNotNull(manga.genre, manga.originalGenre).joinToString(", "), fromLatest)
            ?.let { 110 to it }

    fun localDayStamp(calendar: Calendar = Calendar.getInstance()): Int =
        calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR)

    fun select(
        day: Int,
        dayStamp: Int,
        newest: List<Manga>,
        trending: List<Manga>,
        personal: List<Manga>,
        library: List<Manga>,
        enabledSourceIds: Set<Long>,
        usedAcrossDays: Set<String>,
        limit: Int = 3,
    ): List<Pick> {
        if (enabledSourceIds.isEmpty() || limit <= 0) return emptyList()
        val candidates = LinkedHashMap<String, Ranked>()

        fun collect(pool: List<Manga>, poolBonus: Int, fromLatest: Boolean) {
            for (comic in pool) {
                if (comic.source !in enabledSourceIds) continue
                if (comic.url.isBlank() || comic.thumbnail_url.isNullOrBlank()) continue
                val identity = seriesKey(comic)
                if (identity.isBlank()) continue
                val (themeScore, reason) = relevance(day, comic, fromLatest) ?: continue
                val score = themeScore + poolBonus
                val current = candidates[identity]
                if (current == null || current.score < score) {
                    // Keep the true source ID and URL for the existing reader.
                    candidates[identity] = Ranked(comic, reason, score)
                }
            }
        }

        if (day == Calendar.WEDNESDAY) {
            // Only the real Latest feed, not an arbitrary year in a title.
            collect(newest, 15, fromLatest = true)
        } else {
            collect(personal, 12, fromLatest = false)
            collect(trending, 8, fromLatest = false)
            collect(newest, 7, fromLatest = false)
            collect(library, 6, fromLatest = false)
        }
        if (candidates.isEmpty()) return emptyList()

        val eligible = candidates.values
            .sortedWith(
                compareByDescending<Ranked> { it.score }
                    .thenBy { seriesKey(it.manga) },
            )
            .take(60)
        // Stable throughout today, rotates tomorrow when enough matches exist.
        val offset = Math.floorMod(dayStamp + day * 37, eligible.size)
        val rotated = eligible.drop(offset) + eligible.take(offset)

        // Avoid showing the same series on several daily heroes if possible.
        val diverse = rotated.filterNot { seriesKey(it.manga) in usedAcrossDays }
        val repeats = rotated.filter { seriesKey(it.manga) in usedAcrossDays }
        // Prefer fresh franchises; a popular series must not crowd out alternatives.
        val preferred = diverse.filterNot { isInvincible(it.manga) }
        val other = diverse.filter { isInvincible(it.manga) }
        val fallback = repeats.filterNot { isInvincible(it.manga) }
        val last = repeats.filter { isInvincible(it.manga) }
        return (preferred + other + fallback + last).take(limit).map { Pick(it.manga, it.reason) }
    }
}
