package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline fixtures based on public BatCave event markup/navigation observed in
 * November 2026. They validate mapping rules, not live website availability.
 */
class InkShelf2BatCaveGuidesTest {
    @Test
    fun catalogueFindsEventsButNotNavigationLinks() {
        val html = """
            <a href="/event/marvel/">Marvel 139</a>
            <a href="/event/blackest-night/"><strong>Blackest Night</strong>
              <span>DC</span><span>80 issues · about 16 h All issues readable</span></a>
            <a href="/event/civil-war-2006/">Marvel Civil War Marvel 132 issues · about 26 h</a>
            <a href="/event/secret-wars-2015/">Secret Wars (2015) Marvel 230 issues</a>
            <a href="/event/page/2/">2</a>
            <a href="/10185-blackest-night.html">Blackest Night</a>
        """.trimIndent()
        val found = InkShelf2BatCaveGuides.parseCatalogue(html)
        assertEquals(listOf("blackest-night", "civil-war-2006", "secret-wars-2015"),
            found.map { it.batcaveSlug })
        assertEquals(listOf("DC", "Marvel", "Marvel"), found.map { it.publisher })
    }

    @Test
    fun blackestNightReadsIssueZeroAndShortPathWithoutUsingArrowAsTitle() {
        val html = """
            <h2>Full chronology</h2>
            <ol>
              <li><a href="/event/blackest-night/1">1Blackest Night: Tales of the CorpsIssue #1</a>
                  <a href="/10191-blackest-night-tales-of-the-corps.html">↗</a></li>
              <li><a href="/event/blackest-night/6">6Blackest NightIssue #0</a>
                  <a href="/10185-blackest-night.html">↗</a></li>
              <li><a href="/event/blackest-night/17">17Green Lantern by Geoff Johns (2005)Green Lantern (2005) Issue #45</a>
                  <a href="/10022-green-lantern-by-geoff-johns.html">↗</a></li>
            </ol>
            <h2>Start here: the short path</h2>
            <p>The core story</p>
            <ol><li>Blackest Night: Tales of the Corps #1</li>
                <li>Blackest Night #0</li></ol>
        """.trimIndent()
        val parsed = InkShelf2BatCaveGuides.parseIssues(html, "blackest-night", 1)
        assertEquals(3, parsed.issues.size)
        assertEquals("Blackest Night: Tales of the Corps", parsed.issues[0].series)
        assertEquals("/10191-blackest-night-tales-of-the-corps.html",
            parsed.issues[0].batcaveSeriesPath)
        assertEquals("0", parsed.issues[1].number)
        assertTrue(parsed.issues[1].isEssential)
        assertEquals("Green Lantern by Geoff Johns (2005)", parsed.issues[2].series)
        assertEquals(2005, parsed.issues[2].yearBegan)
        assertFalse(parsed.issues[2].isEssential)
        assertFalse(parsed.more)
    }

    @Test
    fun civilWarSecondPageKeepsOriginalPositionsAndRealSourceLinks() {
        val html = """
            <h1>Marvel Civil War Reading Order</h1>
            <ol>
              <li><a href="/event/civil-war-2006/101">101Thunderbolts (1997-2012)Thunderbolts (1997) Issue #108</a>
                <a href="/18138-thunderbolts-1997-2012.html">↗</a></li>
              <li><a href="/event/civil-war-2006/113">113Civil War (2006)Issue #6</a>
                <a href="/10055-civil-war-2006.html">↗</a></li>
            </ol>
            <a href="/event/civil-war-2006/list/1/">1</a>
        """.trimIndent()
        val parsed = InkShelf2BatCaveGuides.parseIssues(html, "civil-war-2006", 2)
        assertEquals(listOf(101, 113), parsed.issues.map { it.position })
        assertEquals("Civil War (2006)", parsed.issues[1].series)
        assertEquals(6.toString(), parsed.issues[1].number)
        assertFalse(parsed.more)
    }

    @Test
    fun civilWarEssentialIssuesOnSecondPageKeepShortPath() {
        val first = """
            <h2>Start here: the short path</h2>
            <ol><li>Civil War #6</li><li>Civil War #7</li></ol>
        """.trimIndent()
        val second = """
            <ol><li><a href="https://batcave.biz/event/civil-war-2006/113">
              113Civil War (2006)Issue #6</a>
              <a href="https://batcave.biz/10055-civil-war-2006.html">↗</a>
            </li></ol>
        """.trimIndent()
        val parsed = InkShelf2BatCaveGuides.parseIssues(
            second, "civil-war-2006", 2, first,
        )
        assertEquals(1, parsed.issues.size)
        assertTrue(parsed.issues.first().isEssential)
        assertEquals("/10055-civil-war-2006.html",
            parsed.issues.first().batcaveSeriesPath)
    }

    @Test
    fun missingEventTieInRemainsInReadingOrderWithoutInventingSource() {
        val html = """
            <ol>
             <li>64 Daily Bugle Special Edition: Civil War #1 not on the site yet</li>
             <li><a href="/event/civil-war-2006/65">65Civil War: Front Line Issue #4</a>
                 <a href="/11677-civil-war-front-line.html">↗</a></li>
            </ol>
        """.trimIndent()
        val parsed = InkShelf2BatCaveGuides.parseIssues(html, "civil-war-2006", 1)
        assertEquals(listOf(64, 65), parsed.issues.map { it.position })
        assertEquals(null, parsed.issues.first().batcaveSeriesPath)
        assertEquals("1", parsed.issues.first().number)
    }

    @Test
    fun paginationAndUnrelatedLinkCannotSpoofAnotherEvent() {
        val html = """
            <ol>
              <li><a href="/event/blackest-night/1">1Blackest NightIssue #1</a>
                <a href="/10185-blackest-night.html">↗</a></li>
              <li><a href="/event/house-of-m/2">2House of MIssue #2</a>
                <a href="/10180-house-of-m.html">↗</a></li>
            </ol>
            <a href="/event/blackest-night/list/2/">2</a>
        """.trimIndent()
        val parsed = InkShelf2BatCaveGuides.parseIssues(html, "blackest-night", 1)
        assertEquals(1, parsed.issues.size)
        assertTrue(parsed.more)
        assertEquals("Blackest Night", parsed.issues.first().series)
    }
}
