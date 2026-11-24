package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkShelf2EventTimelineTest {
    private val events = InkShelf2BatCaveGuides.verified()

    @Test
    fun verifiedCatalogueUsesPublicationOrder() {
        val sorted = InkShelf2EventTimeline.ordered(events.reversed())
        assertEquals(events.map { it.id }.toSet(), sorted.map { it.id }.toSet())
        val years = sorted.mapNotNull(InkShelf2GuidesData::publicationYear)
        assertEquals(events.size, years.size)
        assertEquals(years.sorted(), years)
    }

    @Test
    fun relatedEventsKeepPublisherAndExcludeCurrent() {
        val current = events.first { it.batcaveSlug == "civil-war-2006" }
        val related = InkShelf2EventTimeline.related(current, events)
        assertTrue(related.isNotEmpty())
        assertTrue(related.all { it.publisher == current.publisher && it.id != current.id })
        assertEquals("secret-invasion", related.first().batcaveSlug)
    }
}
