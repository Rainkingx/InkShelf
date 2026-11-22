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
    private val classicYear = Regex("""(?<!\d)(?:19[3-9]\d|200[0-9])(?!\d)""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(nonWord, " ").trim()

    fun seriesKey(manga: Manga): String = normalize(manga.title.replace(yearTag, " "))

    private fun matches(value: String, terms: List<String>): Boolean {
        val normalized = " ${normalize(value)} "
        return terms.any { " ${normalize(it)} " in normalized }
    }

    private fun relevance(day: Int, manga: Manga, fromLatest: Boolean): Pair<Int, String>? {
        val title = manga.title
        val tags = listOfNotNull(manga.genre, manga.originalGenre).joinToString(" ")
        if (day == Calendar.WEDNESDAY) {
            // The Latest source feed does not guarantee actual release dates.
            return if (fromLatest) 110 to "Recently surfaced from your sources" else null
        }
        val terms = when (day) {
            Calendar.MONDAY ->
                listOf("x-men", "x men", "wolverine", "x-force", "x force",
                    "mutant", "magneto", "new mutants", "weapon x", "deadpool")
            Calendar.TUESDAY ->
                listOf("image comics", "dark horse", "boom studios", "idw",
                    "indie", "independent", "saga", "spawn", "invincible",
                    "radiant black", "monstress", "hellboy", "walking dead",
                    "paper girls", "black science", "east of west",
                    "something is killing the children", "once & future")
            Calendar.THURSDAY ->
                listOf("classic", "golden age", "silver age", "bronze age",
                    "retro", "archive", "archives", "vintage", "reprint")
            Calendar.FRIDAY ->
                listOf("fantastic four", "fantastic", "magic", "fantasy",
                    "cosmic", "silver surfer", "doctor strange", "scarlet witch",
                    "galactus", "sorcerer", "mythology", "supernatural")
            Calendar.SATURDAY ->
                listOf("spider-man", "spiderman", "spider verse", "spider-verse",
                    "miles morales", "venom", "carnage", "silk", "spider gwen",
                    "spider-woman", "symbiote")
            else ->
                listOf("superman", "batman", "wonder woman", "justice league",
                    "supergirl", "nightwing", "aquaman", "green lantern",
                    "flash", "shazam", "avengers", "captain america",
                    "iron man", "superhero", "superheroes", "heroic")
        }
        val titleMatch = matches(title, terms)
        val tagMatch = matches(tags, terms)
        val classicTitle = day == Calendar.THURSDAY && classicYear.containsMatchIn(title)
        if (!titleMatch && !tagMatch && !classicTitle) return null

        val reason = when (day) {
            Calendar.MONDAY -> "A match for Mutant Monday"
            Calendar.TUESDAY -> "An independent comics pick"
            Calendar.THURSDAY -> "A classic or throwback series"
            Calendar.FRIDAY -> "Fantasy, magic or cosmic adventures"
            Calendar.SATURDAY -> "From the Spider-Man universe"
            else -> "Superheroes for Sunday"
        }
        return (if (titleMatch || classicTitle) 95 else 65) to reason
    }

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
            .take(18)
        // Stable throughout today, rotates tomorrow when enough matches exist.
        val offset = Math.floorMod(dayStamp + day * 37, eligible.size)
        val rotated = eligible.drop(offset) + eligible.take(offset)

        // Avoid showing the same series on several daily heroes if possible.
        val diverse = rotated.filterNot { seriesKey(it.manga) in usedAcrossDays }
        val repeats = rotated.filter { seriesKey(it.manga) in usedAcrossDays }
        return (diverse + repeats).take(limit).map { Pick(it.manga, it.reason) }
    }
}
