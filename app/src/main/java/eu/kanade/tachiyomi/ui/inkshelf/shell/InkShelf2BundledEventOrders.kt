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
        val specialLabel: String? = null,
    )

    private const val HOUSE_OF_M_PATH = "/18298-house-of-m-2005.html"

    private val houseOfM = listOf(
        Slice("Excalibur", 13, 14, 2004, "/14576-excalibur-2004.html"),
        Slice("House of M", 1, 2, 2005, HOUSE_OF_M_PATH),
        Slice("Civil War: House of M", 1, 5, 2008),
        Slice("House of M: Masters of Evil", 1, 4, 2009, "/18303-house-of-m-masters-of-evil.html"),
        Slice("House of M: Avengers", 1, 5, 2008),
        Slice("The Incredible Hulk", 83, 83, 2000, "/28273-the-incredible-hulk-2000-2008.html"),
        Slice("The Pulse", 1, 1, 2004, "/28952-the-pulse-2004-2006.html", "House of M Special"),
        Slice("Fantastic Four: House of M", 1, 3, 2005),
        Slice("Spider-Man: House of M", 1, 5, 2005, "/25992-spider-man-house-of-m-2005.html"),
        Slice("The Incredible Hulk", 84, 86, 2000, "/28273-the-incredible-hulk-2000-2008.html"),
        Slice("Iron Man: House of M", 1, 3, 2005, "/17397-iron-man-house-of-m.html"),
        Slice("Mutopia X", 1, 3, 2005, "/22231-mutopia-x.html"),
        Slice("New Thunderbolts", 11, 11, 2005),
        Slice("Uncanny X-Men", 462, 465, 1963, "/6967-uncanny-x-men-1963.html"),
        Slice("House of M", 3, 3, 2005, HOUSE_OF_M_PATH),
        Slice("New X-Men", 16, 19, 2004, "/22582-new-x-men-2004.html"),
        Slice("Wolverine", 33, 35, 2003, "/31569-wolverine-2003-2009.html"),
        Slice("House of M", 4, 5, 2005, HOUSE_OF_M_PATH),
        Slice("The Pulse", 10, 10, 2004, "/28952-the-pulse-2004-2006.html"),
        Slice("Captain America", 10, 10, 2004),
        Slice("Black Panther", 7, 7, 2005),
        Slice("Exiles", 69, 71, 2001),
        Slice("Cable and Deadpool", 17, 17, 2004, "/10951-cable-and-deadpool.html"),
        Slice("House of M", 6, 7, 2005, HOUSE_OF_M_PATH),
        Slice("Mutopia X", 4, 4, 2005, "/22231-mutopia-x.html"),
        Slice("House of M", 8, 8, 2005, HOUSE_OF_M_PATH),
        Slice("Giant-Size Ms. Marvel", 1, 1, 2006, null, "One-shot"),
    ).flatMap { slice ->
        (slice.first..slice.last).map { number -> slice to number }
    }.mapIndexed { index, (slice, number) ->
        val position = index + 1
        InkShelf2GuideIssue(
            id = position,
            position = position,
            series = slice.title,
            volume = null,
            number = if (slice.specialLabel != null) "Full" else number.toString(),
            issueLabel = slice.specialLabel,
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
