package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import java.util.Locale
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
    private val prefs: PreferencesHelper = Injekt.get()
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
     * Recommendation cards can outlive the extension that first supplied
     * them. A cached cover is not proof that its source is still installed or
     * provides readable issues. Resolve a real source+series+issues pair
     * before presenting the card as a readable comic.
     *
     * Never silently migrate an existing library favourite; the original
     * J2K library and its read/download history are preserved.
     */
    data class ResolvedComic(
        val manga: Manga,
        val sourceName: String?,
        val problem: String? = null,
    )

    private fun seriesYear(title: String): String? =
        Regex("""\(((?:19|20)\d{2})(?:[-–]\d{0,4})?\)\s*$""")
            .find(title.trim())?.groupValues?.get(1)

    private fun searchTitle(title: String): String =
        title.trim().replace(
            Regex("""\s*\((?:19|20)\d{2}(?:[-–]\d{0,4})?\)\s*$"""), "",
        ).trim()

    private fun titleKey(title: String): String =
        searchTitle(title).lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]+"""), " ").trim()

    private fun matchesRequestedEdition(requested: String, candidate: String): Boolean {
        if (titleKey(requested) != titleKey(candidate)) return false
        val desiredYear = seriesYear(requested)
        val candidateYear = seriesYear(candidate)
        // A known different year could be an entirely different comic series.
        return desiredYear == null || candidateYear == null || desiredYear == candidateYear
    }

    private suspend fun hydrateDetails(local: Manga, source: Source) {
        if (local.initialized && !local.description.isNullOrBlank()) return
        try {
            val details = withTimeout(9_000L) { source.getMangaDetails(local.copy()) }
            local.copyFrom(details)
            local.initialized = true
            database.updateMangaInfo(local).executeAsBlocking()
        } catch (cancel: CancellationException) {
            if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
        } catch (_: Exception) {
            // A source without synopsis can still supply readable issues.
        }
    }

    private suspend fun verifyAndSync(local: Manga, source: Source): Boolean {
        val existingIssues = database.getChapters(local).executeAsBlocking()
        if (existingIssues.isNotEmpty()) {
            hydrateDetails(local, source)
            return true
        }
        val remoteIssues = try {
            withTimeout(12_000L) { source.getChapterList(local) }
        } catch (cancel: CancellationException) {
            if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (remoteIssues.isEmpty()) return false
        syncChaptersWithSource(database, remoteIssues, local, source)
        hydrateDetails(local, source)
        return database.getChapters(local).executeAsBlocking().isNotEmpty()
    }

    /**
     * A source-backed comic search is deliberately strict about the series
     * title and year, so e.g. Daredevil (2026) is not silently swapped for
     * Daredevil (2019) just because their names partially match.
     */
    suspend fun resolveReadableComic(comic: Manga): ResolvedComic = withContext(Dispatchers.IO) {
        val local = database.getManga(comic.url, comic.source).executeAsBlocking()
            ?: comic.also {
                it.id = database.insertManga(it).executeAsBlocking().insertedId()
            }
        val original = sources.get(local.source)
        if (original != null && verifyAndSync(local, original)) {
            return@withContext ResolvedComic(local, original.name)
        }

        if (local.favorite) {
            return@withContext ResolvedComic(
                local,
                original?.name,
                "The library source could not provide issues. Your saved series has not been changed.",
            )
        }

        val enabledLanguages = prefs.enabledLanguages().get()
        val hidden = prefs.hiddenSources().get()
        val catalogues = sources.getCatalogueSources()
            .filter { it.id != LocalSource.ID }
            .filter { it.lang in enabledLanguages && it.id.toString() !in hidden }
            .filterNot { it.id == local.source }
            .distinctBy { it.id }
        val query = searchTitle(local.title)
        val limiter = Semaphore(3)
        val matches = supervisorScope {
            catalogues.map { source ->
                async {
                    limiter.withPermit {
                        try {
                            withTimeout(10_000L) {
                                source.getSearchManga(1, query, source.getFilterList()).mangas
                                    .filter { matchesRequestedEdition(local.title, it.title) }
                                    .take(4)
                                    .map { source to it }
                            }
                        } catch (cancel: CancellationException) {
                            if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
                            emptyList<Pair<CatalogueSource, SManga>>()
                        } catch (_: Exception) {
                            emptyList<Pair<CatalogueSource, SManga>>()
                        }
                    }
                }
            }.awaitAll().flatten()
        }.sortedWith(
            compareByDescending<Pair<CatalogueSource, SManga>> {
                seriesYear(it.second.title) == seriesYear(local.title)
            }.thenBy { it.first.name },
        )

        for ((source, remote) in matches.take(9)) {
            // Probe chapters before inserting a candidate into the database.
            val remoteChapters = try {
                withTimeout(12_000L) { source.getChapterList(remote) }
            } catch (cancel: CancellationException) {
                if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            if (remoteChapters.isEmpty()) continue

            val resolved = database.getManga(remote.url, source.id).executeAsBlocking()
                ?: Manga.create(remote.url, remote.title, source.id).apply {
                    copyFrom(remote)
                    id = database.insertManga(this).executeAsBlocking().insertedId()
                }
            syncChaptersWithSource(database, remoteChapters, resolved, source)
            hydrateDetails(resolved, source)
            return@withContext ResolvedComic(resolved, source.name)
        }

        ResolvedComic(
            local,
            original?.name,
            "No installed comic source returned readable issues for this title. Search Sources to choose an edition.",
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

    /**
     * The single source of truth for every 2.0 Comic Details route.
     * Resolve the original J2K database row and hydrate missing metadata.
     * Issues load independently when Details appears, preventing a slow
     * chapter request from blocking navigation for a newly found comic.
     */
    suspend fun prepareComic(comic: Manga): Manga = withContext(Dispatchers.IO) {
        val existing = database.getManga(comic.url, comic.source).executeAsBlocking()
        val local = existing ?: comic.also {
            it.id = database.insertManga(it).executeAsBlocking().insertedId()
        }
        val source = sources.get(local.source) ?: return@withContext local
        if (!local.initialized || local.description.isNullOrBlank()) {
            try {
                val updated = withTimeout(12_000L) { source.getMangaDetails(local.copy()) }
                local.copyFrom(updated)
                local.initialized = true
                database.updateMangaInfo(local).executeAsBlocking()
            } catch (cancel: CancellationException) {
                if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
                // Partial cached metadata is still enough to open Details.
            } catch (_: Exception) {
                // Offline/missing metadata must not stop Comic Details opening.
            }
        }
        local
    }
}
