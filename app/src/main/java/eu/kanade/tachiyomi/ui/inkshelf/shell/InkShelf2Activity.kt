package eu.kanade.tachiyomi.ui.inkshelf.shell

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
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverRefreshWorker
import kotlinx.coroutines.launch
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
    private val sourceManager: SourceManager by lazy { Injekt.get() }
    private val extensionManager: ExtensionManager by lazy { Injekt.get() }
    private val preferences: PreferencesHelper by lazy { Injekt.get() }
    private var refreshEpoch by mutableIntStateOf(0)
    private var lastBackAtMillis = 0L
    private var readerOpenInProgress by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureActivityDelegate.setSecure(this)
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
            // Persist visited main tabs rather than relying on Activity.finish().
            // Store only enum names, which are safe for Android saved state.
            var tabTrail by rememberSaveable { mutableStateOf("HOME") }
            var searchFocusEpoch by remember { mutableIntStateOf(0) }
            var discoverSearchPreset by remember { mutableStateOf("") }
            var sourceHubOpen by rememberSaveable { mutableStateOf(false) }
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
            // MangaImpl.equals compares only URL and source, so an updated
            // database row may be 'equal' despite having new synopsis/cover
            // metadata. Use identity changes to refresh the Compose details.
            var selected by remember {
                mutableStateOf<Manga?>(null, referentialEqualityPolicy())
            }
            var home by remember { mutableStateOf<InkShelf2Engine.Home?>(null) }
            var library by remember { mutableStateOf<List<InkShelf2LibraryItem>>(emptyList()) }
            var discover by remember { mutableStateOf<InkShelf2Engine.Discover?>(null) }
            var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
            var loadingIssues by remember { mutableStateOf(false) }
            var issuesError by remember { mutableStateOf<String?>(null) }
            var sourceLinkError by remember { mutableStateOf<String?>(null) }
            var detailsRevision by remember { mutableIntStateOf(0) }

            LaunchedEffect(refreshEpoch) {
                // Read existing data; never reset or migrate the library.
                home = engine.loadHome()
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
                sourceLinkError = null
                issuesError = null
                loadingIssues = true
                selected = manga
            }

            // Register at the shell root, so Back always navigates inside
            // InkShelf 2.0 (including Library, Account and Discover), rather
            // than immediately finishing the preview Activity on those tabs.
            BackHandler(enabled = true) {
                when {
                    selected != null -> selected = null
                    selectedDiscoverShelf != null -> selectedDiscoverShelf = null
                    selectedDay != null -> selectedDay = null
                    activeSourceId != null -> activeSourceId = null
                    sourceHubOpen -> sourceHubOpen = false
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
                showHeader = selected == null,
                showBottomNav = selected == null,
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
                when {
                    comic != null -> key(detailsRevision, comic.source, comic.url) { InkShelf2DetailsScreen(
                        manga = comic,
                        chapters = chapters,
                        padding = padding,
                        onBack = { selected = null },
                        onRead = { openReader(comic) },
                        sourceName = sourceManager.get(comic.source)?.name,
                        sourceLinkError = sourceLinkError,
                        onFindSource = {
                            val title = comic.title
                            selected = null
                            sourceLinkError = null
                            openTab(InkShelf2Tab.DISCOVER)
                            discoverSearchPreset = title
                            searchFocusEpoch += 1
                        },
                        isOpeningReader = readerOpenInProgress,
                        isLoadingIssues = loadingIssues,
                        issuesError = issuesError,
                        onChapter = { openChapter(comic, it) },
                        onSave = { saveComic(comic) },
                    ) }
                    selectedDiscoverShelf != null -> InkShelf2DiscoverShelfScreen(
                        title = selectedDiscoverShelf!!,
                        comics = when (selectedDiscoverShelf) {
                            "New This Week" -> discover?.newest.orEmpty()
                            else -> discover?.trending.orEmpty()
                        },
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
                    tab == InkShelf2Tab.HOME -> InkShelf2HomeScreen(
                        home = home,
                        padding = padding,
                        onOpen = openComicDetails,
                        onRead = { openReader(it) },
                        onSave = { saveComic(it) },
                        onDiscover = { openTab(InkShelf2Tab.DISCOVER) },
                        onLibrary = { openTab(InkShelf2Tab.LIBRARY) },
                        onDayPicks = { selectedDay = it },
                        onTrendingSeeAll = { selectedDiscoverShelf = "Trending Right Now" },
                    )
                    tab == InkShelf2Tab.DISCOVER && sourceHubOpen -> InkShelf2SourceHub(
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
                        padding = padding,
                        onOpen = openComicDetails,
                        sources = enabledSources,
                        onOpenSearchResult = openComicDetails,
                        searchFocusToken = searchFocusEpoch,
                        initialSearchQuery = discoverSearchPreset,
                        onManageSources = {
                            sourceHubMode = "Sources"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onManageExtensions = {
                            sourceHubMode = "Extensions"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onManageRepositories = {
                            sourceHubMode = "Repos"
                            sourceHubOpen = true
                            activeSourceId = null
                        },
                        onSeeAll = { selectedDiscoverShelf = it },
                    )
                    tab == InkShelf2Tab.LIBRARY -> InkShelf2LibraryScreen(
                        library = library,
                        padding = padding,
                        onOpen = openComicDetails,
                    )
                    else -> InkShelf2AccountScreen(padding)
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
