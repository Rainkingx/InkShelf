package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.manga.InkShelfComicIssueGaps

/**
 * Reuse InkShelf 1's proven issue-number parser. Source chapter_number can be
 * offset by annuals and extras (e.g. internal 170 = printed Issue #144).
 */
internal object InkShelf2IssueNumbers {
    fun fromChapter(chapter: Chapter): Int? = InkShelfComicIssueGaps.issueNumber(chapter)

    fun anchor(chapters: List<Chapter>): Int? =
        chapters.asSequence().mapNotNull(::fromChapter).maxOrNull()
}
