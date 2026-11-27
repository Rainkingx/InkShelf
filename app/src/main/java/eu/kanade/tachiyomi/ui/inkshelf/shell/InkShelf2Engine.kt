package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.home.InkShelfDailyHeroArt
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverCache
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverCache.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val sourceManager: SourceManager = Injekt.get()
    private val libraryWriteMutex = Mutex()

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
        val curatedGems: List<Manga> = emptyList(),
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

        val discover = loadDiscoverDirect(enabledSourceIds)
        val saved = runCatching {
            database.getLibraryMangas().executeAsBlocking().filter { it.favorite }
        }.getOrDefault(emptyList())

        val classics = InkShelf2ClassicPicks.load(context, enabledSourceIds)
        val dayStamp = InkShelf2DailyRotation.localDayStamp()
        val usedAcrossDays = mutableSetOf<String>()
        val days = weekdays.map { day ->
            val picks = InkShelf2DailyRotation.select(
                day = day,
                dayStamp = dayStamp,
                newest = discover.newest,
                trending = discover.trending,
                personal = discover.recommended,
                library = saved + InkShelf2CuratedCatalogue.load(context, day, enabledSourceIds) +
                    (if (day == Calendar.THURSDAY) classics else emptyList()),
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

    suspend fun refreshCuratedPicks(enabledSourceIds: Set<Long>): Boolean =
        InkShelf2CuratedCatalogue.refresh(context, sourceManager.getCatalogueSources()
            .filter { it.id in enabledSourceIds && it.id !in InkShelf2ExtensionPause.pausedSourceIds() })

    suspend fun loadLibrary(): List<InkShelf2LibraryItem> =
        loadInkShelf2Library(database)

    suspend fun mangaById(id: Long): Manga? = withContext(Dispatchers.IO) {
        if (id <= 0) null else runCatching { database.getManga(id).executeAsBlocking() }.getOrNull()
    }

    suspend fun recentUpdates(): List<InkShelf2RecentUpdate> = withContext(Dispatchers.IO) {
        runCatching {
            database.getRecentChapters("", 0, false).executeAsBlocking()
                .filter { it.chapter.date_fetch > 0L }
                .distinctBy { it.chapter.id ?: (it.manga.id.toString() + ":" + it.chapter.url) }
                .take(100)
                .map { InkShelf2RecentUpdate(it.manga, it.chapter, it.chapter.date_fetch) }
        }.getOrDefault(emptyList())
    }

    /** Reading History uses the same J2K history query as the classic Recents screen. */
    suspend fun loadHistory(): List<InkShelf2HistoryEntry> = withContext(Dispatchers.IO) {
        runCatching {
            database.getHistoryUngrouped("", 0, false).executeAsBlocking()
                .filter { it.history.last_read > 0L }
                .distinctBy { it.chapter.id ?: it.chapter.url }
                .take(50)
                .map { InkShelf2HistoryEntry(it.manga, it.chapter, it.history.last_read) }
        }.getOrDefault(emptyList())
    }

    suspend fun loadDiscover(enabledSourceIds: Set<Long>? = null): Discover =
        withContext(Dispatchers.IO) { loadDiscoverDirect(enabledSourceIds) }

    private fun loadDiscoverDirect(enabledSourceIds: Set<Long>? = null): Discover {
        val pausedIds = InkShelf2ExtensionPause.pausedSourceIds()
        // Only restore rows backed by currently enabled extensions. Filtering
        // inside the cache loader prevents disabled XOXO entries from being
        // reinserted into J2K while Home/Discover load their saved cards.
        val availableIds = enabledSourceIds?.minus(pausedIds)
        fun cached(section: Section): List<Manga> =
            runCatching {
                InkShelfDiscoverCache.load(context, section, database, availableIds).map { it.manga }
            }.getOrDefault(emptyList())
                .distinctBy { it.source.toString() + ":" + it.url }
        val personal = runCatching {
            InkShelfDiscoverCache.load(context, Section.PICKED_FOR_YOU, database, availableIds)
        }.getOrDefault(emptyList())
        val personalPicks = personal.distinctBy { it.manga.source.toString() + ":" + it.manga.url }
            .map { PersonalPick(it.manga, it.reason) }
        val raw = Discover(
            trending = cached(Section.TRENDING),
            newest = cached(Section.NEW_THIS_WEEK),
            recommended = personalPicks.map { it.manga },
            recommendationDetails = personalPicks,
            curatedGems = InkShelf2CuratedCatalogue.load(context, InkShelf2CuratedCatalogue.GEMS, availableIds ?: sourceManager.getCatalogueSources().map { it.id }.toSet().minus(pausedIds)),
        )
        val sourceNames = sourceManager.getCatalogueSources().associate { it.id to it.name }
        val active = raw.copy(
            trending = raw.trending.filterNot { it.source in pausedIds },
            newest = raw.newest.filterNot { it.source in pausedIds },
            recommended = raw.recommended.filterNot { it.source in pausedIds },
            recommendationDetails = raw.recommendationDetails.filterNot { it.manga.source in pausedIds },
        )
        val saved = runCatching { database.getLibraryMangas().executeAsBlocking() }
            .getOrDefault(emptyList()).filter { it.favorite }
        val balanced = InkShelf2SourceBalance.balanceDiscover(active, sourceNames, saved)
        // An older cache may contain only XOXO recommendations. Until the
        // existing recommendation engine rebuilds them from enabled sources,
        // offer real currently sourced comics instead of a blank shelf.
        // These are visibly labelled as source picks, not falsely personalised.
        if (balanced.recommendationDetails.size >= 3) return balanced
        val chosen = balanced.recommendationDetails.toMutableList()
        val seenEditions = chosen.map {
            InkShelf2ComicIdentity.editionKey(it.manga)
        }.toMutableSet()
        (balanced.trending + balanced.newest).forEach { manga ->
            val edition = InkShelf2ComicIdentity.editionKey(manga)
            if (manga.url.isNotBlank() && manga.source !in pausedIds &&
                (availableIds == null || manga.source in availableIds) &&
                edition.isNotBlank() && seenEditions.add(edition) && chosen.size < 9
            ) {
                chosen += PersonalPick(manga, InkShelf2RecommendationRecovery.FALLBACK_REASON)
            }
        }
        return balanced.copy(
            recommended = chosen.map { it.manga },
            recommendationDetails = chosen,
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

    data class LibrarySaveResult(val saved: Boolean, val existing: Manga? = null)

    /** Save through the J2K database; refuse a second favourite for the same edition. */
    suspend fun addToLibrary(manga: Manga): LibrarySaveResult = withContext(Dispatchers.IO) {
        libraryWriteMutex.withLock {
        runCatching {
            val item = database.getManga(manga.url, manga.source).executeAsBlocking() ?: manga
            if (item.favorite) return@runCatching LibrarySaveResult(true)
            // A matching known edition or substantial shared synopsis can
            // identify a duplicate even when a source omits the year.
            val otherFavourites = database.getLibraryMangas().executeAsBlocking()
                .filter { other ->
                    other.favorite && (other.source != item.source || other.url != item.url)
                }
                .distinctBy { it.id }
            val existing = InkShelf2ComicIdentity.matchingLibraryCopy(item, otherFavourites)
            if (existing != null) return@runCatching LibrarySaveResult(false, existing)
            item.favorite = true
            item.date_added = System.currentTimeMillis()
            if (item.id == null) {
                item.id = database.insertManga(item).executeAsBlocking().insertedId()
            } else {
                database.updateMangaFavorite(item).executeAsBlocking()
                database.updateMangaAdded(item).executeAsBlocking()
            }
            manga.favorite = true
            manga.id = item.id
            LibrarySaveResult(true)
        }.getOrDefault(LibrarySaveResult(false))
        }
    }

    /** Remove Library membership without discarding reading history or files. */
    suspend fun removeFromLibrary(manga: Manga): Boolean = withContext(Dispatchers.IO) {
        libraryWriteMutex.withLock {
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
