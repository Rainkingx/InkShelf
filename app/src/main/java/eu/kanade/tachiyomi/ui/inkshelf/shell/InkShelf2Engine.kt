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
        val pickReasons: Map<String, String> = emptyMap(),
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

    suspend fun loadHome(enabledSourceIds: Set<Long>): Home = withContext(Dispatchers.IO) {
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

        val dayStamp = InkShelf2DailyRotation.localDayStamp()
        val usedAcrossDays = mutableSetOf<String>()
        val days = weekdays.map { day ->
            val picks = InkShelf2DailyRotation.select(
                day = day,
                dayStamp = dayStamp,
                newest = discover.newest,
                trending = discover.trending,
                personal = discover.recommended,
                library = saved,
                enabledSourceIds = enabledSourceIds,
                usedAcrossDays = usedAcrossDays,
            )
            usedAcrossDays += picks.map { InkShelf2DailyRotation.seriesKey(it.manga) }
            Day(
                calendarDay = day,
                title = dayTitle(day),
                artwork = InkShelfDailyHeroArt.forDay(day),
                picks = picks.map { it.manga },
                pickReasons = picks.associate {
                    "${it.manga.source}:${it.manga.url}" to it.reason
                },
            )
        }
        // The weekday hero owns its own three themed picks. The Home shelf
        // instead shows the live cached Trending feed, matching Discover.
        val trendingNow = discover.trending
            .filter { it.source in enabledSourceIds && !it.thumbnail_url.isNullOrBlank() }
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

    /** Remove Library membership without discarding reading history or files. */
    suspend fun removeFromLibrary(manga: Manga): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val entry = database.getManga(manga.url, manga.source).executeAsBlocking()
                ?: return@runCatching false
            if (!entry.favorite) return@runCatching false
            entry.favorite = false
            database.updateMangaFavorite(entry).executeAsBlocking()
            manga.favorite = false
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

}
