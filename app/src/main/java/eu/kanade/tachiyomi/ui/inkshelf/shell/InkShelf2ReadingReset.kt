package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Same progress fields as J2K mark unread. Never touches downloads or bookmarks. */
internal object InkShelf2ReadingReset {
    private fun clear(chapter: Chapter) {
        chapter.read = false
        chapter.last_page_read = 0
        chapter.pages_left = 0
    }

    suspend fun comic(manga: Manga, selectedId: Long? = null): Int = withContext(Dispatchers.IO) {
        val db: DatabaseHelper = Injekt.get()
        val local = db.getManga(manga.url, manga.source).executeAsBlocking() ?: return@withContext 0
        val chapters = db.getChapters(local).executeAsBlocking()
            .filter { it.id != null && (selectedId == null || it.id == selectedId) }
        chapters.forEach(::clear)
        if (chapters.isNotEmpty()) db.updateChaptersProgress(chapters).executeAsBlocking()
        chapters.size
    }

    data class EventResult(val reset: Int, val groupedSkipped: Int)

    suspend fun event(context: Context, guide: InkShelf2Guide): EventResult = withContext(Dispatchers.IO) {
        val db: DatabaseHelper = Injekt.get()
        val state = InkShelf2GuideProgress.load(context, listOf(guide))[guide.id]
            ?: error("No event progress available")
        val targets = state.issues.values.flatMap { it.targets }.mapNotNull { it.chapter.id }.distinct()
        val chapters = targets.mapNotNull { db.getChapter(it).executeAsBlocking() }
        chapters.forEach(::clear)
        if (chapters.isNotEmpty()) db.updateChaptersProgress(chapters).executeAsBlocking()
        val skipped = state.issues.values.count { entry ->
            val bound = entry.boundTarget
            bound != null && entry.targets.none { it.chapter.id == bound.chapter.id }
        }
        InkShelf2GuidesData.clearReadingMarks(context, guide.id)
        EventResult(chapters.size, skipped)
    }
}
