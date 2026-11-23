package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * Editorial discovery shortlist, NOT a fabricated comic reading order.
 * Only real public Metron lists are shown. A guide's event era comes from
 * its event identity, never from the earliest series appearing in its issues.
 */
internal object InkShelf2GuideFocus {
    internal enum class Era { MODERN, CLASSIC }
    internal data class Event(
        val title: String,
        val publisher: String,
        val year: Int,
        val era: Era,
        // Secret Wars is a different event in 1984 and 2015. Do not
        // categorise the undated name as either one without evidence.
        val needsExplicitYear: Boolean = false,
    )
    private fun modern(title: String, publisher: String, year: Int, explicit: Boolean = false) =
        Event(title, publisher, year, Era.MODERN, explicit)
    private fun classic(title: String, publisher: String, year: Int) =
        Event(title, publisher, year, Era.CLASSIC)

    val events: List<Event> = listOf(
        modern("Civil War", "marvel", 2006),
        modern("House of M", "marvel", 2005),
        modern("Secret Invasion", "marvel", 2008),
        modern("World War Hulk", "marvel", 2007),
        modern("Siege", "marvel", 2010),
        modern("Fear Itself", "marvel", 2011),
        modern("Avengers vs X-Men", "marvel", 2012),
        modern("Infinity", "marvel", 2013),
        modern("Original Sin", "marvel", 2014),
        modern("Secret Wars", "marvel", 2015, explicit = true),
        modern("Secret Empire", "marvel", 2017),
        modern("War of the Realms", "marvel", 2019),
        modern("King in Black", "marvel", 2020),
        modern("Devil's Reign", "marvel", 2021),
        modern("A.X.E. Judgment Day", "marvel", 2022),
        modern("Blood Hunt", "marvel", 2024),
        modern("Identity Crisis", "dc", 2004),
        modern("Infinite Crisis", "dc", 2005),
        modern("Final Crisis", "dc", 2008),
        modern("Blackest Night", "dc", 2009),
        modern("Flashpoint", "dc", 2011),
        modern("Forever Evil", "dc", 2013),
        modern("Dark Nights Metal", "dc", 2017),
        modern("Dark Nights Death Metal", "dc", 2020),
        modern("Dark Crisis on Infinite Earths", "dc", 2022),
        modern("Lazarus Planet", "dc", 2023),
        modern("Knight Terrors", "dc", 2023),
        modern("Absolute Power", "dc", 2024),
        classic("Crisis on Infinite Earths", "dc", 1985),
        classic("Knightfall", "dc", 1993),
        classic("Infinity Gauntlet", "marvel", 1991),
        classic("The Death of Superman", "dc", 1992),
        classic("Zero Hour", "dc", 1994),
        classic("No Man's Land", "dc", 1999),
    )

    private val punctuation = Regex("""[^\p{L}\p{N}]+""")
    private val years = Regex("""\b(?:19|20)\d{2}\b""")
    private val suffix = Regex(
        """^(?:reading order|reading list|event|guide|complete|story arc|crossover|comics)(?:\s.*)?$""",
    )
    private fun key(value: String): String =
        value.lowercase(Locale.ROOT).replace(punctuation, " ").trim()

    /** Only display a known event when the name is unambiguous. */
    fun identify(guide: InkShelf2Guide): Event? {
        val name = key(guide.name)
        val statedYears = years.findAll(guide.name).mapNotNull { it.value.toIntOrNull() }.toList()
        return events.firstOrNull { event ->
            if (guide.publisher.isNotBlank() &&
                !guide.publisher.equals(event.publisher, ignoreCase = true)
            ) return@firstOrNull false
            if (statedYears.any { it != event.year }) return@firstOrNull false
            if (event.needsExplicitYear && event.year !in statedYears) return@firstOrNull false
            val title = key(event.title)
            val withoutPublisher = name
                .removePrefix("marvel ")
                .removePrefix("dc ")
                .removePrefix("the ")
            val expectedName = if (event.year in statedYears)
                withoutPublisher.replace(Regex("""\b(?:19|20)\d{2}\b"""), "").trim()
            else withoutPublisher
            expectedName == title ||
                (expectedName.startsWith("$title ") &&
                    suffix.matches(expectedName.substring(title.length).trim()))
        }
    }

    fun choose(guides: List<InkShelf2Guide>, era: Era? = null): List<InkShelf2Guide> =
        guides.distinctBy { it.id }.filter { identify(it)?.let { event ->
            era == null || event.era == era
        } == true }.sortedWith(
            compareBy<InkShelf2Guide> { g -> events.indexOf(identify(g)) }
                .thenByDescending { it.rating },
        ).distinctBy { identify(it)?.let { e -> e.publisher + ":" + e.title + ":" + e.year } }

    /**
     * Resolve a small handful of missing, named modern events lazily.
     * Prevents the home shelves being dominated by the API's first-page
     * entries, while limiting Metron requests and preserving its order data.
     */
    suspend fun load(
        context: Context,
        initial: List<InkShelf2Guide>,
        onUpdate: (List<InkShelf2Guide>) -> Unit,
    ) {
        val collected = initial.toMutableList()
        onUpdate(choose(collected))
        // Keep this batch small. Other known events remain discoverable
        // through the user's explicit guide title search.
        val priority = listOf(
            "Civil War", "Absolute Power", "Blood Hunt", "Secret Invasion",
            "Blackest Night", "Flashpoint", "King in Black", "House of M",
            "Knightfall", "Crisis on Infinite Earths",
        )
        for (name in priority) {
            val event = events.first { it.title == name }
            if (choose(collected).any { identify(it) == event }) continue
            val remote = try {
                InkShelf2GuidesData.search(context, event.title, event.publisher)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }
            val matched = remote.filter { identify(it) == event }
            if (matched.isNotEmpty()) {
                collected += matched
                onUpdate(choose(collected))
            }
        }
    }
}
