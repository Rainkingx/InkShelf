package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
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
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverRefreshWorker
import kotlinx.coroutines.launch

/**
 * A genuinely separate Compose activity: no MainActivity XML, toolbar,
 * old Conductor controllers, legacy bottom navigation or header overlays.
 *
 * Exposed as a SECOND launcher icon only while the preview is under development.
 * The stable launcher remains available and the library database is shared.
 */
class InkShelf2Activity : AppCompatActivity() {
    private val engine by lazy { InkShelf2Engine(applicationContext) }
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

            BackHandler(enabled = selected != null) { selected = null }
            InkShelf2Shell(
                currentTab = tab,
                showHeader = selected == null,
                showBottomNav = selected == null,
                onTabSelected = { tab = it },
                onSearch = { tab = InkShelf2Tab.DISCOVER },
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
                    tab == InkShelf2Tab.DISCOVER -> InkShelf2DiscoverScreen(
                        discover = discover,
                        padding = padding,
                        onOpen = { selected = it },
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
