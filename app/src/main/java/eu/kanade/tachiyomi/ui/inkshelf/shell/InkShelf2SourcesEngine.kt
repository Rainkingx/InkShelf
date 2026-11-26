package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
                        val found = withTimeoutOrNull(12_000L) {
                            page(source, 1, query).comics
                        }
                        if (found == null) emptyList<SourceResult>() to true
                        else found.take(35).map { SourceResult(it, source.name) } to false
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

    private fun sourceAllowed(source: Source): Boolean {
        if (source.id == LocalSource.ID) return true
        if (source.id in InkShelf2ExtensionPause.pausedSourceIds()) return false
        if (source.id.toString() in prefs.hiddenSources().get()) return false
        return source !is CatalogueSource || source.lang in prefs.enabledLanguages().get()
    }

    // Short-lived successful alternate resolution, not a library migration.
    // Guarded by issueLoadMutex, so a racing Read Now reuses the result.
    private data class RecentResolution(val result: ResolvedComic, val at: Long)
    private val recentAlternates = LinkedHashMap<String, RecentResolution>()

    /**
     * Refresh as InkShelf 1's MangaDetailsPresenter.refreshAll() does:
     * request metadata and chapter list together via getMangaUpdate, copy
     * metadata to the original J2K row, then synchronize its chapters.
     * The legacy reader and existing reading/download flags are preserved.
     */
    private suspend fun refreshUsingJ2K(local: Manga, source: Source): ResolvedComic {
        // XOXO paginates the full issue list. The original J2K details flow
        // does not impose the short 2.0 catalogue timeouts on this operation.
        val xoxo = source.name.contains("xoxo", ignoreCase = true)
        val existingChapters = database.getChapters(local).executeAsBlocking()
        if (existingChapters.isNotEmpty() && !local.description.isNullOrBlank()) {
            return ResolvedComic(local, source.name)
        }
        var failure: Throwable? = null
        val update = try {
            withTimeout(if (xoxo) 60_000L else 24_000L) {
                source.getMangaUpdate(
                    manga = local.copy(),
                    chapters = emptyList(),
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
        } catch (cancel: CancellationException) {
            if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
            currentCoroutineContext().ensureActive()
            failure = cancel
            null
        } catch (error: Exception) {
            failure = error
            null
        }
        if (update != null) {
            try {
                // Same model update and persistence as the InkShelf 1 presenter.
                local.copyFrom(update.manga)
                local.initialized = true
                database.updateMangaInfo(local).executeAsBlocking()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                failure = error
            }
            if (update.chapters.isNotEmpty()) {
                try {
                    syncChaptersWithSource(database, update.chapters, local, source)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    failure = error
                }
            }
        }

        // Some extensions can fetch chapter lists even when their metadata
        // endpoint fails. Use that original J2K API as a fallback.
        if (database.getChapters(local).executeAsBlocking().isEmpty()) {
            val chapterList = try {
                withTimeout(if (xoxo) 45_000L else 15_000L) { source.getChapterList(local) }
            } catch (cancel: CancellationException) {
                if (cancel !is kotlinx.coroutines.TimeoutCancellationException) throw cancel
            currentCoroutineContext().ensureActive()
                failure = cancel
                emptyList()
            } catch (error: Exception) {
                failure = error
                emptyList()
            }
            if (chapterList.isNotEmpty()) {
                try {
                    syncChaptersWithSource(database, chapterList, local, source)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    failure = error
                }
            }
        }
        if (database.getChapters(local).executeAsBlocking().isNotEmpty()) {
            return ResolvedComic(local, source.name)
        }
        // Do not mislabel a responsive installed extension as "no source".
        val code = Regex("""\b(401|403|404|408|429|500|502|503|504)\b""")
            .find(failure?.message.orEmpty())?.value
        val detail = code?.let { " (HTTP $it)" }
            ?: failure?.let { " (${it.javaClass.simpleName})" }.orEmpty()
        return ResolvedComic(
            local,
            source.name,
            if (failure != null) {
                "${source.name} failed to load this comic's issues$detail. Check the source or try its catalogue."
            } else {
                "${source.name} has no issues available for this edition yet."
            },
        )
    }

    /** Wait briefly for J2K's asynchronous extension registration. */
    private suspend fun installedSource(id: Long): Source? {
        sources.get(id)?.let { return it }
        withTimeoutOrNull(8_000L) {
            sources.catalogueSources.first { items -> items.any { it.id == id } }
        }
        return sources.get(id)
    }

    /**
     * A source-backed comic search is deliberately strict about the series
     * title and year, so e.g. Daredevil (2026) is not silently swapped for
     * Daredevil (2019) just because their names partially match.
     */
    suspend fun resolveReadableComic(comic: Manga): ResolvedComic = withContext(Dispatchers.IO) {
        issueLoadMutex.withLock { resolveLocked(comic) }
    }

    private suspend fun resolveLocked(comic: Manga): ResolvedComic {
        val local = database.getManga(comic.url, comic.source).executeAsBlocking()
            ?: comic.also {
                it.id = database.insertManga(it).executeAsBlocking().insertedId()
            }
        val requestedTitle = local.title
        val original = installedSource(local.source)
        val cached = database.getChapters(local).executeAsBlocking()
        // Never replace a saved issue list, including existing downloads.
        if (cached.isNotEmpty()) {
            if (original != null && sourceAllowed(original) && local.description.isNullOrBlank()) {
                prepareComic(local)
            }
            return ResolvedComic(local, original?.name)
        }
        val key = "${local.source}:${local.url}"
        recentAlternates[key]?.let { recent ->
            val source = sources.get(recent.result.manga.source)
            if (System.currentTimeMillis() - recent.at in 0L..120_000L &&
                source != null && sourceAllowed(source) &&
                database.getChapters(recent.result.manga).executeAsBlocking().isNotEmpty()
            ) return recent.result
            recentAlternates.remove(key)
        }
        val originalResult = if (original != null && sourceAllowed(original)) {
            refreshUsingJ2K(local, original)
        } else ResolvedComic(local, original?.name, "The original source is unavailable or disabled.")
        if (originalResult.problem == null) return originalResult

        // A confirmed year is mandatory for automatic switching. A yearless
        // title must be selected manually rather than guessing a reboot.
        if (InkShelf2ComicIdentity.year(requestedTitle) == null || local.source == LocalSource.ID) {
            return originalResult.copy(problem = originalResult.problem +
                " Choose an alternative edition manually in Discover.")
        }
        val alternate = withTimeoutOrNull(45_000L) { findAlternate(local, requestedTitle) }
        if (alternate != null) {
            recentAlternates[key] = RecentResolution(alternate, System.currentTimeMillis())
            while (recentAlternates.size > 64) recentAlternates.remove(recentAlternates.keys.first())
            return alternate
        }
        return originalResult.copy(problem = originalResult.problem +
            " No verified alternative returned issues. Your library data is unchanged.")
    }

    private suspend fun findAlternate(local: Manga, requestedTitle: String): ResolvedComic? {
        val catalogues = sources.getCatalogueSources()
            .filter { it.id != LocalSource.ID && it.id != local.source && sourceAllowed(it) }
            .distinctBy { it.id }
            .sortedBy { it.name }
            .take(8)
        val query = InkShelf2ComicIdentity.searchTitle(requestedTitle)
        val limiter = Semaphore(3)
        val matches = supervisorScope {
            catalogues.map { source ->
                async {
                    limiter.withPermit {
                        try {
                            withTimeoutOrNull(10_000L) {
                                source.getSearchManga(1, query, source.getFilterList()).mangas
                                    .filter { InkShelf2ComicIdentity.isVerifiedAlternative(requestedTitle, it.title) }
                                    .take(4)
                                    .map { source to it }
                            }.orEmpty()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            emptyList<Pair<CatalogueSource, SManga>>()
                        }
                    }
                }
            }.awaitAll().flatten()
        }.distinctBy { (source, remote) -> "${source.id}:${remote.url}" }
            .sortedBy { it.first.name }

        for ((source, remote) in matches.take(6)) {
            if (!sourceAllowed(source)) continue
            // Probe chapters before inserting a candidate into the database.
            val remoteChapters = try {
                withTimeoutOrNull(8_000L) { source.getChapterList(remote) }.orEmpty()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }
            if (remoteChapters.isEmpty()) continue

            val resolved = database.getManga(remote.url, source.id).executeAsBlocking()
                ?: Manga.create(remote.url, remote.title, source.id).apply {
                    copyFrom(remote)
                    id = database.insertManga(this).executeAsBlocking().insertedId()
                }
            if (!InkShelf2ComicIdentity.isVerifiedAlternative(requestedTitle, resolved.title)) continue
            try {
                syncChaptersWithSource(database, remoteChapters, resolved, source)
                if (database.getChapters(resolved).executeAsBlocking().isNotEmpty()) {
                    // Leave details enrichment to the normal UI; never lose a
                    // good issue list because metadata is slow or unavailable.
                    return ResolvedComic(resolved, source.name)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Continue to another provider if this one cannot sync.
            }
        }

        return null
    }

    /**
     * On-demand first read for catalogue and recommendation comics. Those
     * cards commonly have artwork + metadata but no cached issue list yet.
     * Resolve the original J2K manga row, fetch issues from its installed
     * extension when needed, and preserve all existing read/download flags.
     */
    suspend fun ensureIssues(comic: Manga, refreshCached: Boolean = false): Pair<Manga, List<Chapter>> =
        withContext(Dispatchers.IO) {
            issueLoadMutex.withLock {
                val saved = database.getManga(comic.url, comic.source).executeAsBlocking()
                val local = saved ?: comic.also {
                    it.id = database.insertManga(it).executeAsBlocking().insertedId()
                }
                val cached = database.getChapters(local).executeAsBlocking()
                if (cached.isNotEmpty() && !refreshCached) return@withLock local to cached

                val source = installedSource(local.source)
                    ?.takeIf { sourceAllowed(it) }
                    ?: throw IllegalStateException("Comic source is unavailable or disabled.")
                // Guide retry only: a partial/stale cache must not prevent a
                // missing issue being found. Never clear existing chapters on
                // an empty or failed response; use the normal J2K synchronizer.
                if (cached.isNotEmpty() && refreshCached) {
                    val fetched = try {
                        withTimeoutOrNull(20_000L) { source.getChapterList(local.copy()) }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        null
                    }
                    if (!fetched.isNullOrEmpty()) {
                        syncChaptersWithSource(database, fetched, local, source)
                    }
                    return@withLock local to database.getChapters(local).executeAsBlocking()
                }
                // Exactly the same data-fetching path used by the Details
                // screen and the original InkShelf 1 presenter.
                // Guide lookups remain pinned to the selected source. Share
                // the same issue-loading lock and original J2K refresh path.
                refreshUsingJ2K(local, source)
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
            currentCoroutineContext().ensureActive()
                // Partial cached metadata is still enough to open Details.
            } catch (_: Exception) {
                // Offline/missing metadata must not stop Comic Details opening.
            }
        }
        local
    }
}
