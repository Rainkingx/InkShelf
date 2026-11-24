package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InkShelf2GuideChapterFinderTest {
    private val issue = InkShelf2GuideIssue(3, 3, "War of the Realms", null, "1", "Core Issue", 2019)
    private fun chapter(label: String, sequence: Float) = Chapter.create().apply {
        name = label
        chapter_number = sequence
    }

    @Test
    fun printedNumberWinsOverUnlabelledInternalSequence() {
        val first = chapter("Issue #1", 2f)
        val other = chapter("Prelude", 1f)
        assertEquals(first, InkShelf2GuideChapterFinder.find(issue, listOf(other, first))?.chapter)
    }

    @Test
    fun ambiguousPrintedNumbersDoNotGuess() {
        assertNull(InkShelf2GuideChapterFinder.find(issue,
            listOf(chapter("Issue #1", 1f), chapter("Chapter #1", 2f))))
    }

    @Test
    fun singlePrintedIssueWinsOverCollection() {
        val first = chapter("Issue #1", 3f)
        val grouped = chapter("#001-#006", 1f)
        assertEquals(first, InkShelf2GuideChapterFinder.find(issue, listOf(grouped, first))?.chapter)
    }

    @Test
    fun originalWarOfTheRealmsIssueWinsOverDirectorsCut() {
        val first = chapter("#1", 2f)
        val chapters = (6 downTo 2).map { chapter("#" + it, it.toFloat()) } +
            listOf(first, chapter("#1 Director 's Cut", 1f))
        org.junit.Assert.assertSame(first, InkShelf2GuideChapterFinder.find(issue, chapters)?.chapter)
    }

    @Test
    fun alternateEditionAloneDoesNotReplaceOriginal() {
        assertNull(InkShelf2GuideChapterFinder.find(issue,
            listOf(chapter("#1 Director’s Cut", 1f))))
        assertNull(InkShelf2GuideChapterFinder.find(issue,
            listOf(chapter("#1 Facsimile Edition", 1f))))
    }

    @Test
    fun decimalIssueIsDistinctFromRegularIssueAndOtherDecimalIssues() {
        val wanted = issue.copy(number = "7.1")
        val exact = chapter("Issue #7.1", 8f)
        val regular = chapter("Issue #7", 7f)
        val other = chapter("Issue #7.2", 9f)
        assertEquals(exact, InkShelf2GuideChapterFinder.find(wanted, listOf(regular, other, exact))?.chapter)
        assertNull(InkShelf2GuideChapterFinder.find(wanted, listOf(regular, other)))
        assertNull(InkShelf2GuideChapterFinder.find(issue.copy(number = "7"), listOf(chapter("#7.1", 7f))))
        assertEquals(regular, InkShelf2GuideChapterFinder.find(issue.copy(number = "7"), listOf(regular, exact))?.chapter)
    }

    @Test
    fun articleAliasIsSpecificToWarOfTheRealms() {
        assertEquals(3, InkShelf2GuideAutoMatch.rank(issue, "The War of the Realms (2019)"))
        org.junit.Assert.assertTrue(InkShelf2GuideAutoMatch.queries(issue).contains("The War of the Realms"))
    }
}
