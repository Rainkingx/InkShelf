package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkShelf2EventTimelineTest {
    private val events = InkShelf2BatCaveGuides.verified()

    @Test
    fun arcArtworkUsesActualComicSeries() {
        val targets = mapOf(
            "batman-zero-year" to Triple("Batman", 2011, "21"),
            "death-of-the-family" to Triple("Batman", 2011, "13"),
            "night-of-the-owls" to Triple("Batman", 2011, "8"),
            "spider-verse" to Triple("The Amazing Spider-Man", 2014, "9"),
            "x-men-original-sin" to Triple("Wolverine: Origins", 2006, "28"),
        )
        targets.forEach { (slug, expected) ->
            val target = InkShelf2GuidesData.eventCoverTarget(slug)!!
            assertEquals(expected, Triple(target.title, target.year, target.number))
        }
    }

    @Test
    fun verifiedCatalogueUsesPublicationOrder() {
        val sorted = InkShelf2EventTimeline.ordered(events.reversed())
        assertEquals(events.map { it.id }.toSet(), sorted.map { it.id }.toSet())
        val years = sorted.mapNotNull(InkShelf2GuidesData::publicationYear)
        assertEquals(events.size, years.size)
        assertEquals(years.sorted(), years)
    }

    @Test
    fun additionalOrdersKeepAllPositionsAndShortPaths() {
        val added = InkShelf2AdditionalEventOrders.events
        assertEquals(13, added.size)
        assertEquals(727, added.sumOf { it.completeCount })
        assertEquals(7, added.sumOf { event -> event.entries.count { it.issue.sourceUnavailable } })
        added.forEach { event ->
            val order = InkShelf2BundledEventOrders.get(event.slug, 1)!!.issues
            assertEquals((1..event.completeCount).toList(), order.map { it.position })
            assertEquals(event.essentialPositions, order.filter { it.isEssential }.map { it.position }.toSet())
        }
    }

    @Test
    fun relatedEventsKeepPublisherAndExcludeCurrent() {
        val current = events.first { it.batcaveSlug == "civil-war-2006" }
        val related = InkShelf2EventTimeline.related(current, events)
        assertTrue(related.isNotEmpty())
        assertTrue(related.all { it.publisher == current.publisher && it.id != current.id })
        assertTrue(related.mapNotNull { it.batcaveSlug }.contains("house-of-m"))
    }
}
