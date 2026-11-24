package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkShelf2GuideAutoMatchTest {
    private fun issue(series: String, year: Int?) = InkShelf2GuideIssue(
        id = 1, position = 1, series = series, volume = null,
        number = "13", role = "Optional tie-in", yearBegan = year,
    )

    @Test
    fun excaliburVolumeThreeResolvesToBatcave2004Not1988() {
        val wanted = issue("Excalibur Vol. 3", 2004)
        assertEquals(3, InkShelf2GuideAutoMatch.rank(wanted, "Excalibur (2004)"))
        assertEquals(0, InkShelf2GuideAutoMatch.rank(wanted, "Excalibur (1988)"))
        assertEquals(listOf("Excalibur (2004)", "Excalibur Vol. 3", "Excalibur"),
            InkShelf2GuideAutoMatch.queries(wanted))
    }

    @Test
    fun identicalCoreTitleCannotOpenWrongHouseOfMVolume() {
        val wanted = issue("House of M", 2005)
        assertEquals(3, InkShelf2GuideAutoMatch.rank(wanted, "House of M (2005)"))
        assertEquals(0, InkShelf2GuideAutoMatch.rank(wanted, "House of M (2015)"))
        assertEquals(0, InkShelf2GuideAutoMatch.rank(wanted, "House of M Omnibus (2005)"))
    }

    @Test
    fun matchesLongRunningSeriesWithoutGuessingOtherYears() {
        assertEquals(3, InkShelf2GuideAutoMatch.rank(
            issue("The Incredible Hulk", 2000),
            "The Incredible Hulk (2000-2008)",
        ))
        assertEquals(0, InkShelf2GuideAutoMatch.rank(
            issue("Wolverine", 2003), "Wolverine (1988-2003)",
        ))
        assertEquals(3, InkShelf2GuideAutoMatch.rank(
            issue("New X-Men", 2004), "New X-Men (2004)",
        ))
        assertEquals(0, InkShelf2GuideAutoMatch.rank(
            issue("New X-Men", 2004), "New X-Men (2001)",
        ))
    }

    @Test
    fun acceptsUnlabelledYearOnlyAfterExactNameAndIssueVerification() {
        assertEquals(1, InkShelf2GuideAutoMatch.rank(
            issue("Fantastic Four: House of M", 2005),
            "Fantastic Four: House of M",
        ))
        assertEquals(1, InkShelf2GuideAutoMatch.rank(
            issue("House of M: Avengers", 2008),
            "House of M: Avengers",
        ))
        assertEquals(0, InkShelf2GuideAutoMatch.rank(
            issue("House of M: Avengers", 2008),
            "House of M: Masters of Evil",
        ))
    }

    @Test
    fun ampersandsAndArticlesDoNotChangeTheOriginalBook() {
        assertEquals(3, InkShelf2GuideAutoMatch.rank(
            issue("Cable & Deadpool", 2004), "Cable and Deadpool (2004)",
        ))
        assertEquals(InkShelf2GuideAutoMatch.key("The Pulse (2004-2006)"),
            InkShelf2GuideAutoMatch.key("Pulse"))
        assertTrue(InkShelf2GuideAutoMatch.queries(issue("Exiles", 2001))
            .contains("Exiles (2001)"))
        assertFalse(InkShelf2GuideAutoMatch.queries(issue("Exiles", 2001))
            .contains("Exiles (2020)"))
    }
    @Test
    fun savedSeriesPathsAllowRealSlashTitlesButNotTraversalOrExternalUrls() {
        org.junit.Assert.assertTrue(InkShelf2GuideAutoMatch.isSafeSeriesPath("/3202-king-in-black-iron-man/doom.html"))
        org.junit.Assert.assertTrue(InkShelf2GuideAutoMatch.isSafeSeriesPath("/11663-civil-war-2006.html"))
        listOf("https://example.com/12-book.html", "/12-book/../other.html",
            "/12-book.html?token=secret", "/12-book//doom.html", "//example.com/12-book.html").forEach {
            org.junit.Assert.assertFalse(it, InkShelf2GuideAutoMatch.isSafeSeriesPath(it))
        }
    }

    @Test
    fun annualOneCannotMatchAnnualTenOrOneHundred() {
        org.junit.Assert.assertTrue(InkShelf2GuideAutoMatch.matchesSpecialLabel("Teen Titans _Annual 1", "Annual 1"))
        org.junit.Assert.assertFalse(InkShelf2GuideAutoMatch.matchesSpecialLabel("Teen Titans _Annual 10", "Annual 1"))
        org.junit.Assert.assertFalse(InkShelf2GuideAutoMatch.matchesSpecialLabel("Teen Titans _Annual 100", "Annual 1"))
        org.junit.Assert.assertFalse(InkShelf2GuideAutoMatch.matchesSpecialLabel("Issue #1", "Annual 1"))
    }

    @Test
    fun existingNamedSpecialsContinueToMatch() {
        org.junit.Assert.assertTrue(InkShelf2GuideAutoMatch.matchesSpecialLabel("The Pulse: House of M Special", "House of M Special"))
        org.junit.Assert.assertTrue(InkShelf2GuideAutoMatch.matchesSpecialLabel("FCBD 2024: Absolute Power", "Absolute Power"))
        org.junit.Assert.assertFalse(InkShelf2GuideAutoMatch.matchesSpecialLabel("FCBD 2024: Absolute Powerless", "Absolute Power"))
    }
}
