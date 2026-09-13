package eu.kanade.tachiyomi.ui.more.stats

import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.MANGA_HAS_UNREAD
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.MANGA_NON_READ
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar

/**
 * Presenter of [StatsController].
 */
class StatsPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    private val prefs: PreferencesHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {
    private val libraryMangas = getLibrary()
    val mangaDistinct = libraryMangas.distinct()

    private fun getLibrary(): MutableList<LibraryManga> = db.getLibraryMangas().executeAsBlocking()

    fun getTracks(manga: Manga): MutableList<Track> = db.getTracks(manga).executeAsBlocking()

    fun getLoggedTrackers(): List<TrackService> = trackManager.services.filter { it.isLogged }

    fun getSources(): List<CatalogueSource> {
        val languages = prefs.enabledLanguages().get()
        val hiddenCatalogues = prefs.hiddenSources().get()
        return sourceManager
            .getCatalogueSources()
            .filter { it.lang in languages }
            .filterNot { it.id.toString() in hiddenCatalogues }
    }

    fun getGlobalUpdateManga(): Map<Long?, List<LibraryManga>> {
        val includedCategories = prefs.libraryUpdateCategories().get().map(String::toInt)
        val excludedCategories = prefs.libraryUpdateCategoriesExclude().get().map(String::toInt)
        val restrictions = prefs.libraryUpdateMangaRestriction().get()
        return libraryMangas
            .groupBy { it.id }
            .filterNot { it.value.any { manga -> manga.category in excludedCategories } }
            .filter { includedCategories.isEmpty() || it.value.any { manga -> manga.category in includedCategories } }
            .filterNot {
                val manga = it.value.first()
                (MANGA_NON_COMPLETED in restrictions && manga.status == SManga.COMPLETED) ||
                    (MANGA_HAS_UNREAD in restrictions && manga.unread != 0) ||
                    (MANGA_NON_READ in restrictions && manga.totalChapters > 0 && !manga.hasRead)
            }
    }

    fun getDownloadCount(manga: LibraryManga): Int = downloadManager.getDownloadCount(manga)

    fun get10PointScore(track: Track): Float? {
        val service = trackManager.getService(track.sync_id)
        return service?.get10PointScore(track.score)
    }

    fun getReadDuration(): String {
        val chaptersTime = db.getTotalReadDuration()
        return chaptersTime.getReadDuration(prefs.context.getString(R.string.none))
    }

    data class MonthlyReadingStats(
        val chaptersRead: Int,
        val titlesTouched: Int,
        val readDuration: String,
    )

    data class TopWorldsStats(
        val topSeries: String?,
        val topSeriesReads: Int,
        val topGenre: String?,
        val topGenreReads: Int,
        val topCreator: String?,
        val topCreatorReads: Int,
        val longestSeries: String?,
        val longestSeriesChapters: Int,
    )

    fun getTopWorldsStats(): TopWorldsStats {
        // Rank the reading-based cards from actual history rather than LibraryManga.read.
        // LibraryManga.read reflects the current library state, which can under-count
        // titles that were read in the past or whose library state later changed.
        val history =
            db.getHistoryPerPeriod(0L, System.currentTimeMillis()).executeAsBlocking()

        val topSeriesHistory =
            history
                .groupBy { it.manga.id }
                .values
                .maxByOrNull { it.size }
        val topSeries = topSeriesHistory?.firstOrNull()?.manga

        val topGenre =
            history
                .flatMap { item ->
                    item.manga
                        .getGenres()
                        .orEmpty()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                        .map { genre -> genre.lowercase() to genre }
                }.groupBy { it.first }
                .values
                .map { entries -> entries.first().second to entries.size }
                .maxByOrNull { it.second }

        val topCreator =
            history
                .flatMap { item ->
                    listOfNotNull(item.manga.author, item.manga.artist)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                        .map { creator -> creator.lowercase() to creator }
                }.groupBy { it.first }
                .values
                .map { entries -> entries.first().second to entries.size }
                .maxByOrNull { it.second }

        // Longest series is a collection-size metric, so keep it based on current
        // Library chapter totals rather than reading history.
        val longestSeries =
            mangaDistinct
                .filter { it.totalChapters > 0 }
                .maxByOrNull { it.totalChapters }

        return TopWorldsStats(
            topSeries = topSeries?.title,
            topSeriesReads = topSeriesHistory?.size ?: 0,
            topGenre = topGenre?.first,
            topGenreReads = topGenre?.second ?: 0,
            topCreator = topCreator?.first,
            topCreatorReads = topCreator?.second ?: 0,
            longestSeries = longestSeries?.title,
            longestSeriesChapters = longestSeries?.totalChapters ?: 0,
        )
    }

    fun getMonthlyReadingStats(monthOffset: Int = 0): MonthlyReadingStats {
        val start =
            Calendar.getInstance().apply {
                add(Calendar.MONTH, monthOffset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val end =
            Calendar.getInstance().apply {
                timeInMillis = start.timeInMillis
                add(Calendar.MONTH, 1)
                timeInMillis -= 1
            }

        val history = db.getHistoryPerPeriod(start.timeInMillis, end.timeInMillis).executeAsBlocking()
        val duration = history.sumOf { it.history.time_read }

        return MonthlyReadingStats(
            chaptersRead = history.size,
            titlesTouched = history.map { it.manga }.distinctBy { it.id }.size,
            readDuration = duration.getReadDuration(prefs.context.getString(R.string.none)),
        )
    }
}
