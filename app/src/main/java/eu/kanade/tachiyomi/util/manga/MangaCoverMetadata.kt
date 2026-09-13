package eu.kanade.tachiyomi.util.manga

import android.graphics.BitmapFactory
import android.os.Process
import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.getBestColor
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Object that holds info about a covers size ratio + dominant colors */
object MangaCoverMetadata {
    private var coverRatioMap = ConcurrentHashMap<Long, Float>()
    private var coverColorMap = ConcurrentHashMap<Long, Pair<Int, Int>>()
    private val preferences by injectLazy<PreferencesHelper>()
    private val coverCache by injectLazy<CoverCache>()

    /**
     * Decoding a cover and running a palette over it is CPU work. The old per-fetcher scope ran it
     * on Dispatchers.IO, which is normal thread priority - fine when nothing else is happening, but
     * it competes evenly with the UI thread for CPU while scrolling. A background-priority thread
     * gets the OS to give it less of a share under that contention, without needing to be idle.
     */
    private val metadataScope =
        CoroutineScope(
            SupervisorJob() +
                Executors
                    .newSingleThreadExecutor { runnable ->
                        Thread {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                            runnable.run()
                        }.apply { name = "cover-metadata" }
                    }.asCoroutineDispatcher(),
        )

    fun load() {
        val ratios = preferences.coverRatios().get()
        coverRatioMap =
            ConcurrentHashMap(
                ratios
                    .mapNotNull {
                        val splits = it.split("|")
                        val id = splits.firstOrNull()?.toLongOrNull()
                        val ratio = splits.lastOrNull()?.toFloatOrNull()
                        if (id != null && ratio != null) {
                            id to ratio
                        } else {
                            null
                        }
                    }.toMap(),
            )
        val colors = preferences.coverColors().get()
        coverColorMap =
            ConcurrentHashMap(
                colors
                    .mapNotNull {
                        val splits = it.split("|")
                        val id = splits.firstOrNull()?.toLongOrNull()
                        val color = splits.getOrNull(1)?.toIntOrNull()
                        val textColor = splits.getOrNull(2)?.toIntOrNull()
                        if (id != null && color != null) {
                            id to (color to (textColor ?: 0))
                        } else {
                            null
                        }
                    }.toMap(),
            )
    }

    fun setRatioAndColors(
        manga: Manga,
        ogFile: File? = null,
        force: Boolean = false,
    ) {
        if (!manga.favorite) {
            remove(manga)
        }
        if (manga.vibrantCoverColor != null && !manga.favorite) return
        // Nothing left to read off the cover, and this otherwise runs for every one that scrolls
        // into view - a manga only reaches this state once, the first time all three are found
        if (!force &&
            manga.favorite &&
            getRatio(manga) != null &&
            manga.dominantCoverColors != null &&
            manga.vibrantCoverColor != null
        ) {
            return
        }
        val file = ogFile ?: coverCache.getCustomCoverFile(manga).takeIf { it.exists() } ?: coverCache.getCoverFile(manga)
        // if the file exists and the there was still an error then the file is corrupted
        if (file.exists()) {
            val options = BitmapFactory.Options()
            val hasVibrantColor = if (manga.favorite) manga.vibrantCoverColor != null else true
            if (manga.dominantCoverColors != null && hasVibrantColor && !force) {
                options.inJustDecodeBounds = true
            } else {
                options.inSampleSize = 4
            }
            val bitmap = BitmapFactory.decodeFile(file.path, options)
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                if (manga.favorite) {
                    palette.dominantSwatch?.let { swatch ->
                        manga.dominantCoverColors = swatch.rgb to swatch.titleTextColor
                    }
                }
                palette.getBestColor()?.let { manga.vibrantCoverColor = it }
            }
            if (manga.favorite && !(options.outWidth == -1 || options.outHeight == -1)) {
                addCoverRatio(manga, options.outWidth / options.outHeight.toFloat())
            }
        }
    }

    /** Queues [setRatioAndColors] onto the background-priority thread above. */
    fun setRatioAndColorsAsync(
        manga: Manga,
        ogFile: File? = null,
        force: Boolean = false,
    ) {
        metadataScope.launch {
            setRatioAndColors(manga, ogFile, force)
        }
    }

    fun remove(manga: Manga) {
        val id = manga.id ?: return
        coverRatioMap.remove(id)
        coverColorMap.remove(id)
    }

    fun addCoverRatio(
        manga: Manga,
        ratio: Float,
    ) {
        val id = manga.id ?: return
        coverRatioMap[id] = ratio
    }

    fun addCoverColor(
        manga: Manga,
        @ColorInt color: Int,
        @ColorInt textColor: Int,
    ) {
        val id = manga.id ?: return
        coverColorMap[id] = color to textColor
    }

    fun getColors(manga: Manga): Pair<Int, Int>? = coverColorMap[manga.id]

    fun getRatio(manga: Manga): Float? = coverRatioMap[manga.id]

    fun savePrefs() {
        val mapCopy = coverRatioMap.toMap()
        preferences.coverRatios().set(mapCopy.map { "${it.key}|${it.value}" }.toSet())
        val mapColorCopy = coverColorMap.toMap()
        preferences.coverColors().set(mapColorCopy.map { "${it.key}|${it.value.first}|${it.value.second}" }.toSet())
    }
}
