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

    @Test
    fun savedEventsHaveEveryDeclaredPositionAndEssentialEntry() {
        val expected = mapOf(
            "absolute-power" to (32 to 5), "dark-nights-metal" to (26 to 8),
            "infinite-crisis" to (98 to 7), "king-in-black" to (65 to 5),
            "war-of-the-realms" to (63 to 6), "secret-invasion" to (126 to 13),
            "secret-wars-2015" to (230 to 10),
            "batman-zero-year" to (25 to 0), "blood-hunt" to (54 to 6),
            "civil-war-ii" to (152 to 15), "dc-k-o" to (40 to 6),
            "death-of-the-family" to (30 to 5), "fear-itself" to (124 to 10),
            "flashpoint" to (66 to 10), "house-of-m" to (64 to 8),
            "night-of-the-owls" to (15 to 0), "original-sin" to (67 to 11),
            "spider-verse" to (32 to 0), "world-war-hulk" to (52 to 7),
            "x-men-original-sin" to (6 to 0),
        )
        assertEquals(expected.keys, InkShelf2SavedEventOrders.events.map { it.slug }.toSet())
        expected.forEach { (slug, counts) ->
            val page = InkShelf2BundledEventOrders.get(slug, 1)!!
            assertEquals(slug, counts.first, page.issues.size)
            assertEquals(slug, (1..counts.first).toList(), page.issues.map { it.position })
            assertEquals(slug, counts.second, page.issues.count { it.isEssential })
            assertFalse(page.more)
            assertTrue(InkShelf2BundledEventOrders.get(slug, 2)!!.issues.isEmpty())
            assertTrue(page.issues.filterNot { it.sourceUnavailable }.all {
                it.batcaveIssuePath == "/event/$slug/${it.position}"
            })
            assertTrue(page.issues.filterNot { it.sourceUnavailable }.all {
                InkShelf2GuideAutoMatch.isSafeSeriesPath(it.batcaveSeriesPath!!)
            })
            val ids = page.issues.filterNot { it.sourceUnavailable }.map { InkShelf2SavedEventOrders.websiteChapterId(slug, it.position) }
            assertTrue(ids.all { it != null && it > 0 })
            assertEquals(counts.first - page.issues.count { it.sourceUnavailable }, ids.distinct().size)
        }
    }

    @Test
    fun paginatedGuidesIncludeTheirFinalPagesAndEssentialIssues() {
        val invasion = InkShelf2BundledEventOrders.get("secret-invasion", 1)!!.issues
        assertEquals(126, invasion.last().position)
        assertEquals("Secret Invasion Prologue", invasion[4].series)
        assertEquals("Full", invasion[4].number)
        assertTrue(invasion[4].isEssential)
        assertEquals(listOf(1, 2, 3, 4, 5, 44, 45, 71, 84, 110, 118, 120, 122),
            invasion.filter { it.isEssential }.map { it.position })
        val wars = InkShelf2BundledEventOrders.get("secret-wars-2015", 1)!!.issues
        assertEquals(230, wars.last().position)
        assertEquals("Secret Wars Too", wars.last().series)
        assertEquals("Full", wars.last().number)
        assertEquals((0..9).map { it.toString() }, wars.filter { it.isEssential }.map { it.number })
        assertEquals(listOf(3, 4, 24, 35, 156, 212, 218, 222, 223, 224),
            wars.filter { it.isEssential }.map { it.position })
    }

    @Test
    fun secretWarsUnavailableEntriesKeepPositionsWithoutInventedIdentity() {
        val issues = InkShelf2BundledEventOrders.get("secret-wars-2015", 1)!!.issues
        val unavailable = issues.filter { it.sourceUnavailable }
        assertEquals(listOf(34, 79, 80, 81, 82, 139, 202, 216, 227), unavailable.map { it.position })
        assertTrue(unavailable.all {
            it.batcaveIssuePath == null && it.batcaveSeriesPath == null && it.yearBegan == null &&
                InkShelf2SavedEventOrders.websiteChapterId("secret-wars-2015", it.position) == null
        })
        assertEquals(221, issues.count { !it.sourceUnavailable })
        assertEquals("Old Man Logan", unavailable.first().series)
        assertEquals("X-Men '92", unavailable[1].series)
    }

    @Test
    fun castingOneShotIsPartOfMetalsEightEssentialEntries() {
        val order = InkShelf2BundledEventOrders.get("dark-nights-metal", 1)!!.issues
        val casting = order[1]
        assertEquals("Dark Days: The Casting", casting.series)
        assertEquals("Full", casting.number)
        assertTrue(casting.isEssential)
        assertEquals(listOf(1, 2, 3, 4, 14, 21, 23, 26), order.filter { it.isEssential }.map { it.position })
    }

    @Test
    fun annualsAndFcbdRetainNamedChapterIdentity() {
        val crisis = InkShelf2BundledEventOrders.get("infinite-crisis", 1)!!.issues
        assertEquals("Teen Titans", crisis[74].series)
        assertEquals("Full", crisis[74].number)
        assertEquals("Annual 1", crisis[74].issueLabel)
        assertEquals("Batman", crisis[82].series)
        assertEquals("Annual 25", crisis[82].issueLabel)
        val fcbd = InkShelf2BundledEventOrders.get("absolute-power", 1)!!.issues[1]
        assertEquals("FCBD 2024 Collection", fcbd.series)
        assertEquals("Full", fcbd.number)
        assertEquals("Absolute Power", fcbd.issueLabel)
        assertFalse(fcbd.isEssential)
        assertEquals(230704, InkShelf2SavedEventOrders.websiteChapterId("absolute-power", 2))
    }

    @Test
    fun sourceLabelsAndPathsAreRetainedWithoutHiddenLinkText() {
        val king = InkShelf2BundledEventOrders.get("king-in-black", 1)!!.issues
        assertEquals("5", king[61].number)
        assertTrue(king[61].isEssential)
        assertEquals("/3202-king-in-black-iron-man/doom.html", king[24].batcaveSeriesPath)
        assertTrue(InkShelf2SavedEventOrders.events.flatMap { it.entries }.all {
            !it.issue.series.contains("All issues of") && !it.issue.series.contains("↗")
        })
        val war = InkShelf2BundledEventOrders.get("war-of-the-realms", 1)!!.issues
        assertEquals(listOf(3, 15, 23, 39, 43, 60), war.filter { it.isEssential }.map { it.position })
    }

}
