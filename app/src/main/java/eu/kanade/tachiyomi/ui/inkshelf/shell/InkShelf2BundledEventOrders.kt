package eu.kanade.tachiyomi.ui.inkshelf.shell

/**
 * Offline House of M pilot: issue-order facts from ComicBookReadingOrders'
 * House of M (2005) event list, also used by BatCave's event catalogue.
 * The native BatCave extension, not this index, supplies the actual pages.
 *
 * Keeping the reading order local means a 401 / bot check on BatCave's event
 * website cannot leave the guide empty even though comics remain readable.
 */
internal object InkShelf2BundledEventOrders {
    private data class Slice(
        val title: String,
        val first: Int,
        val last: Int = first,
        val yearBegan: Int? = null,
        val batcaveSeriesPath: String? = null,
    )

    private const val HOUSE_OF_M_PATH = "/18298-house-of-m-2005.html"

    private val houseOfM = listOf(
        Slice("Excalibur Vol. 3", 13, 14),
        Slice("House of M", 1, 2, 2005, HOUSE_OF_M_PATH),
        Slice("Civil War: House of M", 1, 5, 2008),
        Slice("House of M: Masters of Evil", 1, 4, 2009),
        Slice("House of M: Avengers", 1, 5),
        Slice("Incredible Hulk Vol. 2", 83),
        Slice("The Pulse House of M Special", 1),
        Slice("Fantastic Four: House of M", 1, 3, 2005),
        Slice("Spider-Man: House of M", 1, 5, 2005),
        Slice("Incredible Hulk Vol. 2", 84, 86),
        Slice("Iron Man: House of M", 1, 3, 2005),
        Slice("Mutopia X", 1, 3, 2005),
        Slice("New Thunderbolts", 11),
        Slice("Uncanny X-Men", 462, 465),
        Slice("House of M", 3, 3, 2005, HOUSE_OF_M_PATH),
        Slice("New X-Men Vol. 2", 16, 19),
        Slice("Wolverine Vol. 3", 33, 35),
        Slice("House of M", 4, 5, 2005, HOUSE_OF_M_PATH),
        Slice("The Pulse", 10),
        Slice("Captain America Vol. 5", 10),
        Slice("Black Panther Vol. 4", 7),
        Slice("Exiles", 69, 71),
        Slice("Cable & Deadpool", 17),
        Slice("House of M", 6, 7, 2005, HOUSE_OF_M_PATH),
        Slice("Mutopia X", 4, 4, 2005),
        Slice("House of M", 8, 8, 2005, HOUSE_OF_M_PATH),
        Slice("Giant-Size Ms. Marvel", 1),
    ).flatMap { slice ->
        (slice.first..slice.last).map { number -> slice to number }
    }.mapIndexed { index, (slice, number) ->
        val position = index + 1
        InkShelf2GuideIssue(
            id = position,
            position = position,
            series = slice.title,
            volume = null,
            number = number.toString(),
            role = if (slice.title == "House of M" && number in 1..8)
                "Core Issue" else "Optional tie-in",
            yearBegan = slice.yearBegan,
            batcaveSeriesPath = slice.batcaveSeriesPath,
            // Reading-order identity only; not passed to the extension as a
            // chapter URL. Direct reading uses the actual series/chapter IDs.
            batcaveIssuePath = "/event/house-of-m/$position",
        )
    }

    fun get(slug: String, page: Int): InkShelf2GuidePage? {
        if (slug != "house-of-m") return null
        return InkShelf2GuidePage(
            issues = if (page == 1) houseOfM else emptyList(),
            more = false,
        )
    }
}
