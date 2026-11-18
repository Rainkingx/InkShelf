package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            var searchFocusEpoch by remember { mutableIntStateOf(0) }
            var sourceHubOpen by rememberSaveable { mutableStateOf(false) }
            var sourceHubMode by rememberSaveable { mutableStateOf("Sources") }
            var activeSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
            var openingComic by remember { mutableStateOf(false) }
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
            var selected by remember { mutableStateOf<Manga?>(null) }
            var home by remember { mutableStateOf<InkShelf2Engine.Home?>(null) }
            var library by remember { mutableStateOf<List<InkShelf2LibraryItem>>(emptyList()) }
            var discover by remember { mutableStateOf<InkShelf2Engine.Discover?>(null) }
            var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }

            LaunchedEffect(refreshEpoch) {
                // Read existing data; never reset or migrate the library.
                home = engine.loadHome()
                library = engine.loadLibrary()
                discover = engine.loadDiscover()
            }
            LaunchedEffect(selected?.id, selected?.url) {
                chapters = selected?.let { engine.chapters(it) }.orEmpty()
            }

            val openSourceComic: (Manga) -> Unit = { manga ->
                if (!openingComic) {
                    openingComic = true
                    lifecycleScope.launch {
                        try {
                            selected = sourcesEngine.prepareComic(manga)
                        } catch (error: Exception) {
                            Toast.makeText(
                                this@InkShelf2Activity,
                                error.message ?: "Could not open comic from source",
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            openingComic = false
                        }
                    }
                }
            }

            BackHandler(enabled = selected != null || sourceHubOpen) {
                when {
                    selected != null -> selected = null
                    activeSourceId != null -> activeSourceId = null
                    else -> sourceHubOpen = false
                }
            }
            InkShelf2Shell(
                currentTab = tab,
                showHeader = selected == null,
                showBottomNav = selected == null,
                onTabSelected = {
                    tab = it
                    sourceHubOpen = false
                    activeSourceId = null
                },
                onSearch = {
                    tab = InkShelf2Tab.DISCOVER
                    sourceHubOpen = false
                    activeSourceId = null
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
                    comic != null -> InkShelf2DetailsScreen(
                        manga = comic,
                        chapters = chapters,
                        padding = padding,
                        onBack = { selected = null },
                        onRead = { openReader(comic) },
                        onChapter = { openChapter(comic, it) },
                        onSave = { saveComic(comic) },
                    )
                    tab == InkShelf2Tab.HOME -> InkShelf2HomeScreen(
                        home = home,
                        padding = padding,
                        onOpen = { selected = it },
                        onRead = { openReader(it) },
                        onSave = { saveComic(it) },
                        onDiscover = { tab = InkShelf2Tab.DISCOVER },
                        onLibrary = { tab = InkShelf2Tab.LIBRARY },
                    )
                    tab == InkShelf2Tab.DISCOVER && openingComic -> {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = InkShelf2Colors.AccentLight,
                            )
                        }
                    }
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
                        onOpenComic = openSourceComic,
                    )
                    tab == InkShelf2Tab.DISCOVER -> InkShelf2DiscoverScreen(
                        discover = discover,
                        padding = padding,
                        onOpen = { selected = it },
                        sources = enabledSources,
                        onOpenSearchResult = openSourceComic,
                        searchFocusToken = searchFocusEpoch,
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
                    )
                    tab == InkShelf2Tab.LIBRARY -> InkShelf2LibraryScreen(
                        library = library,
                        padding = padding,
                        onOpen = { selected = it },
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
        lifecycleScope.launch {
            val chapter = engine.nextChapter(manga)
            if (chapter != null) {
                openChapter(manga, chapter)
            } else {
                Toast.makeText(
                    this@InkShelf2Activity,
                    "No cached issue available for this comic yet",
                    Toast.LENGTH_SHORT,
                ).show()
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
