package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.home.InkShelfDailyHeroArt
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverCache
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverCache.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Read-only presentation adapter to the EXISTING J2K database and Discover cache.
 * This is not a new comic database and it does not own the reader or extension engine.
 */
internal class InkShelf2Engine(private val context: Context) {
    private val database: DatabaseHelper = Injekt.get()

    data class Reading(val manga: Manga, val percent: Int?)
    data class Day(
        val calendarDay: Int,
        val title: String,
        val artwork: InkShelfDailyHeroArt.Artwork,
        val picks: List<Manga>,
    )
    data class Home(
        val history: List<Reading>,
        val days: List<Day>,
        val trendingNow: List<Manga>,
    )
    data class PersonalPick(val manga: Manga, val reason: String)
    data class Discover(
        val trending: List<Manga>,
        val newest: List<Manga>,
        val recommended: List<Manga>,
        val recommendationDetails: List<PersonalPick>,
    )

    private val weekdays = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
    )

    suspend fun loadHome(): Home = withContext(Dispatchers.IO) {
        val history = runCatching {
            database.getRecentMangaLimit("", 0, false).executeAsBlocking()
                .asSequence()
                .filter { it.manga.favorite }
                .distinctBy { it.manga.id }
                .take(12)
                .map { item ->
                    val total = item.chapter.last_page_read + item.chapter.pages_left
                    val progress = if (item.chapter.last_page_read > 0 && total > 0) {
                        ((item.chapter.last_page_read + 1f) / total * 100f)
                            .roundToInt().coerceIn(0, 100)
                    } else null
                    Reading(item.manga, progress)
                }.toList()
        }.getOrDefault(emptyList())

        val discover = loadDiscoverDirect()
        val saved = runCatching {
            database.getLibraryMangas().executeAsBlocking().filter { it.favorite }
        }.getOrDefault(emptyList())

        val days = weekdays.map { day ->
            // "What's New Wednesday" means freshly surfaced comics, not only
            // titles whose names happen to contain the current year.
            // Discover's NEW_THIS_WEEK shelf is sourced from current Latest
            // feeds. Its provider does not guarantee an exact publication
            // date, so don't mislabel these as released within seven days.
            val pools = if (day == Calendar.WEDNESDAY) {
                sequenceOf(
                    discover.newest,
                    discover.trending.filter { matchesTheme(it, day) },
                    discover.recommended.filter { matchesTheme(it, day) },
                    saved.filter { matchesTheme(it, day) },
                )
            } else {
                sequenceOf(discover.newest, discover.trending, discover.recommended, saved)
                    .map { pool -> pool.filter { matchesTheme(it, day) } }
            }
            val picks = pools.flatten()
                .filter { !it.thumbnail_url.isNullOrBlank() }
                .distinctBy { it.title.lowercase(Locale.ROOT).trim() }
                .take(3)
                .toList()
            Day(day, dayTitle(day), InkShelfDailyHeroArt.forDay(day), picks)
        }
        // The weekday hero owns its own three themed picks. The Home shelf
        // instead shows the live cached Trending feed, matching Discover.
        val trendingNow = discover.trending
            .filter { !it.thumbnail_url.isNullOrBlank() }
            .distinctBy { "${it.source}:${it.url}" }
        Home(history, days, trendingNow)
    }

    suspend fun loadLibrary(): List<InkShelf2LibraryItem> =
        loadInkShelf2Library(database)

    suspend fun loadDiscover(): Discover = withContext(Dispatchers.IO) { loadDiscoverDirect() }

    private fun loadDiscoverDirect(): Discover {
        fun cached(section: Section): List<Manga> =
            runCatching { InkShelfDiscoverCache.load(context, section, database).map { it.manga } }
                .getOrDefault(emptyList())
                .distinctBy { it.source.toString() + ":" + it.url }
        val personal = runCatching {
            InkShelfDiscoverCache.load(context, Section.PICKED_FOR_YOU, database)
        }.getOrDefault(emptyList())
        val personalPicks = personal.distinctBy { it.manga.source.toString() + ":" + it.manga.url }
            .map { PersonalPick(it.manga, it.reason) }
        return Discover(
            trending = cached(Section.TRENDING),
            newest = cached(Section.NEW_THIS_WEEK),
            recommended = personalPicks.map { it.manga },
            recommendationDetails = personalPicks,
        )
    }

    suspend fun chapters(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        runCatching {
            database.getChapters(manga).executeAsBlocking()
                .sortedByDescending { it.chapter_number }
        }.getOrDefault(emptyList())
    }

    suspend fun nextChapter(manga: Manga): Chapter? = withContext(Dispatchers.IO) {
        runCatching {
            val chapters = database.getChapters(manga).executeAsBlocking()
            chapters.filterNot { it.read }
                .minByOrNull { if (it.chapter_number >= 0f) it.chapter_number else Float.MAX_VALUE }
                ?: chapters.firstOrNull()
        }.getOrNull()
    }

    /** Save through J2K's own database. No parallel store or data migration. */
    suspend fun addToLibrary(manga: Manga): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val item = database.getManga(manga.url, manga.source).executeAsBlocking() ?: manga
            if (!item.favorite) {
                item.favorite = true
                item.date_added = System.currentTimeMillis()
                if (item.id == null) {
                    item.id = database.insertManga(item).executeAsBlocking().insertedId()
                } else {
                    database.updateMangaFavorite(item).executeAsBlocking()
                    database.updateMangaAdded(item).executeAsBlocking()
                }
            }
            manga.favorite = true
            manga.id = item.id
            true
        }.getOrDefault(false)
    }

    private fun dayTitle(day: Int): String = when (day) {
        Calendar.MONDAY -> "MUTANT MONDAY"
        Calendar.TUESDAY -> "INDIE TUESDAY"
        Calendar.WEDNESDAY -> "WHAT'S NEW WEDNESDAY"
        Calendar.THURSDAY -> "THROWBACK THURSDAY"
        Calendar.FRIDAY -> "FANTASTIC FRIDAY"
        Calendar.SATURDAY -> "SPIDER-MAN SATURDAY"
        else -> "SUPER SUNDAY"
    }

    /** Conservative matching: never pad a theme with unrelated titles. */
    private fun matchesTheme(manga: Manga, day: Int): Boolean {
        val title = manga.title.lowercase(Locale.ROOT)
        val tags = listOfNotNull(manga.genre, manga.originalGenre)
            .joinToString(" ").lowercase(Locale.ROOT)
        val all = "$title $tags"
        val keywords = when (day) {
            Calendar.MONDAY -> listOf("x-men", "wolverine", "mutant", "x-force")
            Calendar.WEDNESDAY -> listOf("new release", "new this week", Calendar.getInstance().get(Calendar.YEAR).toString())
            Calendar.THURSDAY -> listOf("classic", "196", "197", "198", "199")
            Calendar.FRIDAY -> listOf("fantastic", "magic", "fantasy", "cosmic")
            Calendar.SATURDAY -> listOf("spider-man", "spiderman", "spider-verse", "venom")
            Calendar.SUNDAY -> listOf("superman", "batman", "wonder woman", "justice league")
            else -> listOf("image comics", "dark horse", "boom", "idw", "indie", "saga", "spawn", "invincible")
        }
        if (day == Calendar.TUESDAY && listOf(
                "marvel comics", "dc comics", "x-men", "batman", "superman",
                "spider-man", "wonder woman", "avengers",
            ).any { it in all }
        ) return false
        return keywords.any { it in all }
    }
}
