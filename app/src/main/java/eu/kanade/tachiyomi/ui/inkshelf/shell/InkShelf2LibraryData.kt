package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Display-only state derived from J2K library, categories and cached chapters.
 * This file never changes reading progress, categories or the existing database.
 */
internal enum class InkShelf2ReadingState {
    UNSTARTED,
    READING,
    COMPLETED,
    ON_HOLD,
}

internal data class InkShelf2LibraryItem(
    val manga: Manga,
    val state: InkShelf2ReadingState,
    val progress: Int?,
    val readChapters: Int,
    val totalChapters: Int,
)

/**
 * The existing J2K library query returns one row per category assignment.
 * Deduplicate on manga ID first; retain ALL category memberships for status.
 */
internal suspend fun loadInkShelf2Library(database: DatabaseHelper): List<InkShelf2LibraryItem> =
    withContext(Dispatchers.IO) {
        val raw = runCatching { database.getLibraryMangas().executeAsBlocking() }
            .getOrDefault(emptyList())
            .filter { it.favorite && it.id != null && !it.isBlank() }

        val memberships = raw.groupBy { it.id!! }
        val categories = runCatching { database.getCategories().executeAsBlocking() }
            .getOrDefault(emptyList())
        fun normalized(name: String) =
            name.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")

        val categoryTypes = categories.mapNotNull { category ->
            val id = category.id ?: return@mapNotNull null
            val type = when (normalized(category.name)) {
                "onhold", "paused" -> InkShelf2ReadingState.ON_HOLD
                "completed", "finished" -> InkShelf2ReadingState.COMPLETED
                "reading", "currentlyreading" -> InkShelf2ReadingState.READING
                else -> null
            }
            if (type == null) null else id to type
        }.toMap()

        val representative = memberships.values.map { rows -> rows.first() }
        val inProgressIds = representative
            .filter { it.unread > 0 && it.id != null }
            .mapNotNull { it.id }

        // One batched query per ~350 comics, not a separate query for every
        // Library cover. J2K's existing chapter table is the only source.
        val partialChapters = inProgressIds.chunked(350)
            .flatMap { ids ->
                runCatching { database.getChapters(ids).executeAsBlocking() }
                    .getOrDefault(emptyList())
            }
            .filter { !it.read && it.last_page_read > 0 && it.manga_id != null }
            .groupBy { it.manga_id!! }
            .mapValues { (_, chapters) ->
                chapters.sumOf { chapter ->
                    (chapter.last_page_read.toDouble() /
                        (chapter.last_page_read + chapter.pages_left).coerceAtLeast(1).toDouble()
                    ).coerceIn(0.0, 0.99)
                }
            }

        val entries = representative.map { manga: LibraryManga ->
            val id = manga.id!!
            val membershipTypes = memberships[id].orEmpty()
                .mapNotNull { categoryTypes[it.category] }
                .toSet()
            val total = manga.totalChapters.coerceAtLeast(0)
            val read = manga.read.coerceIn(0, total)
            val part = partialChapters[id] ?: 0.0
            val state = when {
                InkShelf2ReadingState.ON_HOLD in membershipTypes -> InkShelf2ReadingState.ON_HOLD
                InkShelf2ReadingState.COMPLETED in membershipTypes ->
                    InkShelf2ReadingState.COMPLETED
                total > 0 && read == total -> InkShelf2ReadingState.COMPLETED
                InkShelf2ReadingState.READING in membershipTypes ->
                    InkShelf2ReadingState.READING
                read > 0 || part > 0.0 -> InkShelf2ReadingState.READING
                else -> InkShelf2ReadingState.UNSTARTED
            }
            val progress = if (total > 0) {
                (((read + part) / total) * 100.0).roundToInt().coerceIn(0, 100)
            } else null

            InkShelf2LibraryItem(
                manga = manga,
                state = state,
                progress = progress,
                readChapters = read,
                totalChapters = total,
            )
        }
        // Two J2K favourites can represent the same published edition from
        // different extension sources. Display one item in 2.0; NEVER delete
        // either underlying row, reading history, categories or downloads.
        val pausedSourceIds = InkShelf2ExtensionPause.pausedSourceIds()
        InkShelf2ComicIdentity.groupLibraryCopies(entries).map { copies ->
            // Prefer a usable source if one favourite has been paused; retain
            // all archived rows and their separate progress in J2K unchanged.
            val activeCopies = copies.filterNot { it.manga.source in pausedSourceIds }
            (activeCopies.ifEmpty { copies }).reduce { best, next ->
                InkShelf2ComicIdentity.preferLibraryRecord(best, next)
            }
        }.sortedBy { it.manga.title.lowercase(Locale.ROOT) }
    }
