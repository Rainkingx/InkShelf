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
    fun emptyAndAmbiguousTitlesDoNotProduceResults() {
        assertEquals(null, InkShelf2PulseMatcher.searchTerm("???"))
        assertTrue(InkShelf2PulseMatcher.related("???", listOf(item("comics news"))).isEmpty())
    }
}
