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
import android.view.ViewTreeObserver
import coil.imageLoader
import coil.request.ImageRequest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.withTimeoutOrNull
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
        cinematicSplash?.dismiss()
        cinematicSplash = null
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
    // Read from Android's native splash condition on the UI thread. The splash
    // remains until the initial Home, Library and Discover snapshot is ready.
    @Volatile private var initialShellReady = false
    private var cinematicSplash: InkShelf2CinematicSplash? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialShellReady = false
        val splashStartedAt = SystemClock.elapsedRealtime()
        val firstProcessLaunch = InkShelf2CinematicSplash.claimColdStart()
        val motionAllowed = android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()
        val cinematic = firstProcessLaunch && savedInstanceState == null &&
            intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            motionAllowed && InkShelf2CinematicSplash.enabled(this)
        // Prevent the first Activity draw until initial Home content is
        // hydrated. On Android 12+ the system launch splash remains visible;
        // older Android keeps its normal starting window. No extra library or
        // simulated full-screen screen is needed.
        val splashRoot = window.decorView
        splashRoot.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val keepSplash = !cinematic && !initialShellReady &&
                        SystemClock.elapsedRealtime() - splashStartedAt < 8_000L
                    if (keepSplash) return false
                    if (splashRoot.viewTreeObserver.isAlive) {
                        splashRoot.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    return true
                }
            },
        )
        // The timeout needs a draw request even if a broken source never
        // finishes loading. Never leave the opening screen stuck forever.
        splashRoot.postDelayed({
            splashRoot.postInvalidateOnAnimation()
        }, 8_000L)
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
            var guideOpeningId by remember { mutableStateOf<Int?>(null) }
            var guideReadError by remember { mutableStateOf<String?>(null) }
            var savedGuideIds by remember { mutableStateOf(InkShelf2GuidesData.savedGuideIds(applicationContext)) }
            var libraryGuideId by rememberSaveable { mutableStateOf<Int?>(null) }
            var downloadingGuideId by remember { mutableStateOf<Int?>(null) }
            var guideCollectionPicker by remember {
                mutableStateOf<InkShelf2GuideCollections.Collection?>(null)
            }
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
            var pulseReturnToTab by rememberSaveable { mutableStateOf("") }
            var pulseHasNewStories by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                // A cached headline can light the dot even when launched into
                // Library/Discover. Home's feed callback updates it on refresh.
                pulseHasNewStories = InkShelf2PulseStatus.hasUnread(
                    applicationContext, InkShelf2PulseFeed.cached(applicationContext),
                )
            }
            fun openTab(destination: InkShelf2Tab) {
                val visibleDestination = destination
                if (tab == InkShelf2Tab.GUIDES) {
                    libraryGuideId = null
                    guideIssuePicker = null
                    guideCollectionPicker = null
                }
                if (tab == InkShelf2Tab.DISCOVER && visibleDestination != InkShelf2Tab.DISCOVER) {
                    clearDiscoverSearch()
                }
                if (tab != visibleDestination) {
                    tabTrail = (tabTrail.split('|')
                        .takeLast(20) + visibleDestination.name).joinToString("|")
                    tab = visibleDestination
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
            fun openPulse() {
                // The shortcut is a route to the existing full Pulse screen,
                // not a fifth tab or a second/duplicated feed.
                pulseReturnToTab = if (tab == InkShelf2Tab.HOME) "" else tab.name
                openTab(InkShelf2Tab.HOME)
                pulseExpanded = true
                InkShelf2PulseStatus.markViewed(applicationContext)
                pulseHasNewStories = false
            }
            fun closePulse() {
                pulseExpanded = false
                val previous = InkShelf2Tab.entries.firstOrNull {
                    it.name == pulseReturnToTab
                }
                pulseReturnToTab = ""
                if (previous != null) openTab(previous)
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
            // A result is emitted only by the event end-of-issue button.
            var eventAdvance by rememberSaveable { mutableStateOf<String?>(null) }
            val eventReader = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                if (result.resultCode == RESULT_OK && data != null && data.hasExtra(ReaderActivity.EVENT_GUIDE_ID)) {
                    eventAdvance = "${data.getIntExtra(ReaderActivity.EVENT_GUIDE_ID, 0)}:" +
                        "${data.getIntExtra(ReaderActivity.EVENT_ISSUE_ID, 0)}:" +
                        data.getBooleanExtra(ReaderActivity.EVENT_ESSENTIAL, true).toString()
                }
                refreshEpoch += 1
            }
            fun openGuideIssue(guideId: Int, requestedIssue: InkShelf2GuideIssue, essentialMode: Boolean) {
                if (guideOpeningId == null) {
                    guideOpeningId = requestedIssue.id
                    guideReadError = null
                    lifecycleScope.launch {
                        try {
                            var matchDiagnostic = ""
                            val local = try {
                                val guide = InkShelf2PersonalEvents.catalogue(applicationContext).firstOrNull { it.id == guideId }
                                if (guide == null) null else InkShelf2GuideProgress.load(applicationContext, listOf(guide))[guideId]
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) { null }
                            val savedTarget = local?.let {
                                InkShelf2GuideProgress.resumeTarget(applicationContext, guideId, requestedIssue.id, it,
                                    if (requestedIssue.explicitSource == LocalSource.ID) enabledSourceIds + LocalSource.ID else enabledSourceIds)
                            }
                            val offlineTarget = local?.let {
                                try {
                                    InkShelf2GuideDownloads.offlineTarget(it, requestedIssue.id, downloadManager)
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) { null }
                            }
                            // The pinned source owns the saved page; offline duplicates are fallback only.
                            val resume = savedTarget ?: offlineTarget
                            val reading = resume?.let { InkShelf2GuideAutoReader.Resolved(it.manga, it.chapter) }
                                ?: if (requestedIssue.explicitSource != null) null else InkShelf2GuideAutoReader.resolve(
                                applicationContext,
                                sourcesEngine, enabledSources, requestedIssue,
                                onDiagnostic = { matchDiagnostic = it },
                            )
                            val localFallback = if (reading == null && !requestedIssue.sourceUnavailable) {
                                local?.issues?.get(requestedIssue.id)?.targets.orEmpty()
                                    .filter { it.manga.source in enabledSourceIds && it.chapter.id != null }
                                    .distinctBy { it.chapter.id }.singleOrNull()
                            } else null
                            val resolvedReading = reading ?: localFallback?.let {
                                InkShelf2GuideAutoReader.Resolved(it.manga, it.chapter)
                            }
                            if (resolvedReading != null) {
                                InkShelf2GuidesData.start(
                                    applicationContext, guideId, requestedIssue.id,
                                )
                                val mangaId = resolvedReading.manga.id
                                val chapterId = resolvedReading.chapter.id
                                if (mangaId != null && chapterId != null) {
                                    InkShelf2GuidesData.rememberChapter(
                                        applicationContext, guideId, requestedIssue.id, mangaId, chapterId,
                                    )
                                }
                                eventReader.launch(ReaderActivity.newIntent(this@InkShelf2Activity, resolvedReading.manga, resolvedReading.chapter).apply {
                                    putExtra(ReaderActivity.EVENT_GUIDE_ID, guideId)
                                    putExtra(ReaderActivity.EVENT_ISSUE_ID, requestedIssue.id)
                                    putExtra(ReaderActivity.EVENT_ESSENTIAL, essentialMode)
                                })
                            } else {
                                guideReadError =
                                    "Could not match " + requestedIssue.displayName +
                                    " to a readable issue in the installed extensions. " +
                                    "The guide order is unchanged." +
                                    if (matchDiagnostic.isBlank()) "" else "\n" + matchDiagnostic
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            guideReadError =
                                "Could not open this issue from your sources. Try again later."
                        } finally {
                            guideOpeningId = null
                        }
                    }
                }
            }
            LaunchedEffect(eventAdvance) {
                val advance = eventAdvance ?: return@LaunchedEffect
                val fields = advance.split(':')
                val guideId = fields.getOrNull(0)?.toIntOrNull() ?: return@LaunchedEffect
                val issueId = fields.getOrNull(1)?.toIntOrNull() ?: return@LaunchedEffect
                val essential = fields.getOrNull(2) == "true"
                val guide = InkShelf2PersonalEvents.catalogue(applicationContext).firstOrNull { it.id == guideId } ?: return@LaunchedEffect
                val all = InkShelf2PersonalEvents.order(applicationContext, guide)
                val order = InkShelf2GuideReadingSelection.issuesForMode(all, essential)
                val state = try {
                    InkShelf2GuideProgress.load(applicationContext, listOf(guide))[guideId] ?: InkShelf2GuideProgress.EventState()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) { InkShelf2GuideProgress.EventState() }
                val read = InkShelf2GuideProgress.effectiveRead(applicationContext, guideId, state)
                val next = InkShelf2GuideReadingSelection.after(order, issueId, read)
                InkShelf2GuidesData.setEssentialMode(applicationContext, guideId, essential)
                if (next != null) openGuideIssue(guideId, next, essential)
                else Toast.makeText(this@InkShelf2Activity, "End of selected reading order", Toast.LENGTH_SHORT).show()
                // Clearing the effect key before the suspended database read would cancel the advance.
                eventAdvance = null
            }

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
                // Stage a complete startup snapshot before publishing it.
                // Previously Home painted its heading while the database and
                // Discover source cache were still loading, causing a jump.
                val refreshedHome = try {
                    engine.loadHome(enabledSourceIds)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    android.util.Log.w("InkShelf2", "Home refresh failed", error)
                    null
                }
                val refreshedLibrary = try {
                    engine.loadLibrary()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    android.util.Log.w("InkShelf2", "Library refresh failed", error)
                    null
                }
                val refreshedDiscover = try {
                    engine.loadDiscover(enabledSourceIds)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    android.util.Log.w("InkShelf2", "Discover refresh failed", error)
                    null
                }

                if (refreshedHome != null) home = refreshedHome
                if (refreshedLibrary != null) library = refreshedLibrary
                if (refreshedDiscover != null) discover = refreshedDiscover

                if (!initialShellReady) {
                    // Warm today's first visible artwork so we don't replace
                    // an empty hero with a cover moments after the splash.
                    // Cached images return quickly; network loads have a short
                    // timeout and must never delay entry indefinitely.
                    val todaysHeroUrl = refreshedHome?.days?.firstOrNull {
                        it.calendarDay == java.util.Calendar.getInstance()
                            .get(java.util.Calendar.DAY_OF_WEEK)
                    }?.artwork?.imageUrl
                    if (!todaysHeroUrl.isNullOrBlank()) {
                        withTimeoutOrNull(2_500L) {
                            withContext(Dispatchers.IO) {
                                applicationContext.imageLoader.execute(
                                    ImageRequest.Builder(applicationContext)
                                        .data(todaysHeroUrl)
                                        .build(),
                                )
                            }
                        }
                    }
                    // Home's first hero has a 620ms entrance animation.
                    // Allow it to finish behind the branded splash instead
                    // of revealing the heading while the hero fades in.
                    withFrameNanos { }
                    if (refreshedHome != null && tab == InkShelf2Tab.HOME && !welcomeOpen) {
                        delay(700L)
                    }
                    withFrameNanos { }
                    initialShellReady = true
                    // A plain readiness flag does not recompose on its own.
                    // Schedule the frame that releases the pre-draw gate.
                    window.decorView.postInvalidateOnAnimation()
                }

                // An extension pause can leave every cached Discover card
                // pointing at XOXO. Rebuild missing shelves from the other
                // enabled extensions AFTER the first UI frame; never delay the
                // splash, hijack a saved comic or create library favourites.
                if (refreshedDiscover != null && enabledSourceIds.isNotEmpty()) {
                    val missing = InkShelf2RecommendationRecovery.missing(
                        refreshedDiscover, enabledSourceIds,
                    )
                    if (InkShelf2RecommendationRecovery.claimAttempt(
                            applicationContext, enabledSourceIds, missing,
                        )
                    ) {
                        try {
                            InkShelf2RecommendationRecovery.refresh(applicationContext, missing)
                            // The network can fail independently per source; use
                            // whatever successful cache entries were recovered.
                            discover = engine.loadDiscover(enabledSourceIds)
                            home = engine.loadHome(enabledSourceIds)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            android.util.Log.w("InkShelf2", "Recommendation source recovery failed", error)
                        }
                    }
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
                    cinematicSplash?.parent != null -> {
                        cinematicSplash?.dismiss()
                        cinematicSplash = null
                    }
                    selected != null -> backFromComicDetails()
                    pulseExpanded -> closePulse()
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
                        val backTrail = tabTrail.substringBeforeLast('|')
                        tabTrail = backTrail.ifBlank { InkShelf2Tab.HOME.name }
                        tab = InkShelf2Tab.entries.firstOrNull {
                            it.name == tabTrail.substringAfterLast('|')
                        } ?: InkShelf2Tab.HOME
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
                // Comic details keeps the same four-tab bar. The original
                // full-screen reader still runs in its separate Activity.
                showBottomNav = !welcomeOpen,
                onTabSelected = { destination ->
                    if (selected != null) {
                        // A tab switch must dismiss the details route; merely
                        // changing tab underneath would leave Details on top.
                        selected = null
                        comicBackStack = emptyList()
                        sourceLinkError = null
                        issuesError = null
                        loadingIssues = false
                    }
                    if (destination == InkShelf2Tab.DISCOVER) guideIssueSearch = null
                    openTab(destination)
                },
                onSearch = {
                    // Header search always starts a genuinely new search.
                    clearDiscoverSearch()
                    openTab(InkShelf2Tab.DISCOVER)
                    searchFocusEpoch += 1
                },
                onNotifications = { notificationsOpen = true },
                onPulse = { openPulse() },
                onGuides = { openTab(InkShelf2Tab.GUIDES) },
                guidesActive = tab == InkShelf2Tab.GUIDES,
                pulseActive = tab == InkShelf2Tab.HOME && pulseExpanded,
                pulseHasNewStories = pulseHasNewStories,
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
                        onEventChanged = { refreshEpoch += 1 },
                        onResetReading = { chapterId ->
                            lifecycleScope.launch {
                                try {
                                    val count = InkShelf2ReadingReset.comic(comic, chapterId)
                                    refreshEpoch += 1
                                    Toast.makeText(this@InkShelf2Activity, "Reset $count issue(s)", Toast.LENGTH_SHORT).show()
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    Toast.makeText(this@InkShelf2Activity, "Could not reset progress", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
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
                        onPulseExpandedChange = { expanded ->
                            if (expanded) openPulse() else closePulse()
                        },
                        onPulseArticlesUpdated = { articles ->
                            pulseHasNewStories = !pulseExpanded &&
                                InkShelf2PulseStatus.hasUnread(applicationContext, articles)
                        },
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
                            discover = engine.loadDiscover(enabledSourceIds)
                            home = engine.loadHome(enabledSourceIds)
                        },
                    )
                    tab == InkShelf2Tab.GUIDES -> InkShelf2GuidesScreen(
                        padding = padding,
                        onMetronSettings = {
                            openTab(InkShelf2Tab.ACCOUNT)
                            metronOpen = true
                        },
                        openingIssueId = guideOpeningId,
                        readingError = guideReadError,
                        refreshEpoch = refreshEpoch,
                        initialGuideId = libraryGuideId,
                        onCloseInitialGuide = { openTab(InkShelf2Tab.LIBRARY) },
                        savedGuideIds = savedGuideIds,
                        onSaveGuide = { guideId, save ->
                            savedGuideIds = InkShelf2GuidesData.setSaved(applicationContext, guideId, save)
                        },
                        enabledSourceIds = enabledSourceIds,
                        downloadingGuideId = downloadingGuideId,
                        onDownloadIssues = { guideId, issues ->
                            if (downloadingGuideId == null) {
                                downloadingGuideId = guideId
                                lifecycleScope.launch {
                                    try {
                                        val result = queueMatchedEventIssues(guideId, issues, enabledSourceIds)
                                        if (result > 0) {
                                            savedGuideIds = InkShelf2GuidesData.setSaved(applicationContext, guideId, true)
                                            refreshEpoch += 1
                                        }
                                        Toast.makeText(
                                            this@InkShelf2Activity,
                                            if (result > 0) "$result issue(s) queued or retried" else "No new matched issues available to queue",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } catch (cancel: CancellationException) {
                                        throw cancel
                                    } catch (_: Exception) {
                                        Toast.makeText(this@InkShelf2Activity, "Could not queue event issues", Toast.LENGTH_LONG).show()
                                    } finally {
                                        downloadingGuideId = null
                                    }
                                }
                            }
                        },
                        onReadIssue = { guideId, requestedIssue ->
                            openGuideIssue(guideId, requestedIssue, InkShelf2GuidesData.essentialMode(applicationContext, guideId))
                        },
                        // Guide details now resolve issues automatically. There
                        // is no user-facing collection/source search stage.
                        onFindCollection = { },
                    )
                    tab == InkShelf2Tab.LIBRARY -> InkShelf2LibraryScreen(
                        library = library,
                        savedGuides = InkShelf2PersonalEvents.catalogue(applicationContext).filter { it.id in savedGuideIds }
                            .map { it.copy(imageUrl = InkShelf2GuidesData.cachedEventCover(applicationContext, it)) },
                        onOpenGuide = { guideId ->
                            openTab(InkShelf2Tab.GUIDES)
                            libraryGuideId = guideId
                            guideReadError = null
                        },
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
                        onNotifications = { notificationsOpen = true },
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
        if (cinematic) {
            val splash = InkShelf2CinematicSplash(this, { initialShellReady }, splashStartedAt)
            cinematicSplash = splash
            addContentView(splash, android.view.ViewGroup.LayoutParams(-1, -1))
        }
    }

    override fun onDestroy() {
        cinematicSplash?.dismiss()
        cinematicSplash = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        InkShelf2Themes.refreshScheduledEvent(this)
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

    /** No remote matching sweep, synthetic manga, or automatic duplicate favourites. */
    private suspend fun queueMatchedEventIssues(
        guideId: Int,
        issues: List<InkShelf2GuideIssue>,
        enabledSourceIds: Set<Long>,
    ): Int {
        val guide = InkShelf2PersonalEvents.catalogue(applicationContext).firstOrNull { it.id == guideId } ?: return 0
        val progress = InkShelf2GuideProgress.load(applicationContext, listOf(guide))[guideId] ?: return 0
        downloadManager.awaitDownloadCacheReady()
        val chosen = withContext(Dispatchers.IO) {
            issues.filterNot { it.sourceUnavailable }.mapNotNull { issue ->
                val targets = progress.issues[issue.id]?.targets.orEmpty()
                if (targets.any { downloadManager.isChapterDownloaded(it.chapter, it.manga) }) return@mapNotNull null
                if (targets.any {
                    downloadManager.getChapterDownloadOrNull(it.chapter)?.status?.let { status ->
                        status != Download.State.ERROR && status != Download.State.NOT_DOWNLOADED
                    } == true
                }) return@mapNotNull null
                targets.firstOrNull {
                    InkShelf2GuideDownloads.eligible(it, sourceManager, enabledSourceIds)
                }?.let { issue.id to it }
            }.distinctBy { it.second.chapter.id }
        }
        var queued = 0
        chosen.groupBy { it.second.manga.id }.values.forEach { entries ->
            val manga = entries.first().second.manga
            val chapters = entries.map { it.second.chapter }
            val retries = chapters.filter { downloadManager.getChapterDownloadOrNull(it)?.status == Download.State.ERROR }
            retries.forEach { downloadManager.startDownloadNow(it) }
            val new = chapters.filter { downloadManager.getChapterDownloadOrNull(it) == null }
            if (new.isNotEmpty()) downloadManager.downloadChapters(manga, new)
            queued += retries.size + new.size
            entries.forEach { (issueId, target) ->
                val mangaId = target.manga.id
                val chapterId = target.chapter.id
                if (mangaId != null && chapterId != null && InkShelf2GuidesData.matchedChapter(applicationContext, guideId, issueId) == null) {
                    InkShelf2GuidesData.rememberChapter(applicationContext, guideId, issueId, mangaId, chapterId)
                }
            }
        }
        return queued
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
