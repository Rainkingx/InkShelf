package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.Chapter

/**
 * Event sessions resume the actual saved position, including an unfinished
 * reread of a chapter that still has its old J2K read flag.
 * The final one/two pages can legitimately mark a single/double-page read complete.
 */
internal object InkShelf2EventPageResume {
    fun isPartial(chapter: Chapter): Boolean =
        if (chapter.read) chapter.pages_left > 2
        else chapter.last_page_read > 0 || chapter.pages_left > 0

    fun page(chapter: Chapter, pageCount: Int): Int =
        if (isPartial(chapter) && pageCount > 0)
            chapter.last_page_read.coerceIn(0, pageCount - 1)
        else 0
}
