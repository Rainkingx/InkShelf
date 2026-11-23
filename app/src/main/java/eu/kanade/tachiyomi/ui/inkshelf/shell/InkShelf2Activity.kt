package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Intent
import android.app.SearchManager
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.more.AboutController
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
 * The single InkShelf launcher. The original reader and domain services
 * remain shared with existing app data and Android background integrations.
 */
class InkShelf2Activity : AppCompatActivity() {
    companion object {
        const val ACTION_SHOW_DOWNLOADS = "eu.kanade.tachiyomi.inkshelf2.SHOW_DOWNLOADS"
        const val ACTION_SHOW_MANGA = "eu.kanade.tachiyomi.inkshelf2.SHOW_MANGA"
        const val ACTION_SHOW_EXTENSIONS = "eu.kanade.tachiyomi.inkshelf2.SHOW_EXTENSIONS"
        const val ACTION_SHOW_LIBRARY = "eu.kanade.tachiyomi.inkshelf2.SHOW_LIBRARY"
        const val ACTION_SHOW_UPDATES = "eu.kanade.tachiyomi.inkshelf2.SHOW_UPDATES"
        const val ACTION_SHOW_APP_UPDATE = "eu.kanade.tachiyomi.inkshelf2.APP_UPDATE"
    }
    private var pendingEntryIntent by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingEntryIntent = intent
    }
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
    private var guideIssueOpenInProgress = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingEntryIntent = intent
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
            val openedFromDownloadNotice = intent.action == ACTION_SHOW_DOWNLOADS
            var tab by rememberSaveable {
                mutableStateOf(if (openedFromDownloadNotice) InkShelf2Tab.ACCOUNT else InkShelf2Tab.HOME)
            }
            var profileName by remember { mutableStateOf(profileStore.displayName()) }
            var welcomeOpen by rememberSaveable {
                mutableStateOf(!profileStore.hasCompletedWelcome())
            }
            var welcomeStep by rememberSaveable { mutableIntStateOf(0) }
            // Persist visited main tabs rather than relying on Activity.finish().
            // Store only enum names, which are safe for Android saved state.
            var tabTrail by rememberSaveable {
                mutableStateOf(if (openedFromDownloadNotice) "ACCOUNT" else "HOME")
            }
            var searchFocusEpoch by remember { mutableIntStateOf(0) }
            var discoverSearchPreset by remember { mutableStateOf("") }
            var guideIssueSearch by remember { mutableStateOf<InkShelf2GuideIssue?>(null) }
            var guideIssuePicker by remember { mutableStateOf<InkShelf2GuideIssue?>(null) }
            // Start a fresh search whenever Discover is left; guide and
            // Find Source entries can still supply a new explicit query.
            var discoverSearchSession by remember { mutableIntStateOf(0) }
            fun clearDiscoverSearch() {
                discoverSearchPreset = ""
                guideIssueSearch = null
                searchFocusEpoch = 0
                discoverSearchSession += 1
            }
            var sourceHubOpen by rememberSaveable { mutableStateOf(false) }
            var metronOpen by rememberSaveable { mutableStateOf(false) }
            var themesOpen by rememberSaveable { mutableStateOf(false) }
            var downloadsOpen by rememberSaveable { mutableStateOf(openedFromDownloadNotice) }
            var historyOpen by rememberSaveable { mutableStateOf(false) }
            var updatesOpen by rememberSaveable { mutableStateOf(false) }
            var backupOpen by rememberSaveable { mutableStateOf(false) }
            var importOpen by rememberSaveable { mutableStateOf(false) }
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            var notificationsOpen by rememberSaveable { mutableStateOf(false) }
            var sourceHubMode by rememberSaveable { mutableStateOf("Sources") }
            var updateAllExtensionsRequested by remember { mutableStateOf(false) }
            var externalRepoDraft by rememberSaveable { mutableStateOf("") }
            var updateNotes by remember { mutableStateOf<Pair<String, String>?>(null) }
            var activeSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
            var selectedDay by remember { mutableStateOf<InkShelf2Engine.Day?>(null) }
            var selectedDiscoverShelf by rememberSaveable { mutableStateOf<String?>(null) }
            // Owned by the shell so reselecting the already-active Home tab
            // closes The Pulse instead of being ignored by bottom navigation.
            var pulseExpanded by rememberSaveable { mutableStateOf(false) }
            fun openTab(destination: InkShelf2Tab) {
                if (tab == InkShelf2Tab.GUIDES && destination != InkShelf2Tab.GUIDES) {
                    guideIssuePicker = null
                }
                if (tab == InkShelf2Tab.DISCOVER && destination != InkShelf2Tab.DISCOVER) {
                    clearDiscoverSearch()
                }
                if (tab != destination) {
                    tabTrail = (tabTrail.split('|').takeLast(20) + destination.name)
                        .joinToString("|")
                    tab = destination
                }
                sourceHubOpen = false
                updateAllExtensionsRequested = false
                metronOpen = false
                themesOpen = false
                downloadsOpen = false
                historyOpen = false
                updatesOpen = false
                backupOpen = false
                importOpen = false
                settingsOpen = false
                notificationsOpen = false
                activeSourceId = null
                selectedDay = null
                selectedDiscoverShelf = null
                pulseExpanded = false
            }
            val installedExtensions by extensionManager.installedExtensionsFlow.collectAsState()
            val availableExtensions by extensionManager.availableExtensionsFlow.collectAsState()
            val untrustedExtensions by extensionManager.untrustedExtensionsFlow.collectAsState()
            val pausedExtensionPackages = preferences.inkShelf2PausedExtensions().get()
            val extensionUpdates = installedExtensions
                .filterNot { it.pkgName in pausedExtensionPackages }
                .mapNotNull { installed ->
                availableExtensions.firstOrNull { available ->
                    available.pkgName == installed.pkgName &&
                        (available.versionCode > installed.versionCode || available.libVersion > installed.libVersion)
                }?.let { installed to it }
            }.sortedBy { it.first.name.lowercase() }
            var checkingExtensionUpdates by remember { mutableStateOf(false) }
            val notificationScope = rememberCoroutineScope()
            // Populate the 2.0 bell when the app starts; use the existing
            // extension repository manager, not a duplicate HTTP client.
            LaunchedEffect(Unit) {
                if (preferences.extensionRepos().get().isNotEmpty()) {
                    checkingExtensionUpdates = true
                    try {
                        extensionManager.findAvailableExtensions()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        // An unavailable repository should not block Home.
                    } finally {
                        checkingExtensionUpdates = false
                    }
                }
            }
            val catalogueSources by sourceManager.catalogueSources.collectAsState(initial = emptyList())
            val enabledLanguages = preferences.enabledLanguages().get()
            val hiddenSources = preferences.hiddenSources().get()
            val pausedIds = InkShelf2ExtensionPause.pausedSourceIds()
            val enabledSources = catalogueSources.filter {
                it.id != LocalSource.ID && it.lang in enabledLanguages &&
                    it.id.toString() !in hiddenSources && it.id !in pausedIds
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
            var recentIssueUpdates by remember { mutableStateOf<List<InkShelf2RecentUpdate>>(emptyList()) }

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
            LaunchedEffect(updatesOpen, refreshEpoch) {
                if (updatesOpen) recentIssueUpdates = engine.recentUpdates()
            }

            LaunchedEffect(refreshEpoch, enabledSourceIds) {
                // Wait for real installed-source registration. A source
                // appearing or disappearing updates the themed picks without
                // opening any extra external searches.
                try {
                    home = engine.loadHome(enabledSourceIds)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    android.util.Log.w("InkShelf2", "Home refresh failed", error)
                }
                try {
                    library = engine.loadLibrary()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    android.util.Log.w("InkShelf2", "Library refresh failed", error)
                }
                try {
                    discover = engine.loadDiscover()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    android.util.Log.w("InkShelf2", "Discover refresh failed", error)
                }
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

                val cached = try {
                    engine.chapters(comic)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    android.util.Log.w("InkShelf2", "Cached issues unavailable", error)
                    issuesError = "This comic's issues could not be loaded. Please try reopening it."
                    loadingIssues = false
                    return@LaunchedEffect
                }
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
                // Opening details leaves Discover even though its tab stays
                // selected underneath. Back must not restore stale results.
                if (tab == InkShelf2Tab.DISCOVER) clearDiscoverSearch()
                comicBackStack = emptyList()
                sourceLinkError = null
                issuesError = null
                loadingIssues = true
                selected = manga
            }
            // All external intents navigate into 2.0; no old Conductor screen is mounted.
            LaunchedEffect(pendingEntryIntent) {
                val entry = pendingEntryIntent ?: return@LaunchedEffect
                val action = entry.action
                if (action != null && action != Intent.ACTION_MAIN) {
                    welcomeOpen = false // Leave setup available next time.
                }
                val notificationId = entry.getIntExtra("notificationId", -1)
                if (notificationId >= 0) {
                    NotificationReceiver.dismissNotification(
                        applicationContext, notificationId, entry.getIntExtra("groupId", 0),
                    )
                }
                when (action) {
                    ACTION_SHOW_DOWNLOADS, MainActivity.SHORTCUT_DOWNLOADS -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        downloadsOpen = true
                    }
                    MainActivity.SHORTCUT_RECENTLY_READ -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        historyOpen = true
                    }
                    ACTION_SHOW_LIBRARY, MainActivity.SHORTCUT_LIBRARY -> openTab(InkShelf2Tab.LIBRARY)
                    ACTION_SHOW_UPDATES, MainActivity.SHORTCUT_RECENTLY_UPDATED -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        updatesOpen = true
                    }
                    MainActivity.SHORTCUT_RECENTS -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        historyOpen = true
                    }
                    ACTION_SHOW_EXTENSIONS, MainActivity.SHORTCUT_EXTENSIONS -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        sourceHubMode = "Extensions"
                        sourceHubOpen = true
                        updateAllExtensionsRequested = entry.getBooleanExtra(
                            MainActivity.EXTRA_UPDATE_ALL_EXTENSIONS, false,
                        )
                    }
                    MainActivity.SHORTCUT_SOURCE -> {
                        val sourceId = entry.getLongExtra(BrowseSourceController.SOURCE_ID_KEY, 0L)
                        if (sourceId > 0L) {
                            openTab(InkShelf2Tab.ACCOUNT)
                            sourceHubMode = "Sources"
                            sourceHubOpen = true
                            activeSourceId = sourceId
                        }
                    }
                    ACTION_SHOW_MANGA, MainActivity.SHORTCUT_MANGA, MainActivity.SHORTCUT_MANGA_BACK -> {
                        val mangaId = entry.getLongExtra(MangaDetailsController.MANGA_EXTRA, 0L)
                        val manga = engine.mangaById(mangaId)
                        if (manga != null) openComicDetails(manga)
                        else Toast.makeText(this@InkShelf2Activity, "Comic not found", Toast.LENGTH_SHORT).show()
                    }
                    ACTION_SHOW_APP_UPDATE, MainActivity.SHORTCUT_UPDATE_NOTES -> {
                        val body = entry.getStringExtra(AboutController.NewUpdateDialogController.BODY_KEY).orEmpty()
                        val downloadUrl = entry.getStringExtra(AboutController.NewUpdateDialogController.URL_KEY).orEmpty()
                        updateNotes = body.take(12_000) to downloadUrl
                    }
                    Intent.ACTION_SEARCH, "com.google.android.gms.actions.SEARCH_ACTION",
                    Intent.ACTION_SEND, MainActivity.INTENT_SEARCH -> {
                        openTab(InkShelf2Tab.DISCOVER)
                        discoverSearchPreset = (
                            entry.getStringExtra(SearchManager.QUERY)
                                ?: entry.getStringExtra(MainActivity.INTENT_SEARCH_QUERY)
                                ?: entry.getStringExtra(Intent.EXTRA_TEXT)
                        ).orEmpty().take(300)
                        searchFocusEpoch += 1
                    }
                    MainActivity.SHORTCUT_BROWSE -> openTab(InkShelf2Tab.DISCOVER)
                    MainActivity.SHORTCUT_READER_SETTINGS, MainActivity.SHORTCUT_MENU_SETTINGS,
                    Intent.ACTION_APPLICATION_PREFERENCES -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        settingsOpen = true
                    }
                    MainActivity.SHORTCUT_BACKUP_TOOLS -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        backupOpen = true
                    }
                    MainActivity.SHORTCUT_LOCAL_COMIC_IMPORT -> {
                        openTab(InkShelf2Tab.ACCOUNT)
                        importOpen = true
                    }
                    Intent.ACTION_VIEW -> {
                        val link = entry.data
                        if ((link?.scheme == "tachiyomi" && link.host == "add-repo") ||
                            (link?.scheme == "mihon" && link.host == "extension-store")
                        ) {
                            val candidate = link?.getQueryParameter("url").orEmpty().take(1024)
                            val url = Uri.parse(candidate)
                            if (url.scheme == "https" && !url.host.isNullOrBlank()) {
                                openTab(InkShelf2Tab.ACCOUNT)
                                externalRepoDraft = candidate
                                sourceHubMode = "Repos"
                                sourceHubOpen = true
                            }
                        }
                    }
                }
                // Do not invalidate the effect while a manga lookup is suspended.
                if (pendingEntryIntent === entry) pendingEntryIntent = null
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
                    notificationsOpen -> notificationsOpen = false
                    selectedDiscoverShelf != null -> selectedDiscoverShelf = null
                    selectedDay != null -> selectedDay = null
                    activeSourceId != null -> activeSourceId = null
                    sourceHubOpen -> {
                        updateAllExtensionsRequested = false
                        sourceHubOpen = false
                    }
                    metronOpen -> metronOpen = false
                    themesOpen -> themesOpen = false
                    downloadsOpen -> downloadsOpen = false
                    historyOpen -> historyOpen = false
                    updatesOpen -> updatesOpen = false
                    backupOpen -> backupOpen = false
                    importOpen -> importOpen = false
                    settingsOpen -> settingsOpen = false
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
            updateNotes?.let { (notes, url) ->
                val parsed = Uri.parse(url)
                val isTrustedApk = parsed.scheme == "https" &&
                    parsed.host.equals("github.com", ignoreCase = true) &&
                    parsed.path?.startsWith("/Rainkingx/InkShelf/releases/download/") == true &&
                    parsed.path?.endsWith(".apk", ignoreCase = true) == true
                AlertDialog(
                    onDismissRequest = { updateNotes = null },
                    title = { Text("InkShelf Update") },
                    text = {
                        Text(
                            if (notes.isBlank()) "A new version of InkShelf is available."
                            else notes.take(2_500),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = isTrustedApk,
                            onClick = {
                                updateNotes = null
                                AppDownloadInstallJob.start(
                                    this@InkShelf2Activity, url, notifyOnInstall = false,
                                )
                            },
                        ) { Text("Download & Install") }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateNotes = null }) { Text("Later") }
                    },
                )
            }
            InkShelf2Shell(
                currentTab = tab,
                showHeader = selected == null && !welcomeOpen,
                showBottomNav = selected == null && !welcomeOpen,
                onTabSelected = {
                    if (it == InkShelf2Tab.DISCOVER) guideIssueSearch = null
                    openTab(it)
                },
                onSearch = {
                    // Header search always starts a genuinely new search.
                    clearDiscoverSearch()
                    openTab(InkShelf2Tab.DISCOVER)
                    searchFocusEpoch += 1
                },
                onNotifications = { notificationsOpen = true },
                notificationCount = extensionUpdates.size,
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
                                    // Checking local chapter files can touch disk;
                                    // keep this work off the Compose/UI thread.
                                    val snapshot = chapters.toList()
                                    downloadedIds = withContext(Dispatchers.IO) {
                                        snapshot.filter {
                                            it.id != null && downloadManager.isChapterDownloaded(it, comic)
                                        }.mapNotNull { it.id }.toSet()
                                    }
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
                                    // Small delay keeps queue progress responsive
                                    // without scanning every issue every second.
                                    delay(2_500)
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
                    notificationsOpen -> InkShelf2NotificationsScreen(
                        padding = padding,
                        updates = extensionUpdates,
                        checking = checkingExtensionUpdates,
                        hasRepositories = preferences.extensionRepos().get().isNotEmpty(),
                        onBack = { notificationsOpen = false },
                        onExtensions = {
                            openTab(InkShelf2Tab.ACCOUNT)
                            sourceHubMode = "Extensions"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onRefresh = {
                            if (!checkingExtensionUpdates) {
                                notificationScope.launch {
                                    checkingExtensionUpdates = true
                                    try {
                                        extensionManager.findAvailableExtensions()
                                    } catch (cancel: CancellationException) {
                                        throw cancel
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            this@InkShelf2Activity,
                                            "Could not check extensions. Try again later.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        checkingExtensionUpdates = false
                                    }
                                }
                            }
                        },
                    )
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
                            (!sourceHubOpen && !metronOpen && !themesOpen && !backupOpen && !importOpen)) ->
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
                        onSourcesChanged = { refreshEpoch += 1 },
                        onBackupTools = {
                            openTab(InkShelf2Tab.ACCOUNT)
                            backupOpen = true
                        },
                        onImportComics = {
                            openTab(InkShelf2Tab.ACCOUNT)
                            importOpen = true
                        },
                    )
                    tab == InkShelf2Tab.HOME -> InkShelf2HomeScreen(
                        home = home,
                        library = library,
                        profileName = profileName,
                        padding = padding,
                        pulseExpanded = pulseExpanded,
                        onPulseExpandedChange = { pulseExpanded = it },
                        onOpen = openComicDetails,
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
                        initialRepoUrl = externalRepoDraft,
                        updateAllOnOpen = updateAllExtensionsRequested,
                        onUpdateAllHandled = { updateAllExtensionsRequested = false },
                        onExtensionStateChanged = { refreshEpoch += 1 },
                        onOpenComic = openComicDetails,
                    )
                    tab == InkShelf2Tab.DISCOVER -> InkShelf2DiscoverScreen(
                        searchSession = discoverSearchSession,
                        discover = discover,
                        curatedShelves = curatedShelves,
                        padding = padding,
                        onOpen = openComicDetails,
                        sources = enabledSources,
                        onOpenSearchResult = { candidate ->
                            val issue = guideIssueSearch
                            if (issue != null &&
                                InkShelf2GuideEdition.classify(issue, candidate.title) !=
                                InkShelf2GuideEdition.Match.MATCHING
                            ) {
                                Toast.makeText(
                                    this@InkShelf2Activity,
                                    "This edition is not verified for " + issue.displayName +
                                        ". Check the year and issue list before reading.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            // Always use the established InkShelf 2.0 details
                            // route. Selecting a series must never launch its
                            // reader directly or add a second library favourite.
                            guideIssueSearch = null
                            openComicDetails(candidate)
                        },
                        onAutoOpenGuideMatch = { candidate, issue ->
                            if (!guideIssueOpenInProgress) {
                                guideIssueOpenInProgress = true
                                lifecycleScope.launch {
                                    try {
                                        // A matching title/year is not enough:
                                        // the requested issue must exist in this
                                        // extension's real chapter list.
                                        val (resolved, chapters) = sourcesEngine.ensureIssues(candidate)
                                        if (InkShelf2GuideChapterFinder.find(issue, chapters) != null) {
                                            guideIssueSearch = null
                                            openComicDetails(resolved)
                                        } else {
                                            Toast.makeText(
                                                this@InkShelf2Activity,
                                                "The series matches, but issue #" + issue.number +
                                                    " was not found. Choose a source below.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    } catch (cancel: CancellationException) {
                                        throw cancel
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            this@InkShelf2Activity,
                                            "Couldn't check this source's issue list. Select a result to inspect it.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } finally {
                                        guideIssueOpenInProgress = false
                                    }
                                }
                            }
                        },
                        searchFocusToken = searchFocusEpoch,
                        initialSearchQuery = discoverSearchPreset,
                        guideIssueTarget = guideIssueSearch,
                        onClearGuideTarget = { guideIssueSearch = null },
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
                    tab == InkShelf2Tab.GUIDES -> Box(modifier = Modifier.fillMaxSize()) {
                        // Keep the selected guide mounted beneath its
                        // dedicated issue finder. Back returns to the same
                        // reading order instead of dumping into Discover.
                        InkShelf2GuidesScreen(
                            padding = padding,
                            onMetronSettings = {
                                openTab(InkShelf2Tab.ACCOUNT)
                                metronOpen = true
                            },
                            onReadIssue = { requestedIssue ->
                                guideIssuePicker = requestedIssue
                            },
                        )
                        val requested = guideIssuePicker
                        if (requested != null) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(InkShelf2Colors.Surface),
                            ) {
                                InkShelf2GuideIssuePickerScreen(
                                    requestedIssue = requested,
                                    sources = enabledSources,
                                    padding = padding,
                                    onBack = { guideIssuePicker = null },
                                    onOpenComic = { comic ->
                                        guideIssuePicker = null
                                        openComicDetails(comic)
                                    },
                                )
                            }
                        }
                    }
                    tab == InkShelf2Tab.LIBRARY -> InkShelf2LibraryScreen(
                        library = library,
                        readingHistory = home?.history.orEmpty(),
                        padding = padding,
                        onRead = { openReader(it) },
                        onOpen = openComicDetails,
                        onComicAction = { manga, removeFromLibrary, deleteDownloads ->
                            manageComic(manga, removeFromLibrary, deleteDownloads)
                        },
                    )
                    tab == InkShelf2Tab.ACCOUNT && settingsOpen -> InkShelf2AdvancedSettingsScreen(
                        padding = padding,
                        onBack = { settingsOpen = false },
                        onThemes = { settingsOpen = false; themesOpen = true },
                        onDownloads = { settingsOpen = false; downloadsOpen = true },
                        onBackup = { settingsOpen = false; backupOpen = true },
                    )
                    tab == InkShelf2Tab.ACCOUNT && importOpen -> InkShelf2LocalImportScreen(
                        padding = padding,
                        onBack = { importOpen = false },
                        onImported = { refreshEpoch += 1 },
                    )
                    tab == InkShelf2Tab.ACCOUNT && backupOpen -> InkShelf2BackupScreen(
                        padding = padding,
                        onBack = { backupOpen = false },
                        onRestoreQueued = { backupOpen = false },
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
                    tab == InkShelf2Tab.ACCOUNT && updatesOpen -> InkShelf2RecentUpdatesScreen(
                        padding = padding,
                        updates = recentIssueUpdates,
                        onBack = { updatesOpen = false },
                        onOpen = openComicDetails,
                        onRead = { manga, chapter -> openChapter(manga, chapter) },
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
                        extensionUpdateCount = extensionUpdates.size,
                        profileName = profileName,
                        onProfile = {
                            welcomeStep = 0
                            welcomeOpen = true
                        },
                        onImportComics = { importOpen = true },
                        onBackupTools = { backupOpen = true },
                        onDownloads = {
                            historyOpen = false
                            downloadsOpen = true
                        },
                        onHistory = {
                            downloadsOpen = false
                            historyOpen = true
                        },
                        onSettings = { settingsOpen = true },
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
                if (newlySaved) {
                    val save = engine.addToLibrary(manga)
                    if (!save.saved && save.existing == null) {
                        Toast.makeText(
                            this@InkShelf2Activity, "Could not add comic to your library", Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                    // An alternate source can still be downloaded without
                    // becoming a second Library favourite.
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
            val result = engine.addToLibrary(manga)
            if (result.saved) refreshEpoch += 1
            Toast.makeText(
                this@InkShelf2Activity,
                when {
                    result.saved -> "Saved to library"
                    result.existing != null -> "Already in your library — no duplicate added"
                    else -> "Could not save comic"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
