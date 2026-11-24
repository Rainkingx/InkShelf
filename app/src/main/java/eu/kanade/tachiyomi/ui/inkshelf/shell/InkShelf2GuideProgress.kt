package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.manga.InkShelfIssueMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Read-only bridge: actual J2K chapters remain the sole source of reader progress. */
internal object InkShelf2GuideProgress {
    data class Target(val manga: Manga, val chapter: Chapter)
    data class IssueState(
        val read: Boolean,
        val partial: Boolean,
        val coverUrl: String?,
        val targets: List<Target> = emptyList(),
    )
    data class EventState(val issues: Map<Int, IssueState> = emptyMap()) {
        val readIds: Set<String> get() = issues.filterValues { it.read }.keys.map(Int::toString).toSet()
        val partialIds: Set<Int> get() = issues.filterValues { it.partial && !it.read }.keys
    }

    suspend fun load(context: Context, guides: List<InkShelf2Guide>): Map<Int, EventState> =
        withContext(Dispatchers.IO) {
            val db: DatabaseHelper = Injekt.get()
            val orders = guides.associate { guide ->
                guide.id to guide.batcaveSlug?.let { InkShelf2BundledEventOrders.get(it, 1)?.issues }.orEmpty()
            }
            val library = db.getLibraryMangas().executeAsBlocking()
                .filter { it.favorite && it.id != null }.distinctBy { it.id }
            val byTitle = library.groupBy { InkShelf2GuideAutoMatch.key(it.title) }
            val bindings = orders.mapValues { (guideId, issues) ->
                issues.mapNotNull { issue ->
                    InkShelf2GuidesData.matchedChapter(context, guideId, issue.id)?.let { issue.id to it }
                }.toMap()
            }
            val matchedIds = bindings.values.flatMap { it.values }.map { it.first }.distinct()
            val knownManga = mutableMapOf<Long, Manga>()
            library.forEach { manga -> manga.id?.let { knownManga[it] = manga } }
            for (id in matchedIds) {
                if (id !in knownManga) {
                    db.getManga(id).executeAsBlocking()?.let { knownManga[id] = it }
                }
            }
            // Only query chapters for relevant existing library editions and exact previous reader matches.
            val ids = (orders.values.flatten().flatMap { issue ->
                byTitle[InkShelf2GuideAutoMatch.key(issue.series)].orEmpty()
                    .filter { InkShelf2GuideAutoMatch.rank(issue, it.title) == 3 }
                    .mapNotNull { it.id }
            } + matchedIds).distinct()
            val chapters = ids.chunked(350).flatMap { batch ->
                db.getChapters(batch).executeAsBlocking()
            }.groupBy { it.manga_id }
            orders.mapValues { (guideId, issues) ->
                EventState(issues.mapNotNull { issue ->
                    val candidates = mutableListOf<Chapter>()
                    val linked = bindings[guideId]?.get(issue.id)
                    if (linked != null) {
                        val found = chapters[linked.first].orEmpty().firstOrNull { it.id == linked.second }
                        // Verify the chapter is still the exact issue, and never infer individual
                        // progress from a multi-issue grouped chapter.
                        val exact = InkShelf2GuideChapterFinder.find(issue, chapters[linked.first].orEmpty())
                        if (found != null && exact?.grouped == false && exact.chapter.id == found.id)
                            candidates += found
                    }
                    byTitle[InkShelf2GuideAutoMatch.key(issue.series)].orEmpty()
                        .filter { InkShelf2GuideAutoMatch.rank(issue, it.title) == 3 }
                        .forEach { manga: Manga ->
                            val exact = InkShelf2GuideChapterFinder.find(issue, chapters[manga.id].orEmpty())
                            if (exact != null && !exact.grouped) candidates += exact.chapter
                        }
                    val valid = candidates.distinctBy { it.id }
                    if (valid.isEmpty()) null else issue.id to IssueState(
                        read = valid.any { it.read },
                        partial = valid.any { !it.read && it.last_page_read > 0 },
                        coverUrl = valid.firstNotNullOfOrNull { InkShelfIssueMetadata.from(it).coverUrl },
                        targets = valid.mapNotNull { chapter ->
                            val manga = chapter.manga_id?.let(knownManga::get) ?: return@mapNotNull null
                            Target(manga, chapter)
                        },
                    )
                }.toMap())
            }
        }

    fun effectiveRead(context: Context, guideId: Int, state: EventState): Set<String> =
        (InkShelf2GuidesData.readIds(context, guideId) + state.readIds) -
            InkShelf2GuidesData.manualUnreadIds(context, guideId)
}
