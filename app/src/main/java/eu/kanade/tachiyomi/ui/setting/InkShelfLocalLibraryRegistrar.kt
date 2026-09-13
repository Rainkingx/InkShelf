package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Bridges files imported by InkShelf into J2K's existing Local Source library model.
 *
 * v3 also asks LocalSource for info.json-backed details before persisting the favourite row,
 * so ComicInfo.xml metadata written by the importer is visible immediately in Manga Details.
 */
internal object InkShelfLocalLibraryRegistrar {
    data class Result(
        val ready: Int,
        val failed: Int,
    )

    fun register(
        context: Context,
        seriesNames: Set<String>,
        staleSeriesNames: Set<String> = emptySet(),
    ): Result {
        if (seriesNames.isEmpty() && staleSeriesNames.isEmpty()) {
            return Result(ready = 0, failed = 0)
        }

        val db: DatabaseHelper = Injekt.get()
        val localSource = LocalSource(context.applicationContext)
        var ready = 0
        var failed = 0

        // If v1/v2 had previously put the same imported CBZ in a bad series folder, v3 can
        // migrate the identical file to its corrected ComicInfo-derived folder. Hide the old
        // Local Source favourite once that old folder has genuinely become empty and was removed.
        staleSeriesNames
            .filterNot { it in seriesNames }
            .forEach { staleSeriesName ->
                try {
                    val staleManga = db.getManga(staleSeriesName, LocalSource.ID).executeAsBlocking()
                    if (staleManga?.favorite == true) {
                        staleManga.favorite = false
                        db.insertManga(staleManga).executeAsBlocking()
                    }
                } catch (error: Throwable) {
                    Timber.e(error, "Unable to hide migrated local series: %s", staleSeriesName)
                }
            }

        seriesNames.forEach { seriesName ->
            try {
                val sourceManga =
                    runBlocking {
                        localSource
                            .getSearchManga(1, seriesName, FilterList())
                            .mangas
                            .firstOrNull { it.url == seriesName }
                    } ?: throw IllegalStateException("Imported local series was not found: $seriesName")

                val detailedManga =
                    runBlocking {
                        localSource.getMangaDetails(sourceManga)
                    }

                val manga =
                    db.getManga(sourceManga.url, LocalSource.ID).executeAsBlocking()
                        ?: Manga.create(sourceManga.url, sourceManga.title, LocalSource.ID)

                val wasFavorite = manga.favorite
                manga.copyFrom(sourceManga)
                manga.copyFrom(detailedManga)
                sourceManga.thumbnail_url?.let { manga.thumbnail_url = it }
                manga.favorite = true

                if (!wasFavorite) {
                    manga.date_added = System.currentTimeMillis()
                }

                val putResult = db.insertManga(manga).executeAsBlocking()
                if (manga.id == null) {
                    manga.id = putResult.insertedId()
                }

                ready++
            } catch (error: Throwable) {
                Timber.e(error, "Unable to register imported local series in Library: %s", seriesName)
                failed++
            }
        }

        return Result(ready = ready, failed = failed)
    }
}
