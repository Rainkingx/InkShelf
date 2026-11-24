package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.manga.InkShelfIssueMetadata
import eu.kanade.tachiyomi.ui.reader.InkShelf2EventPageResume
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
        // A verified grouped entry may resume, but must not count as completion
        // or an individually downloadable issue.
        val boundTarget: Target? = null,
    )
    data class EventState(val issues: Map<Int, IssueState> = emptyMap()) {
        val readIds: Set<String> get() = issues.filterValues { it.read }.keys.map(Int::toString).toSet()
        val partialIds: Set<Int> get() = issues.filterValues { it.partial && !it.read }.keys
    }

    suspend fun load(context: Context, guides: List<InkShelf2Guide>): Map<Int, EventState> =
        withContext(Dispatchers.IO) {
            val db: DatabaseHelper = Injekt.get()
            val orders = guides.associate { guide ->
                guide.id to InkShelf2PersonalEvents.order(context, guide)
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
                    var linkedChapter: Chapter? = null
                    var boundChapter: Chapter? = null
                    if (linked != null) {
                        val found = chapters[linked.first].orEmpty().firstOrNull { it.id == linked.second }
                        // Verify the chapter is still the exact issue, and never infer individual
                        // progress from a multi-issue grouped chapter.
                        val explicit = issue.explicitSource != null
                        val exact = if (explicit) {
                            val manga = knownManga[linked.first]
                            if (found != null && manga != null && InkShelf2PersonalEvents.explicitMatches(issue, manga, found))
                                InkShelf2GuideChapterFinder.Found(found, false) else null
                        } else InkShelf2GuideChapterFinder.find(issue, chapters[linked.first].orEmpty())
                        if (found != null && exact != null && exact.chapter.id == found.id) {
                            boundChapter = found
                            if (!exact.grouped) {
                                candidates += found
                                linkedChapter = found
                            }
                        }
                    }
                    if (issue.explicitSource == null) byTitle[InkShelf2GuideAutoMatch.key(issue.series)].orEmpty()
                        .filter { InkShelf2GuideAutoMatch.rank(issue, it.title) == 3 }
                        .forEach { manga: Manga ->
                            val exact = InkShelf2GuideChapterFinder.find(issue, chapters[manga.id].orEmpty())
                            if (exact != null && !exact.grouped) candidates += exact.chapter
                        }
                    val valid = candidates.distinctBy { it.id }
                    // An explicitly opened chapter is authoritative for this event.
                    // A finished duplicate must never advance an unfinished source copy.
                    // Without a binding, prefer unfinished reading progress over an
                    // inferred completion from another edition-matched library copy.
                    val bound = boundChapter?.let { chapter ->
                        chapter.manga_id?.let(knownManga::get)?.let { manga -> Target(manga, chapter) }
                    }
                    val status = if (linkedChapter == null && bound != null && InkShelf2EventPageResume.isPartial(bound.chapter))
                        false to true
                    else chapterStatus(linkedChapter, valid)
                    if (valid.isEmpty() && bound == null) null else issue.id to IssueState(
                        read = status.first,
                        partial = status.second,
                        coverUrl = valid.firstNotNullOfOrNull { InkShelfIssueMetadata.from(it).coverUrl },
                        boundTarget = bound,
                        targets = valid.mapNotNull { chapter ->
                            val manga = chapter.manga_id?.let(knownManga::get) ?: return@mapNotNull null
                            Target(manga, chapter)
                        },
                    )
                }.toMap())
            }
        }

    internal fun chapterStatus(linked: Chapter?, candidates: List<Chapter>): Pair<Boolean, Boolean> {
        fun partial(chapter: Chapter) =
            !chapter.read && (chapter.last_page_read > 0 || chapter.pages_left > 0)
        if (linked != null) {
            val unfinished = InkShelf2EventPageResume.isPartial(linked)
            return (linked.read && !unfinished) to unfinished
        }
        val unfinished = candidates.any(::partial)
        return (candidates.any { it.read } && !unfinished) to unfinished
    }

    /** Prefer the previously read source to preserve its saved page. */
    fun resumeTarget(
        context: Context,
        guideId: Int,
        issueId: Int,
        state: EventState,
        enabledSourceIds: Set<Long>,
    ): Target? {
        val linked = InkShelf2GuidesData.matchedChapter(context, guideId, issueId)
        val issue = state.issues[issueId] ?: return null
        val candidates = issue.targets.filter { it.manga.source in enabledSourceIds }
        val exact = (listOfNotNull(issue.boundTarget) + candidates).firstOrNull {
            it.manga.source in enabledSourceIds && it.manga.id == linked?.first && it.chapter.id == linked?.second
        }
        // Never switch to an alternate copy merely because it was downloaded
        // or has a later page. A saved page belongs to its original chapter.
        return exact ?: candidates.filter { !it.chapter.read && InkShelf2EventPageResume.isPartial(it.chapter) }
            .maxByOrNull { it.chapter.last_page_read }
    }

    fun effectiveRead(context: Context, guideId: Int, state: EventState): Set<String> =
        (InkShelf2GuidesData.readIds(context, guideId) + state.readIds) -
            InkShelf2GuidesData.manualUnreadIds(context, guideId)
}
