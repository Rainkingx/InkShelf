package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.ui.manga.InkShelfIssueMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Reuse existing source metadata, without new source requests or downloading story pages. */
internal object InkShelf2CachedGuideArtwork {
    suspend fun load(issues: List<InkShelf2GuideIssue>): Map<Int, String> = withContext(Dispatchers.IO) {
        if (issues.isEmpty()) return@withContext emptyMap()
        val db: DatabaseHelper = Injekt.get()
        val titles = issues.groupBy { InkShelf2GuideAutoMatch.key(it.series) }
        val local = db.getMangas().executeAsBlocking().filter { manga ->
            manga.id != null && titles[InkShelf2GuideAutoMatch.key(manga.title)].orEmpty()
                .any { InkShelf2GuideAutoMatch.rank(it, manga.title) == 3 }
        }
        val byTitle = local.groupBy { InkShelf2GuideAutoMatch.key(it.title) }
        val chapters = local.mapNotNull { it.id }.distinct().chunked(350)
            .flatMap { db.getChapters(it).executeAsBlocking() }.groupBy { it.manga_id }
        issues.mapNotNull { issue ->
            val url = byTitle[InkShelf2GuideAutoMatch.key(issue.series)].orEmpty()
                .filter { InkShelf2GuideAutoMatch.rank(issue, it.title) == 3 }
                .sortedByDescending { it.favorite }
                .firstNotNullOfOrNull { manga ->
                    InkShelf2GuideChapterFinder.find(issue, chapters[manga.id].orEmpty())
                        ?.takeUnless { it.grouped }?.chapter?.let { InkShelfIssueMetadata.from(it).coverUrl }
                }
            url?.let { issue.id to it }
        }.toMap()
    }
}
