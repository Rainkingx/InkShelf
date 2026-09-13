package eu.kanade.tachiyomi.appwidget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.appwidget.components.CoverHeight
import eu.kanade.tachiyomi.appwidget.components.CoverWidth
import eu.kanade.tachiyomi.appwidget.components.LockedWidget
import eu.kanade.tachiyomi.appwidget.components.UpdatesWidget
import eu.kanade.tachiyomi.appwidget.util.appWidgetBackgroundRadius
import eu.kanade.tachiyomi.appwidget.util.calculateRowAndColumnCount
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.recents.RecentsPresenter
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

class UpdatesGridGlanceWidget : GlanceAppWidget() {
    private val app: Application by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    override val sizeMode = SizeMode.Exact

    // SizeMode.Exact isn't a valid PreviewSizeMode (there's no live AppWidgetManager size to be
    // exact about for a one-shot preview render), so pin the generated preview to a size roughly
    // matching this widget's targetCellWidth/targetCellHeight (4x2) in the provider info XML.
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(PreviewSize))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            // If app lock enabled, don't do anything
            if (preferences.useBiometrics().get()) {
                LockedWidget()
            } else {
                val rawMangaList = currentState(MangaListKey)
                val data by produceState<List<Pair<Long, Bitmap?>>?>(null, rawMangaList) {
                    value = loadCovers(decodeMangaList(rawMangaList))
                }
                UpdatesWidget(data)
            }
        }
    }

    /**
     * Generates a one-shot preview for the Android 15+ widget picker (see [setWidgetPreviews]).
     * Unlike [provideGlance], this composition never recomposes, so data must be loaded before
     * calling [provideContent] rather than observed reactively.
     */
    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val useLock = preferences.useBiometrics().get()
        val data =
            if (useLock) {
                null
            } else {
                val (rowCount, columnCount) = PreviewSize.calculateRowAndColumnCount()
                val cellCount = minOf(rowCount * columnCount, maxCoversForMemoryBudget())
                val processList = RecentsPresenter.getRecentManga(customAmount = min(50, cellCount))
                loadCovers(
                    processList
                        .sortedByDescending { it.second }
                        .take(cellCount)
                        .mapNotNull { (manga, _) -> manga.takeIf { it.id != null } },
                )
            }
        provideContent {
            if (useLock) {
                LockedWidget()
            } else {
                UpdatesWidget(data)
            }
        }
    }

    /**
     * Fetches the recent manga list and persists just the fields [MangaCoverFetcher][
     * eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher] needs (id, url, favorite, source) to
     * this widget's state, skipping a DB round-trip in [loadCovers]. The covers themselves are
     * loaded lazily by the composable so that a resize (which recomposes an already-running
     * session) picks up fresh data through [currentState] instead of requiring this method to
     * reach into an active composition.
     */
    suspend fun loadData(list: List<Pair<Manga, Long>>? = null) =
        withContext(Dispatchers.IO) {
            // Don't show anything when lock is active
            if (preferences.useBiometrics().get()) {
                updateAll(app)
                return@withContext
            }

            val manager = GlanceAppWidgetManager(app)
            val ids = manager.getGlanceIds(this@UpdatesGridGlanceWidget::class.java)
            if (ids.isEmpty()) return@withContext

            val (rowCount, columnCount) =
                ids
                    .flatMap { manager.getAppWidgetSizes(it) }
                    .map { it.calculateRowAndColumnCount() }
                    .maxBy { (rows, columns) -> rows * columns }
            // The OS enforces a hard per-widget bitmap memory ceiling (device/density-dependent,
            // reported as low as ~13MB on some devices) and throws rather than degrading, so cap
            // how many covers we ever decode by an actual byte budget, not just a cell count.
            val cellCount = minOf(rowCount * columnCount, maxCoversForMemoryBudget())
            val processList = list ?: RecentsPresenter.getRecentManga(customAmount = min(50, cellCount))
            val encoded =
                processList
                    .sortedByDescending { it.second }
                    .take(cellCount)
                    .mapNotNull { (manga, _) -> manga.takeIf { it.id != null } }
                    .joinToString("\n") { manga ->
                        "${manga.id}\t${if (manga.favorite) "1" else "0"}\t${manga.source}\t${manga.thumbnail_url.orEmpty()}"
                    }

            // Once we've committed to a final manga list, make sure the state write and the
            // session notification always land together. A resize arriving mid-write here (this
            // worker gets replaced by ExistingWorkPolicy.REPLACE) would otherwise leave the store
            // updated with nothing telling the running session to re-read it, permanently stuck.
            withContext(NonCancellable) {
                ids.forEach { id ->
                    updateAppWidgetState(app, id) { prefs -> prefs[MangaListKey] = encoded }
                    update(app, id)
                }
            }
        }

    private suspend fun loadCovers(mangaList: List<Manga>): List<Pair<Long, Bitmap?>> {
        // Resize to cover size
        val widthPx = CoverWidth.value.toInt().dpToPx
        val heightPx = CoverHeight.value.toInt().dpToPx
        val roundPx = app.resources.getDimension(R.dimen.appwidget_inner_radius)
        return mangaList.mapNotNull { manga ->
            val mangaId = manga.id ?: return@mapNotNull null
            val request =
                ImageRequest
                    .Builder(app)
                    .data(manga)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .precision(Precision.EXACT)
                    .size(widthPx, heightPx)
                    .scale(Scale.FILL)
                    .let {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            it.transformations(RoundedCornersTransformation(roundPx))
                        } else {
                            it // Handled by system
                        }
                    }.build()
            Pair(
                mangaId,
                app.imageLoader
                    .execute(request)
                    .drawable
                    ?.toBitmap(),
            )
        }
    }

    /**
     * How many ARGB_8888 covers at [CoverWidth]x[CoverHeight] fit in [MAX_COVER_BITMAP_BYTES]. Covers
     * are decoded at a device-density-dependent pixel size, so this scales down automatically on
     * higher-density devices instead of relying on a fixed cell count.
     */
    private fun maxCoversForMemoryBudget(): Int {
        val widthPx = CoverWidth.value.toInt().dpToPx
        val heightPx = CoverHeight.value.toInt().dpToPx
        val bytesPerCover = widthPx.toLong() * heightPx.toLong() * 4L
        return (MAX_COVER_BITMAP_BYTES / bytesPerCover).toInt().coerceAtLeast(1)
    }

    companion object {
        private val MangaListKey = stringPreferencesKey("recent_manga_list")

        // Roughly matches targetCellWidth=4 / targetCellHeight=2 from the widget's provider info
        // XML, using the same 64/95 dp-per-cell divisors as calculateRowAndColumnCount.
        private val PreviewSize = DpSize(width = 256.dp, height = 190.dp)

        // Conservative budget for this widget's own decoded covers, well under the OS's observed
        // per-widget RemoteViews bitmap ceiling (~13MB on one test device) to leave headroom for
        // other overhead (e.g. a widget host combining separate portrait/landscape RemoteViews).
        private const val MAX_COVER_BITMAP_BYTES = 4L * 1024 * 1024

        /** Reconstructs the handful of [Manga] fields a cover fetch needs; see [loadData]. */
        private fun decodeMangaList(raw: String?): List<Manga> =
            raw
                ?.takeIf { it.isNotEmpty() }
                ?.split("\n")
                ?.mapNotNull { line ->
                    val parts = line.split("\t", limit = 4)
                    if (parts.size != 4) return@mapNotNull null
                    val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                    val source = parts[2].toLongOrNull() ?: return@mapNotNull null
                    MangaImpl().apply {
                        this.id = id
                        this.source = source
                        this.favorite = parts[1] == "1"
                        this.thumbnail_url = parts[3]
                        this.url = parts[3]
                    }
                } ?: emptyList()
    }
}

val ContainerModifier =
    GlanceModifier
        .fillMaxSize()
        .background(ImageProvider(R.drawable.appwidget_background))
        .appWidgetBackground()
        .appWidgetBackgroundRadius()
