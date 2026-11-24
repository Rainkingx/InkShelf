package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for offline event reading orders. */
class InkShelf2BundledEventOrdersTest {
    private val order = InkShelf2BundledEventOrders.get("house-of-m", 1)!!

    @Test
    fun loadsAllSixtyFourOrderedIssuesOffline() {
        assertEquals(64, order.issues.size)
        assertFalse(order.more)
        assertEquals((1..64).toList(), order.issues.map { it.position })
        assertEquals("Excalibur (2004) #13", order.issues.first().displayName)
        assertEquals("Giant-Size Ms. Marvel (2006) · One-shot", order.issues.last().displayName)
    }

    @Test
    fun essentialModeContainsExactlyEightHouseOfMIssues() {
        val essential = InkShelf2GuideReadingSelection.issuesForMode(order.issues, true)
        assertEquals(8, essential.size)
        assertEquals((1..8).map { it.toString() }, essential.map { it.number })
        assertTrue(essential.all { it.series == "House of M" && it.yearBegan == 2005 })
        assertEquals(listOf(3, 4, 43, 51, 52, 60, 61, 63), essential.map { it.position })
    }

    @Test
    fun essentialIssuesHaveVerifiedBatcaveSeriesPath() {
        val essential = order.issues.filter { it.isEssential }
        assertTrue(essential.all {
            it.batcaveSeriesPath == "/18298-house-of-m-2005.html" &&
                it.batcaveIssuePath?.startsWith("/event/house-of-m/") == true
        })
    }


    @Test
    fun firstCompleteIssueUsesTheVerified2004ExcaliburEdition() {
        val excalibur = order.issues.first()
        assertEquals("Excalibur", excalibur.series)
        assertEquals(2004, excalibur.yearBegan)
        assertEquals("13", excalibur.number)
        assertEquals("/14576-excalibur-2004.html", excalibur.batcaveSeriesPath)
        assertEquals(3, InkShelf2GuideAutoMatch.rank(excalibur, "Excalibur (2004)"))
        assertEquals(0, InkShelf2GuideAutoMatch.rank(excalibur, "Excalibur (1988)"))
    }

    @Test
    fun allVerifiedTieInPathsPointToSpecificSourcePages() {
        assertTrue(order.issues.filter { it.batcaveSeriesPath != null }.all {
            it.batcaveSeriesPath!!.matches(Regex("""/\d{2,9}-[a-z0-9-]+\.html"""))
        })
        val webTitles = listOf("Excalibur", "The Incredible Hulk", "Uncanny X-Men",
            "New X-Men", "Wolverine", "Spider-Man: House of M")
        assertTrue(webTitles.all { name ->
            order.issues.any { it.series == name && it.batcaveSeriesPath != null }
        })
    }

    @Test
    fun completeOrderKeepsNamedPulseSpecialAndOneShotDistinct() {
        val special = order.issues[19]
        assertEquals("The Pulse", special.series)
        assertEquals("Full", special.number)
        assertEquals("House of M Special", special.issueLabel)
        assertEquals("/28952-the-pulse-2004-2006.html", special.batcaveSeriesPath)
        assertEquals("Full", order.issues.last().number)
        assertEquals("One-shot", order.issues.last().issueLabel)
    }

    @Test
    fun noFalsePaginationOrUnrelatedOfflineEvent() {
        assertNotNull(InkShelf2BundledEventOrders.get("house-of-m", 2))
        assertTrue(InkShelf2BundledEventOrders.get("house-of-m", 2)!!.issues.isEmpty())
        assertEquals(null, InkShelf2BundledEventOrders.get("blackest-night", 1))
    }

    @Test
    fun civilWarOfflineOrderKeepsAll132Positions() {
        val civilWar = InkShelf2BundledEventOrders.get("civil-war-2006", 1)!!
        assertEquals(132, civilWar.issues.size)
        assertEquals((1..132).toList(), civilWar.issues.map { it.position })
        assertFalse(civilWar.more)
        assertTrue(InkShelf2BundledEventOrders.get("civil-war-2006", 2)!!.issues.isEmpty())
        val essential = InkShelf2GuideReadingSelection.issuesForMode(civilWar.issues, true)
        assertEquals(listOf(7, 8, 19, 51, 52, 84, 113, 114), essential.map { it.position })
    }

    @Test
    fun civilWarRetainsOriginalSourceIdentityAndUnavailableEntry() {
        val civilWar = InkShelf2BundledEventOrders.get("civil-war-2006", 1)!!.issues
        val first = civilWar.first()
        assertEquals(1963, first.yearBegan)
        assertEquals("529", first.number)
        assertEquals("/6966-the-amazing-spider-man-1963.html", first.batcaveSeriesPath)
        assertEquals(38796, InkShelf2BundledEventOrders.civilWarChapterId(1))
        val unavailable = civilWar.single { it.position == 64 }
        assertEquals(null, unavailable.batcaveIssuePath)
        assertEquals(null, unavailable.batcaveSeriesPath)
        assertEquals(null, InkShelf2BundledEventOrders.civilWarChapterId(64))
        val ids = civilWar.mapNotNull { InkShelf2BundledEventOrders.civilWarChapterId(it.position) }
        assertEquals(131, ids.size)
        assertEquals(131, ids.distinct().size)
    }

}
