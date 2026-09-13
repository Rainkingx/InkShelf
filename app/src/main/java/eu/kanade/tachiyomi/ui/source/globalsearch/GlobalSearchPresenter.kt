package eu.kanade.tachiyomi.ui.source.globalsearch

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.manga.duplicateLibraryMangaIds
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.Locale

/**
 * Presenter of [GlobalSearchController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param db manages the database calls.
 * @param preferences manages the preference calls.
 */
open class GlobalSearchPresenter(
    private val initialQuery: String? = "",
    private val initialExtensionFilter: String? = null,
    private val sourcesToUse: List<CatalogueSource>? = null,
    val sourceManager: SourceManager = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : BaseCoroutinePresenter<GlobalSearchController>() {
    /**
     * Enabled sources.
     */
    val sources by lazy { getSourcesToQuery() }

    private var fetchSourcesJob: Job? = null

    private var loadTime = hashMapOf<Long, Long>()

    var query = ""

    private var searchGeneration = 0L

    private var initialized = false

    private val extensionManager: ExtensionManager by injectLazy()

    private var extensionFilter: String? = null

    var items: List<GlobalSearchItem> = emptyList()

    override fun onCreate() {
        super.onCreate()

        if (!initialized) {
            initialized = true
            extensionFilter = initialExtensionFilter
            search(initialQuery.orEmpty())
        }
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    protected open fun getEnabledSources(): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val hiddenCatalogues = preferences.hiddenSources().get()
        val pinnedCatalogues = preferences.pinnedCatalogues().get()

        val list =
            sourceManager
                .getCatalogueSources()
                .filter { it.lang in languages }
                .filterNot { it.id.toString() in hiddenCatalogues }
                .sortedBy { "(${it.lang}) ${it.name}" }

        return if (preferences.onlySearchPinned().get()) {
            list.filter { it.id.toString() in pinnedCatalogues }
        } else {
            list.sortedBy { it.id.toString() !in pinnedCatalogues }
        }
    }

    private fun getSourcesToQuery(): List<CatalogueSource> {
        if (sourcesToUse != null) return sourcesToUse
        val filter = extensionFilter
        val enabledSources = getEnabledSources()
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        val languages = preferences.enabledLanguages().get()
        val filterSources =
            extensionManager.installedExtensionsFlow.value
                .filter { it.pkgName == filter }
                .flatMap { it.sources }
                .filter { it.lang in languages }
                .filterIsInstance<CatalogueSource>()

        if (filterSources.isEmpty()) {
            return enabledSources
        }

        return filterSources
    }

    /**
     * Creates a catalogue search item
     */
    protected open fun createCatalogueSearchItem(
        source: CatalogueSource,
        results: List<GlobalSearchMangaItem>?,
    ): GlobalSearchItem = GlobalSearchItem(source, results)

    fun confirmDeletion(manga: Manga) {
        coverCache.deleteFromCache(manga)
        val downloadManager: DownloadManager = Injekt.get()
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteManga(manga, source)
        }
    }

    /**
     * Initiates a search for manga per catalogue.
     *
     * @param query query on which to search.
     */
    fun search(query: String) {
        // Explicit submission also retries the same query. View restoration never submits.
        val generation = ++searchGeneration
        fetchSourcesJob?.cancel()
        fetchSourcesJob = null
        this.query = query
        loadTime.clear()
        items = if (query.isBlank()) emptyList() else sources.map { createCatalogueSearchItem(it, null) }
        view?.setItems(items)
        if (query.isBlank()) return

        val pinnedSourceIds = preferences.pinnedCatalogues().get()
        // Confine state updates to Main; source and database work runs on IO.
        fetchSourcesJob =
            presenterScope.launchUI {
                val semaphore = Semaphore(5)
                sources.forEach { source ->
                    launch {
                        val mangaItems =
                            semaphore.withPermit {
                                try {
                                    withTimeoutOrNull(12_000L) {
                                        withIOContext {
                                            val mangas =
                                                source
                                                    .getSearchManga(1, query, source.getFilterList())
                                                    .mangas
                                                    .take(10)
                                                    .map { networkToLocalManga(it, source.id) }
                                            val duplicateIds =
                                                if (preferences.showDuplicateInLibraryItems().get()) {
                                                    db.duplicateLibraryMangaIds(mangas)
                                                } else {
                                                    emptySet()
                                                }
                                            mangas.map { manga ->
                                                GlobalSearchMangaItem(manga, isDuplicateInLibrary = manga.id in duplicateIds)
                                            }
                                        }
                                    } ?: emptyList()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        // Cancelled/obsolete requests must never replace a newer query's state.
                        if (generation != searchGeneration) return@launch
                        if (mangaItems.isNotEmpty()) loadTime[source.id] = Date().time
                        val result = createCatalogueSearchItem(source, mangaItems)
                        items =
                            items
                                .map { if (it.source.id == source.id) result else it }
                                .sortedWith(
                                    compareBy(
                                        { it.results.isNullOrEmpty() },
                                        { it.source.id.toString() !in pinnedSourceIds },
                                        { loadTime[it.source.id] ?: 0L },
                                        { "${it.source.name.lowercase(Locale.getDefault())} (${it.source.lang})" },
                                    ),
                                )
                        view?.setItems(items)
                        // Details are children of this search, so clear/retry cancels them too.
                        mangaItems
                            .map { it.manga }
                            .filter { it.thumbnail_url == null && !it.initialized }
                            .forEach { manga ->
                                launch {
                                    try {
                                        withTimeoutOrNull(12_000L) {
                                            withIOContext { getMangaDetails(manga, source) }
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (_: Exception) {
                                        // Keep the search result even when cover details fail.
                                    }
                                    if (generation == searchGeneration) view?.onMangaInitialized(source, manga)
                                }
                            }
                    }
                }
            }
    }

    /**
     * Initializes the given manga.
     *
     * @param manga the manga to initialize.
     * @return The initialized manga.
     */
    private suspend fun getMangaDetails(
        manga: Manga,
        source: Source,
    ): Manga {
        val networkManga =
            source.getMangaUpdate(manga.copy(), emptyList(), fetchDetails = true, fetchChapters = false).manga
        manga.copyFrom(networkManga)
        manga.initialized = true
        db.insertManga(manga).executeAsBlocking()
        return manga
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    protected open fun networkToLocalManga(
        sManga: SManga,
        sourceId: Long,
    ): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        } else if (!localManga.favorite) {
            // if the manga isn't a favorite, set its display title from source
            // if it later becomes a favorite, updated title will go to db
            localManga.title = sManga.title
        }
        return localManga
    }
}
