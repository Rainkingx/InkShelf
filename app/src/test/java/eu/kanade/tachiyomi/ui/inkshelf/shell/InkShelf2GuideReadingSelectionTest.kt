package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InkShelf2GuideReadingSelectionTest {
    private fun issue(id: Int, pos: Int, role: String) =
        InkShelf2GuideIssue(id, pos, "Civil War (2006)", null, "$id", role)

    @Test
    fun essentialAndCompleteRemainInReadingOrder() {
        val all = listOf(
            issue(3, 3, "Optional tie-in"),
            issue(1, 1, "Core Issue"),
            issue(2, 2, "Optional tie-in"),
            issue(4, 4, "Epilogue"),
        )
        assertEquals(listOf(1, 4),
            InkShelf2GuideReadingSelection.issuesForMode(all, true).map { it.id })
        assertEquals(listOf(1, 2, 3, 4),
            InkShelf2GuideReadingSelection.issuesForMode(all, false).map { it.id })
    }

    @Test
    fun missingShortListStillAllowsReading() {
        val all = listOf(issue(7, 1, "Optional tie-in"))
        assertEquals(all, InkShelf2GuideReadingSelection.issuesForMode(all, true))
    }

    @Test
    fun startAndContinueUseFirstUnreadThenAllowRereading() {
        val all = listOf(issue(1, 1, "Core Issue"), issue(2, 2, "Core Issue"))
        assertEquals(1, InkShelf2GuideReadingSelection.next(all, emptySet())?.id)
        assertEquals(2, InkShelf2GuideReadingSelection.next(all, setOf("1"))?.id)
        assertEquals(1, InkShelf2GuideReadingSelection.next(all, setOf("1","2"))?.id)
        assertNull(InkShelf2GuideReadingSelection.next(emptyList(), emptySet()))
    }
}
