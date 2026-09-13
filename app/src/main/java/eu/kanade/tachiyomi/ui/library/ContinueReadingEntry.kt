package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.LibraryManga

/**
 * A real Continue Reading target derived from J2K's chapter/history database.
 * [chapter] is the chapter J2K should open: either the partially-read chapter,
 * or the next unread chapter after the most recently completed one.
 */
data class ContinueReadingEntry(
    val manga: LibraryManga,
    val chapter: Chapter,
    val resumesPartialChapter: Boolean,
)
