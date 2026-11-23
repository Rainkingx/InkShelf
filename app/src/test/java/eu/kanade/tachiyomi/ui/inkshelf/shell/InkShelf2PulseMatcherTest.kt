package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard against unrelated movie/franchise stories appearing beneath a comic.
 * These are pure headline-matching tests; live source/reader integration is
 * still validated by the normal Android build and device checks.
 */
class InkShelf2PulseMatcherTest {

    private fun item(title: String, date: Long = 1L, video: Boolean = false) =
        InkShelf2PulseArticle(
            title = title,
            url = "https://aiptcomics.com/${title.hashCode()}/",
            publisher = "Test publisher",
            publishedAt = date,
            imageUrl = null,
            isVideo = video,
        )

    @Test
    fun multiwordSeriesMatchesItsWholeNameOnly() {
        assertTrue(
            InkShelf2PulseMatcher.isMatch(
                "Absolute Batman (2024-)",
                item("Absolute Batman #5 review: new DC Comics issue"),
            ),
        )
        assertFalse(
            InkShelf2PulseMatcher.isMatch(
                "Absolute Batman (2024-)",
                item("Batman comic movie news and reviews"),
            ),
        )
        assertFalse(
            InkShelf2PulseMatcher.isMatch(
                "Ultimate Spider-Man (2024-)",
                item("Amazing Spider-Man issue reviews"),
            ),
        )
    }

    @Test
    fun singleWordFranchiseRequiresComicContext() {
        assertFalse(
            InkShelf2PulseMatcher.isMatch(
                "Batman (2016)",
                item("Batman movie trailer and casting updates"),
            ),
        )
        assertTrue(
            InkShelf2PulseMatcher.isMatch(
                "Batman (2016)",
                item("Batman comic issue #149 review"),
            ),
        )
    }

    @Test
    fun relatedResultsKeepTwoNewsAndTwoVideosInRecencyOrder() {
        val entries = listOf(
            item("Absolute Batman comic news 1", 100),
            item("Absolute Batman comic news 2", 200),
            item("Absolute Batman video review 1", 300, video = true),
            item("Absolute Batman video review 2", 400, video = true),
            item("Absolute Batman comic news 3", 500),
            item("Batman film trailer", 900),
        )
        val result = InkShelf2PulseMatcher.related("Absolute Batman (2024-)", entries)
        assertEquals(
            listOf(
                "Absolute Batman comic news 3",
                "Absolute Batman video review 2",
                "Absolute Batman comic news 2",
                "Absolute Batman video review 1",
            ),
            result.map { it.title },
        )
    }

    @Test
    fun guidesHeroPrefersRealNamedEventsOverSimilarSequels() {
        fun guide(id: Int, title: String, publisher: String) = InkShelf2Guide(
            id = id,
            name = title,
            type = "EVENT",
            author = "test",
            attribution = "",
            rating = 0.0,
            publisher = publisher,
        )
        val available = listOf(
            guide(1, "Civil War II", "marvel"),
            guide(2, "Civil War (2006)", "marvel"),
            guide(3, "Secret Wars (2015)", "marvel"),
            guide(4, "Crisis on Infinite Earths", "dc"),
            guide(5, "Knightfall", "dc"),
            guide(6, "Absolute Power", "dc"),
        )
        val featured = InkShelf2FeaturedGuides.match(available)
        assertEquals(
            listOf("Civil War", "Secret Wars", "Absolute Power", "Knightfall", "Crisis on Infinite Earths"),
            featured.map { it.label },
        )
        assertEquals(2, featured.first().guide.id)
        assertFalse(featured.any { it.guide.id == 1 })
    }

    @Test
    fun guideIssueDoesNotConfuseDifferentNewAvengersRuns() {
        val issue = InkShelf2GuideIssue(
            id = 10024,
            position = 9,
            series = "New Avengers",
            volume = 1,
            number = "24",
            role = "Tie-In",
            yearBegan = 2004,
        )
        assertEquals("New Avengers (2004) #24", issue.displayName)
        assertEquals(
            InkShelf2GuideEdition.Match.MATCHING,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2004)"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.MATCHING,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2004–2010)"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.OTHER_RUN,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2013)"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.UNVERIFIED,
            InkShelf2GuideEdition.classify(issue, "New Avengers"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.OTHER_TITLE,
            InkShelf2GuideEdition.classify(issue, "The New Avengers (2004)"),
        )
        assertEquals("New Avengers", InkShelf2GuideEdition.sourceSearchQuery(issue))
    }

    @Test
    fun emptyAndAmbiguousTitlesDoNotProduceResults() {
        assertEquals(null, InkShelf2PulseMatcher.searchTerm("???"))
        assertTrue(InkShelf2PulseMatcher.related("???", listOf(item("comics news"))).isEmpty())
    }
}
