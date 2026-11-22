package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverRefreshWorker
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A genuinely separate Compose activity: no MainActivity XML, toolbar,
 * old Conductor controllers, legacy bottom navigation or header overlays.
 *
 * Exposed as a SECOND launcher icon only while the preview is under development.
 * The stable launcher remains available and the library database is shared.
 */
class InkShelf2Activity : AppCompatActivity() {
    private val engine by lazy { InkShelf2Engine(applicationContext) }
    private val sourcesEngine by lazy { InkShelf2SourcesEngine() }
    private val downloadManager: DownloadManager by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }
    private val extensionManager: ExtensionManager by lazy { Injekt.get() }
    private val preferences: PreferencesHelper by lazy { Injekt.get() }
    private val profileStore by lazy { InkShelf2ProfileStore(applicationContext) }
    private var refreshEpoch by mutableIntStateOf(0)
    private var lastBackAtMillis = 0L
    private var readerOpenInProgress by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureActivityDelegate.setSecure(this)
        // Restore the selected 2.0 palette before first composition (no purple flash).
        InkShelf2Themes.restore(this)
        // Keep the same six-hour Discover feed refresh when users launch only
        // the new Compose shell. KEEP ensures no duplicate workers are created.
        InkShelfDiscoverRefreshWorker.schedule(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            var tab by rememberSaveable { mutableStateOf(InkShelf2Tab.HOME) }
            var profileName by remember { mutableStateOf(profileStore.displayName()) }
            var welcomeOpen by rememberSaveable {
                mutableStateOf(!profileStore.hasCompletedWelcome())
            }
            var welcomeStep by rememberSaveable { mutableIntStateOf(0) }
            // Persist visited main tabs rather than relying on Activity.finish().
            // Store only enum names, which are safe for Android saved state.
            var tabTrail by rememberSaveable { mutableStateOf("HOME") }
            var searchFocusEpoch by remember { mutableIntStateOf(0) }
            var discoverSearchPreset by remember { mutableStateOf("") }
            var sourceHubOpen by rememberSaveable { mutableStateOf(false) }
            var metronOpen by rememberSaveable { mutableStateOf(false) }
            var themesOpen by rememberSaveable { mutableStateOf(false) }
            var downloadsOpen by rememberSaveable { mutableStateOf(false) }
            var historyOpen by rememberSaveable { mutableStateOf(false) }
            var sourceHubMode by rememberSaveable { mutableStateOf("Sources") }
            var activeSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
            var selectedDay by remember { mutableStateOf<InkShelf2Engine.Day?>(null) }
            var selectedDiscoverShelf by rememberSaveable { mutableStateOf<String?>(null) }
            fun openTab(destination: InkShelf2Tab) {
                if (tab != destination) {
                    tabTrail = (tabTrail.split('|').takeLast(20) + destination.name)
                        .joinToString("|")
                    tab = destination
                }
                sourceHubOpen = false
                metronOpen = false
                themesOpen = false
                downloadsOpen = false
                historyOpen = false
                activeSourceId = null
                selectedDay = null
                selectedDiscoverShelf = null
            }
            val installedExtensions by extensionManager.installedExtensionsFlow.collectAsState()
            val availableExtensions by extensionManager.availableExtensionsFlow.collectAsState()
            val untrustedExtensions by extensionManager.untrustedExtensionsFlow.collectAsState()
            val catalogueSources by sourceManager.catalogueSources.collectAsState(initial = emptyList())
            val enabledLanguages = preferences.enabledLanguages().get()
            val hiddenSources = preferences.hiddenSources().get()
            val enabledSources = catalogueSources.filter {
                it.id != LocalSource.ID && it.lang in enabledLanguages &&
                    it.id.toString() !in hiddenSources
            }
            val enabledSourceIds = enabledSources.map { it.id }.toSet()
            // MangaImpl.equals compares only URL and source, so an updated
            // database row may be 'equal' despite having new synopsis/cover
            // metadata. Use identity changes to refresh the Compose details.
            var selected by remember {
                mutableStateOf<Manga?>(null, referentialEqualityPolicy())
            }
            // Related-comic clicks keep a native Details back stack. This is
            // navigation state only; source URLs and reader data are untouched.
            var comicBackStack by remember { mutableStateOf<List<Manga>>(emptyList()) }
            var home by remember { mutableStateOf<InkShelf2Engine.Home?>(null) }
            var library by remember { mutableStateOf<List<InkShelf2LibraryItem>>(emptyList()) }
            var discover by remember { mutableStateOf<InkShelf2Engine.Discover?>(null) }
            var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
            var loadingIssues by remember { mutableStateOf(false) }
            var issuesError by remember { mutableStateOf<String?>(null) }
            var sourceLinkError by remember { mutableStateOf<String?>(null) }
            var detailsRevision by remember { mutableIntStateOf(0) }
            var downloadQueue by remember { mutableStateOf<List<InkShelf2QueueEntry>>(emptyList()) }
            var offlineSeries by remember { mutableStateOf<List<InkShelf2SavedSeries>>(emptyList()) }
            var downloadsWifiRequired by remember { mutableStateOf(false) }
            var downloadsPaused by remember { mutableStateOf(false) }
            var readingHistory by remember { mutableStateOf<List<InkShelf2HistoryEntry>>(emptyList()) }

            LaunchedEffect(downloadsOpen, library) {
                if (downloadsOpen) {
                    downloadManager.awaitDownloadCacheReady()
                    while (true) {
                        downloadQueue = downloadManager.queue.map { job ->
                            InkShelf2QueueEntry(
                                manga = job.manga,
                                chapter = job.chapter,
                                status = job.status,
                                percent = job.progress.coerceIn(0, 100),
                            )
                        }
                        offlineSeries = withContext(Dispatchers.IO) {
                            library.mapNotNull { item ->
                                val count = downloadManager.getDownloadCount(item.manga)
                                if (count > 0) InkShelf2SavedSeries(item.manga, count) else null
                            }.sortedBy { it.manga.title.lowercase() }
                        }
                        downloadsWifiRequired = preferences.downloadOnlyOverWifi() &&
                            !applicationContext.isConnectedToWifi()
                        downloadsPaused = downloadManager.isPaused()
                        delay(1_500)
                    }
                }
            }
            LaunchedEffect(historyOpen, refreshEpoch) {
                if (historyOpen) readingHistory = engine.loadHistory()
            }

            LaunchedEffect(refreshEpoch, enabledSourceIds) {
                // Wait for real installed-source registration. A source
                // appearing or disappearing updates the themed picks without
                // opening any extra external searches.
                home = engine.loadHome(enabledSourceIds)
                library = engine.loadLibrary()
                discover = engine.loadDiscover()
            }
            // InkShelf 1's flow: show Comic Details immediately, then fetch
            // its metadata and issue list asynchronously. Do not block the
            // navigation with a multi-extension search or a loading page.
            LaunchedEffect(selected?.source, selected?.url, refreshEpoch) {
                val comic = selected
                chapters = emptyList()
                issuesError = null
                if (comic == null) {
                    loadingIssues = false
                    return@LaunchedEffect
                }

                val cached = engine.chapters(comic)
                chapters = cached
                // On return from the reader, refresh progress from J2K
                // without repeatedly searching the network for a ready comic.
                if (cached.isNotEmpty() && !comic.description.isNullOrBlank()) {
                    loadingIssues = false
                    return@LaunchedEffect
                }

                loadingIssues = cached.isEmpty()
                try {
                    // The existing source is checked first. Only stale or
                    // missing entries need a search across installed sources.
                    val match = sourcesEngine.resolveReadableComic(comic)
                    if (selected?.source != comic.source || selected?.url != comic.url) {
                        return@LaunchedEffect
                    }
                    sourceLinkError = match.problem
                    // Keep the exact 2.0 design while replacing the card's
                    // potentially partial cache row with J2K's fully updated
                    // database row. The identity policy above makes this
                    // recompose even when source+URL are unchanged.
                    chapters = engine.chapters(match.manga)
                    selected = match.manga
                    detailsRevision += 1
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    issuesError = "Could not refresh this comic. Check your source connection."
                } finally {
                    loadingIssues = false
                }
            }

            // One immediate route for EVERY comic card, just like InkShelf 1.
            val openComicDetails: (Manga) -> Unit = { manga ->
                comicBackStack = emptyList()
                sourceLinkError = null
                issuesError = null
                loadingIssues = true
                selected = manga
            }
            fun backFromComicDetails() {
                val previousComic = comicBackStack.lastOrNull()
                comicBackStack = comicBackStack.dropLast(1)
                sourceLinkError = null
                issuesError = null
                loadingIssues = previousComic != null
                selected = previousComic
            }

            // Register at the shell root, so Back always navigates inside
            // InkShelf 2.0 (including Library, Account and Discover), rather
            // than immediately finishing the preview Activity on those tabs.
            BackHandler(enabled = true) {
                when {
                    selected != null -> backFromComicDetails()
                    selectedDiscoverShelf != null -> selectedDiscoverShelf = null
                    selectedDay != null -> selectedDay = null
                    activeSourceId != null -> activeSourceId = null
                    sourceHubOpen -> sourceHubOpen = false
                    metronOpen -> metronOpen = false
                    themesOpen -> themesOpen = false
                    downloadsOpen -> downloadsOpen = false
                    historyOpen -> historyOpen = false
                    welcomeOpen -> {
                        welcomeStep = 0
                        profileStore.completeWelcome()
                        welcomeOpen = false
                    }
                    tabTrail.contains('|') -> {
                        tabTrail = tabTrail.substringBeforeLast('|')
                        tab = InkShelf2Tab.valueOf(tabTrail.substringAfterLast('|'))
                    }
                    tab != InkShelf2Tab.HOME -> {
                        tab = InkShelf2Tab.HOME
                        tabTrail = "HOME"
                    }
                    else -> {
                        // At the root there is no previous page. Require an
                        // intentional second Back instead of an accidental exit.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastBackAtMillis < 2_000L) {
                            finish()
                        } else {
                            lastBackAtMillis = now
                            Toast.makeText(
                                this@InkShelf2Activity,
                                "Press Back again to exit InkShelf",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
            InkShelf2Shell(
                currentTab = tab,
                showHeader = selected == null && !welcomeOpen,
                showBottomNav = selected == null && !welcomeOpen,
                onTabSelected = { openTab(it) },
                onSearch = {
                    openTab(InkShelf2Tab.DISCOVER)
                    discoverSearchPreset = ""
                    searchFocusEpoch += 1
                },
                onNotifications = {
                    Toast.makeText(
                        this@InkShelf2Activity,
                        "Notifications are still in the original InkShelf app",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) { padding ->
                val comic = selected
                val curatedShelves = remember(discover, library, enabledSourceIds) {
                    // All curated cards reuse real J2K Manga entries from
                    // installed sources. No separate Details/reader flow.
                    InkShelf2DiscoverCuration.build(discover, library, enabledSourceIds)
                }
                val trendingRank = remember(discover, enabledSourceIds) {
                    discover?.trending.orEmpty()
                        .filter { it.source in enabledSourceIds }
                        .mapIndexed { index, manga ->
                            "${manga.source}:${manga.url}" to index
                        }
                        .toMap()
                }
                when {
                    comic != null -> key(detailsRevision, comic.source, comic.url) {
                        var downloadedIds by remember(comic.source, comic.url) {
                            mutableStateOf<Set<Long>>(emptySet())
                        }
                        var queuedIds by remember(comic.source, comic.url) {
                            mutableStateOf<Set<Long>>(emptySet())
                        }
                        var downloadStates by remember(comic.source, comic.url) {
                            mutableStateOf<Map<Long, InkShelf2IssueDownloadInfo>>(emptyMap())
                        }
                        var waitingForWifi by remember { mutableStateOf(false) }
                        var downloadsPaused by remember { mutableStateOf(false) }
                        // Read the existing J2K download cache and queue. The
                        // worker can leave items queued when Wi-Fi-only is on.
                        LaunchedEffect(comic.source, comic.url, chapters) {
                            if (chapters.isNotEmpty()) {
                                downloadManager.awaitDownloadCacheReady()
                                while (true) {
                                    downloadedIds = chapters.filter {
                                        it.id != null && downloadManager.isChapterDownloaded(it, comic)
                                    }.mapNotNull { it.id }.toSet()
                                    val queued = downloadManager.queue.filter {
                                        it.manga.source == comic.source && it.manga.url == comic.url
                                    }
                                    downloadStates = queued.mapNotNull { entry ->
                                        entry.chapter.id?.let { id ->
                                            id to InkShelf2IssueDownloadInfo(
                                                status = entry.status,
                                                progress = entry.progress.coerceIn(0, 100),
                                            )
                                        }
                                    }.toMap()
                                    queuedIds = queued.filter {
                                        it.status != Download.State.ERROR &&
                                            it.status != Download.State.NOT_DOWNLOADED
                                    }.mapNotNull { it.chapter.id }.toSet()
                                    waitingForWifi = preferences.downloadOnlyOverWifi() &&
                                        !applicationContext.isConnectedToWifi()
                                    downloadsPaused = downloadManager.isPaused()
                                    delay(1_000)
                                }
                            } else {
                                downloadedIds = emptySet()
                                queuedIds = emptySet()
                                downloadStates = emptyMap()
                            }
                        }
                        val similarPicks = remember(detailsRevision, comic.source, comic.url, discover, library, enabledSources) {
                            // All candidates originate from the EXISTING J2K
                            // Discover cache or Library. No synthetic URLs and
                            // no additional extension searches on page opening.
                            InkShelf2MoreLikeThis.select(
                                focus = comic,
                                candidates = buildList {
                                    addAll(discover?.recommendationDetails.orEmpty().map { it.manga })
                                    addAll(discover?.trending.orEmpty())
                                    addAll(discover?.newest.orEmpty())
                                    addAll(library.map { it.manga })
                                },
                                enabledSources = enabledSources.map { it.id }.toSet(),
                            )
                        }
                        InkShelf2DetailsScreen(
                        manga = comic,
                        chapters = chapters,
                        padding = padding,
                        onBack = { backFromComicDetails() },
                        onRead = { openReader(comic) },
                        sourceName = sourceManager.get(comic.source)?.name,
                        sourceLinkError = sourceLinkError,
                        onFindSource = {
                            val title = comic.title
                            selected = null
                            comicBackStack = emptyList()
                            sourceLinkError = null
                            openTab(InkShelf2Tab.DISCOVER)
                            discoverSearchPreset = title
                            searchFocusEpoch += 1
                        },
                        isOpeningReader = readerOpenInProgress,
                        isLoadingIssues = loadingIssues,
                        issuesError = issuesError,
                        onChapter = { openChapter(comic, it) },
                        onDownload = { requested -> downloadIssues(comic, requested) },
                        canDownload = sourceManager.get(comic.source) is HttpSource && sourceLinkError == null,
                        downloadedChapterIds = downloadedIds,
                        queuedChapterIds = queuedIds,
                        downloadStates = downloadStates,
                        waitingForWifi = waitingForWifi,
                        downloadsPaused = downloadsPaused,
                        onResumeDownloads = { resumeDownloads() },
                        onDeleteIssueDownload = { chapter -> removeOfflineIssue(comic, chapter) },
                        onSave = { saveComic(comic) },
                        similarComics = similarPicks,
                        onSimilarComic = { related ->
                            // This is exactly the same source-aware navigation
                            // as Home, Library and Discover. Preserve the
                            // previous Details page when a related cover opens.
                            if (related.source != comic.source || related.url != comic.url) {
                                comicBackStack = (comicBackStack + comic).takeLast(12)
                                sourceLinkError = null
                                issuesError = null
                                loadingIssues = true
                                selected = related
                            }
                        },
                    )
                    }
                    selectedDiscoverShelf != null -> InkShelf2DiscoverShelfScreen(
                        title = selectedDiscoverShelf!!,
                        comics = when (selectedDiscoverShelf) {
                            "New This Week" -> discover?.newest.orEmpty()
                                .filter { it.source in enabledSourceIds }
                            "Recommended for You" -> discover?.recommendationDetails.orEmpty()
                                .map { it.manga }.filter { it.source in enabledSourceIds }
                            "Trending Right Now" -> discover?.trending.orEmpty()
                                .filter { it.source in enabledSourceIds }
                            else -> curatedShelves.firstOrNull {
                                it.title == selectedDiscoverShelf
                            }?.comics.orEmpty()
                        },
                        recommendationReasons = when (selectedDiscoverShelf) {
                            "Recommended for You" ->
                                discover?.recommendationDetails.orEmpty().associate {
                                    "${it.manga.source}:${it.manga.url}" to it.reason
                                }
                            else -> curatedShelves.firstOrNull {
                                it.title == selectedDiscoverShelf
                            }?.reasons.orEmpty()
                        },
                        trendingRanks = trendingRank,
                        padding = padding,
                        onBack = { selectedDiscoverShelf = null },
                        onOpen = openComicDetails,
                    )
                    selectedDay != null -> InkShelf2DailyPicksScreen(
                        day = selectedDay!!,
                        padding = padding,
                        onBack = { selectedDay = null },
                        onOpen = openComicDetails,
                    )
                    welcomeOpen &&
                        (tab != InkShelf2Tab.ACCOUNT ||
                            (!sourceHubOpen && !metronOpen && !themesOpen)) ->
                        InkShelf2WelcomeScreen(
                        padding = padding,
                        displayName = profileName,
                        installedSourceCount = enabledSources.size,
                        step = welcomeStep,
                        onStepChange = { welcomeStep = it },
                        onNameChange = { chosenName ->
                            profileStore.setName(chosenName)
                            profileName = profileStore.displayName()
                        },
                        onFinish = {
                            welcomeStep = 0
                            profileStore.completeWelcome()
                            welcomeOpen = false
                            openTab(InkShelf2Tab.HOME)
                        },
                        onBrowseSources = {
                            openTab(InkShelf2Tab.ACCOUNT)
                            sourceHubMode = "Sources"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onBackupTools = {
                            startActivity(
                                Intent(this@InkShelf2Activity, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_BACKUP_TOOLS),
                            )
                        },
                        onImportComics = {
                            startActivity(
                                Intent(this@InkShelf2Activity, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_LOCAL_COMIC_IMPORT),
                            )
                        },
                    )
                    tab == InkShelf2Tab.HOME -> InkShelf2HomeScreen(
                        home = home,
                        library = library,
                        personalPicks = discover?.recommendationDetails.orEmpty().filter { pick ->
                            pick.manga.source in enabledSourceIds &&
                                library.none { it.manga.source == pick.manga.source && it.manga.url == pick.manga.url }
                        },
                        profileName = profileName,
                        padding = padding,
                        onOpen = openComicDetails,
                        onRead = { openReader(it) },
                        onSave = { saveComic(it) },
                        onDiscover = { openTab(InkShelf2Tab.DISCOVER) },
                        onLibrary = { openTab(InkShelf2Tab.LIBRARY) },
                        onDayPicks = { selectedDay = it },
                        onTrendingSeeAll = { selectedDiscoverShelf = "Trending Right Now" },
                    )
                    tab == InkShelf2Tab.ACCOUNT && sourceHubOpen -> InkShelf2SourceHub(
                        padding = padding,
                        sources = catalogueSources,
                        installed = installedExtensions,
                        available = availableExtensions,
                        untrusted = untrustedExtensions,
                        selectedSourceId = activeSourceId,
                        onSourceSelected = { activeSourceId = it },
                        onClose = {
                            activeSourceId = null
                            sourceHubOpen = false
                        },
                        initialMode = sourceHubMode,
                        onOpenComic = openComicDetails,
                    )
                    tab == InkShelf2Tab.DISCOVER -> InkShelf2DiscoverScreen(
                        discover = discover,
                        curatedShelves = curatedShelves,
                        padding = padding,
                        onOpen = openComicDetails,
                        sources = enabledSources,
                        onOpenSearchResult = openComicDetails,
                        searchFocusToken = searchFocusEpoch,
                        initialSearchQuery = discoverSearchPreset,
                        onManageSources = {
                            openTab(InkShelf2Tab.ACCOUNT)
                            sourceHubMode = "Sources"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onSeeAll = { selectedDiscoverShelf = it },
                        onRecommendationsUpdated = {
                            discover = engine.loadDiscover()
                            home = engine.loadHome(enabledSourceIds)
                        },
                    )
                    tab == InkShelf2Tab.LIBRARY -> InkShelf2LibraryScreen(
                        library = library,
                        padding = padding,
                        onOpen = openComicDetails,
                        onComicAction = { manga, removeFromLibrary, deleteDownloads ->
                            manageComic(manga, removeFromLibrary, deleteDownloads)
                        },
                    )
                    tab == InkShelf2Tab.ACCOUNT && downloadsOpen -> InkShelf2DownloadsScreen(
                        padding = padding,
                        queued = downloadQueue,
                        saved = offlineSeries,
                        wifiRequired = downloadsWifiRequired,
                        paused = downloadsPaused,
                        onBack = { downloadsOpen = false },
                        onPause = { downloadManager.pauseDownloads() },
                        onResume = { resumeDownloads() },
                        onCancel = { entry ->
                            downloadManager.getChapterDownloadOrNull(entry.chapter)?.let {
                                downloadManager.deletePendingDownloads(it)
                            }
                        },
                        onOpen = openComicDetails,
                    )
                    tab == InkShelf2Tab.ACCOUNT && historyOpen -> InkShelf2HistoryScreen(
                        padding = padding,
                        history = readingHistory,
                        onBack = { historyOpen = false },
                        onOpen = openComicDetails,
                        onRead = { manga, chapter -> openChapter(manga, chapter) },
                    )
                    tab == InkShelf2Tab.ACCOUNT && metronOpen -> InkShelf2MenuMetronScreen(
                        padding = padding,
                        onBack = { metronOpen = false },
                    )
                    tab == InkShelf2Tab.ACCOUNT && themesOpen -> InkShelf2ThemesScreen(
                        padding = padding,
                        onBack = { themesOpen = false },
                    )
                    else -> InkShelf2MenuScreen(
                        padding = padding,
                        onBrowseSources = {
                            sourceHubMode = "Sources"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onExtensions = {
                            sourceHubMode = "Extensions"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onRepositories = {
                            sourceHubMode = "Repos"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onMetron = { metronOpen = true },
                        onThemes = { themesOpen = true },
                        profileName = profileName,
                        onProfile = {
                            welcomeStep = 0
                            welcomeOpen = true
                        },
                        onImportComics = {
                            startActivity(
                                Intent(this@InkShelf2Activity, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_LOCAL_COMIC_IMPORT),
                            )
                        },
                        onBackupTools = {
                            startActivity(
                                Intent(this@InkShelf2Activity, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_BACKUP_TOOLS),
                            )
                        },
                        onDownloads = {
                            historyOpen = false
                            downloadsOpen = true
                        },
                        onHistory = {
                            downloadsOpen = false
                            historyOpen = true
                        },
                        onSettings = {
                            startActivity(
                                Intent(this@InkShelf2Activity, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_MENU_SETTINGS),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SecureActivityDelegate.promptLockIfNeeded(this)
        refreshEpoch += 1
    }

    private fun openReader(manga: Manga) {
        if (readerOpenInProgress) return
        readerOpenInProgress = true
        lifecycleScope.launch {
            try {
                var local = manga
                var chapter = engine.nextChapter(local)
                if (chapter == null) {
                    // The first read must fetch the series' issue list, not
                    // require the user to visit the old J2K interface first.
                    val (resolved, fetched) = sourcesEngine.ensureIssues(manga)
                    local = resolved
                    if (fetched.isNotEmpty()) {
                        chapter = engine.nextChapter(local)
                    }
                }
                if (chapter != null) {
                    openChapter(local, chapter)
                } else {
                    Toast.makeText(
                        this@InkShelf2Activity,
                        "No issues available from this comic source",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (_: CancellationException) {
                // Activity was left while loading; no user-facing error needed.
            } catch (_: Exception) {
                Toast.makeText(
                    this@InkShelf2Activity,
                    "Could not load issues. Check the source and try again.",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                readerOpenInProgress = false
            }
        }
    }

    private fun openChapter(manga: Manga, chapter: Chapter) {
        startActivity(ReaderActivity.newIntent(this, manga, chapter))
    }

    /** Queue through InkShelf 1's existing source-backed offline download manager. */
    private fun downloadIssues(manga: Manga, chapters: List<Chapter>) {
        val eligible = chapters.filter { it.id != null }
        if (eligible.isEmpty()) {
            Toast.makeText(this, "No downloadable issues are loaded", Toast.LENGTH_SHORT).show()
            return
        }
        if (sourceManager.get(manga.source) !is HttpSource) {
            Toast.makeText(this, "This comic source cannot download issues", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                // Downloaded issues must remain reachable from the Library.
                val newlySaved = !manga.favorite
                if (newlySaved && !engine.addToLibrary(manga)) {
                    Toast.makeText(
                        this@InkShelf2Activity, "Could not add comic to your library", Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                // Restart failed jobs; simply re-queuing them is a no-op.
                val retries = eligible.filter {
                    downloadManager.getChapterDownloadOrNull(it)?.status == Download.State.ERROR
                }
                retries.forEach { downloadManager.startDownloadNow(it) }
                val toQueue = eligible.filter {
                    downloadManager.getChapterDownloadOrNull(it) == null &&
                        !downloadManager.isChapterDownloaded(it, manga)
                }
                if (toQueue.isNotEmpty()) downloadManager.downloadChapters(manga, toQueue)
                if (newlySaved) refreshEpoch += 1
                Toast.makeText(
                    this@InkShelf2Activity,
                    when {
                        retries.isNotEmpty() && toQueue.isEmpty() -> "Retrying downloads"
                        else -> "Added ${toQueue.size} issue${if (toQueue.size == 1) "" else "s"} to downloads"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                Toast.makeText(
                    this@InkShelf2Activity, "Unable to queue download", Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun resumeDownloads() {
        if (preferences.downloadOnlyOverWifi() && !applicationContext.isConnectedToWifi()) {
            Toast.makeText(this, "Waiting for Wi-Fi. Change Wi-Fi-only in Download settings to use mobile data.", Toast.LENGTH_LONG).show()
            return
        }
        if (downloadManager.hasQueue()) {
            DownloadJob.start(applicationContext)
            Toast.makeText(this, "Resuming downloads", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeOfflineIssue(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source) ?: return
        downloadManager.deleteChapters(listOf(chapter), manga, source, force = true)
        Toast.makeText(this, "Removing offline issue", Toast.LENGTH_SHORT).show()
    }

    private fun manageComic(manga: Manga, removeFromLibrary: Boolean, deleteDownloads: Boolean) {
        lifecycleScope.launch {
            try {
                if (deleteDownloads) {
                    val source = sourceManager.get(manga.source)
                    if (source == null) {
                        Toast.makeText(this@InkShelf2Activity, "Comic source unavailable; downloads unchanged", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    withContext(Dispatchers.IO) { downloadManager.deleteManga(manga, source) }
                }
                if (removeFromLibrary && !engine.removeFromLibrary(manga)) {
                    Toast.makeText(this@InkShelf2Activity, "Could not remove comic from Library", Toast.LENGTH_LONG).show()
                    return@launch
                }
                refreshEpoch += 1
                Toast.makeText(
                    this@InkShelf2Activity,
                    when {
                        removeFromLibrary && deleteDownloads -> "Removed from Library and deleted offline issues"
                        removeFromLibrary -> "Removed from Library; offline files kept"
                        else -> "Offline issues deleted; comic kept in Library"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                Toast.makeText(this@InkShelf2Activity, "Could not update comic", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveComic(manga: Manga) {
        lifecycleScope.launch {
            val saved = engine.addToLibrary(manga)
            if (saved) refreshEpoch += 1
            Toast.makeText(
                this@InkShelf2Activity,
                if (saved) "Saved to library" else "Could not save comic",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
