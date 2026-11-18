package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * InkShelf 2.0 Compose adapter to J2K extension catalogue sources.
 *
 * Keeps the original source interface, installed extensions, chapter sync,
 * reading progress, database and reader. No alternate comic backend.
 */
internal class InkShelf2SourcesEngine {
    private val database: DatabaseHelper = Injekt.get()
    private val sources: SourceManager = Injekt.get()
    // Automatic details loading and Read Now can race. Serialize the first
    // fetch so only one source request / DB chapter sync occurs per attempt.
    private val issueLoadMutex = Mutex()

    data class Page(val comics: List<Manga>, val hasMore: Boolean)

    suspend fun page(source: CatalogueSource, page: Int, query: String, latest: Boolean = false): Page =
        withContext(Dispatchers.IO) {
            val result = when {
                query.isNotBlank() -> source.getSearchManga(page, query.trim(), source.getFilterList())
                latest && source.supportsLatest -> source.getLatestUpdates(page)
                else -> source.getPopularManga(page)
            }
            val comics = result.mangas.map { remote ->
                // A catalogue result can be viewed without automatically adding
                // every search result to the user's database.
                database.getManga(remote.url, source.id).executeAsBlocking()
                    ?: Manga.create(remote.url, remote.title, source.id).apply {
                        copyFrom(remote)
                    }
            }.distinctBy { it.url }
            Page(comics, result.hasNextPage)
        }

    /** First-page results from the enabled J2K extension catalogues. */
    data class SourceResult(
        val manga: Manga,
        val sourceName: String,
    )

    data class GlobalSearchResults(
        val results: List<SourceResult>,
        val searchedSources: Int,
        val failedSources: Int,
    )

    /**
     * Search genuine installed catalogue extensions, not the recommendation
     * cache. Search operations are individually time-bounded and supervised:
     * one broken extension cannot prevent other sources returning results.
     * Never writes search results to the library or database.
     */
    suspend fun searchAcross(
        catalogues: List<CatalogueSource>,
        query: String,
    ): GlobalSearchResults = supervisorScope {
        if (query.isBlank() || catalogues.isEmpty()) {
            return@supervisorScope GlobalSearchResults(emptyList(), 0, 0)
        }
        val limiter = Semaphore(4)
        val responses = catalogues.distinctBy { it.id }.map { source ->
            async {
                limiter.withPermit {
                    try {
                        val found = withTimeout(12_000L) {
                            page(source, 1, query).comics
                        }
                        found.take(35).map { SourceResult(it, source.name) } to false
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        emptyList<SourceResult>() to true
                    }
                }
            }
        }.awaitAll()
        GlobalSearchResults(
            results = responses.flatMap { it.first }
                .distinctBy { "${it.manga.source}:${it.manga.url}" },
            searchedSources = responses.size,
            failedSources = responses.count { it.second },
        )
    }

    /**
     * On-demand first read for catalogue and recommendation comics. Those
     * cards commonly have artwork + metadata but no cached issue list yet.
     * Resolve the original J2K manga row, fetch issues from its installed
     * extension when needed, and preserve all existing read/download flags.
     */
    suspend fun ensureIssues(comic: Manga): Pair<Manga, List<Chapter>> =
        withContext(Dispatchers.IO) {
            issueLoadMutex.withLock {
                val saved = database.getManga(comic.url, comic.source).executeAsBlocking()
                val local = saved ?: comic.also {
                    it.id = database.insertManga(it).executeAsBlocking().insertedId()
                }
                val cached = database.getChapters(local).executeAsBlocking()
                if (cached.isNotEmpty()) return@withLock local to cached

                val source = sources.get(local.source)
                    ?: throw IllegalStateException("Comic source is not installed.")
                val remote = withTimeout(30_000L) { source.getChapterList(local) }
                if (remote.isNotEmpty()) {
                    syncChaptersWithSource(database, remote, local, source)
                }
                local to database.getChapters(local).executeAsBlocking()
            }
        }

    /** Select one comic: register it in the original J2K database and load issues. */
    suspend fun prepareComic(comic: Manga): Manga = withContext(Dispatchers.IO) {
        val existing = database.getManga(comic.url, comic.source).executeAsBlocking()
        val local = existing ?: comic.also {
            it.id = database.insertManga(it).executeAsBlocking().insertedId()
        }
        val source = sources.get(local.source) ?: return@withContext local
        // Prefer source metadata when the locally cached result is incomplete.
        if (!local.initialized || local.description.isNullOrBlank()) {
            runCatching {
                val updated = source.getMangaDetails(local.copy())
                local.copyFrom(updated)
                local.initialized = true
                database.updateMangaInfo(local).executeAsBlocking()
            }
        }
        // Use J2K's chapter synchronizer, which preserves read/download state.
        runCatching {
            val chapters = source.getChapterList(local)
            if (chapters.isNotEmpty()) {
                syncChaptersWithSource(database, chapters, local, source)
            }
        }
        local
    }
}
