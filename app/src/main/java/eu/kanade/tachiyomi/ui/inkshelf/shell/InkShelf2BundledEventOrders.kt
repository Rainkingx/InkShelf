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

    // Exact reading order transcribed from both saved event index pages.
    // Columns: source title | verified series path | position:issue:chapter ID.
    // Position 64 is explicitly unavailable; no source link is invented.
    private val civilWarRows = """
        The Amazing Spider-Man (1963-2018)|/6966-the-amazing-spider-man-1963.html|1:529:38796,2:530:38797,3:531:38798,20:532:38799,21:533:38800,31:534:38801,83:535:38802,85:536:38803,112:537:38804,116:538:38805,117:539:38806
        New Avengers: Illuminati (2006)|/22525-new-avengers-illuminati-2006.html|4:Full:155144
        The Fantastic Four (1961)|/14770-fantastic-four-1961.html|5:536:99484,6:537:99485,29:538:99486,30:539:99487,82:540:99488,97:541:99489,98:542:99490,131:543:99491
        Civil War: Opening Shot|/11680-civil-war-opening-shot.html|7:Full:77473
        Civil War (2006)|/11663-civil-war-2006.html|8:1:77401,19:2:77402,51:3:77403,52:4:77404,84:5:77405,113:6:77406,114:7:77407
        Wolverine (2003-2009)|/31569-wolverine-2003-2009.html|9:42:223503,10:43:223504,11:44:223505,12:45:223506,73:46:223507,74:47:223508,94:48:223509
        She-Hulk (2005-2009)|/25195-she-hulk-2005-2009.html|13:8:174038,28:9:174039
        X-Factor (2006)|/17419-x-factor-2006.html|14:8:117782,66:9:117783
        New Avengers (2004)|/22518-new-avengers-2005.html|15:21:155006,16:22:155007,70:23:155008,81:24:155009,90:25:155010
        New X-Men (2004)|/22582-new-x-men-2004.html|17:28:155622,41:29:155623,42:30:155624,43:31:155625
        Civil War: Front Line|/11677-civil-war-front-line.html|18:1:77456,23:2:77457,32:3:77458,65:4:77459,67:5:77460,78:6:77461,79:7:77462,91:8:77463,92:9:77464,93:10:77465,121:11:77466
        Thunderbolts (1997-2012)|/29797-thunderbolts-1997-2012.html|22:103:209010,39:104:209011,40:105:209012,99:106:209013,100:107:209014,101:108:209015
        The Sensational Spider-Man (2006-2007)|/29162-the-sensational-spider-man-2006-2007.html|24:28:203705,25:29:203706,26:30:203707,27:31:203708,87:32:203709,88:33:203710,89:34:203711
        Iron Man (2005)|/17352-iron-man-2005.html|33:13:117230,96:14:117231
        Ms. Marvel (2006)|/22152-ms-marvel-2006.html|34:6:152535,35:7:152536,36:8:152537,37:9:152538,38:10:152539
        Black Panther (2005)|/10114-black-panther-2005.html|44:18:66298,45:19:66299,46:20:66300,47:21:66301,48:22:66302,56:23:66303,105:24:66304,115:25:66305
        Civil War: X-Men|/11686-civil-war-x-men.html|49:1:77479,53:2:77480,54:3:77481,55:4:77482
        Heroes For Hire (2006)|/17185-heroes-for-hire-2006.html|50:1:115843,68:2:115844,69:3:115845
        Cable and Deadpool|/10951-cable-and-deadpool.html|57:30:72154,58:31:72155,59:32:72156
        Civil War: Young Avengers & Runaways|/11687-civil-war-young-avengers--runaways.html|60:1:77483,61:2:77484,62:3:77485,63:4:77486
        Daily Bugle Special Edition: Civil War||64:1:
        Iron Man/Captain America: Casualties of War|/17379-iron-mancaptain-america-casualties-of-war.html|71:Full:117440
        Civil War Files|/11665-civil-war-files.html|72:Full:77413
        Captain America (2004)|/11044-captain-america-2005.html|75:22:73191,76:23:73192,77:24:73193,122:25:73194
        Civil War: Choosing Sides|/11676-civil-war-choosing-sides.html|80:Full:77455
        Punisher War Journal (2007-2009)|/23757-punisher-war-journal-2007-2009.html|86:1:164133,118:2:164134,119:3:164135,120:4:164136,123:5:164137
        Civil War: War Crimes|/11685-civil-war-war-crimes.html|95:Full:77478
        Winter Soldier: Winter Kills (2007-)|/31491-winter-soldier-winter-kills-2007.html|102:Full:222668
        Blade (2006)|/10227-blade-2006.html|103:5:67167
        Civil War: The Return|/11683-civil-war-the-return.html|104:Full:77476
        Moon Knight (2006-2009)|/21999-moon-knight-2006.html|106:7:151629,107:8:151630,108:9:151631,109:10:151632,110:11:151633,111:12:151634
        Civil War: The Confession|/11681-civil-war-the-confession.html|124:Full:77474
        Civil War: The Initiative|/11682-civil-war-the-initiative.html|125:Full:77475
        Fallen Son: The Death of Captain America|/14738-fallen-son-the-death-of-captain-america.html|126:1:98454,127:2:98455,128:3:98456,129:4:98457,130:5:98458
        Civil War: Battle Damage Report|/11675-civil-war-battle-damage-report.html|132:Full:77454
    """.trimIndent()

    private val civilWarEssential = setOf(7, 8, 19, 51, 52, 84, 113, 114)

    private data class CivilWarEntry(val issue: InkShelf2GuideIssue, val chapterId: Int?)

    private val civilWar = civilWarRows.lineSequence().flatMap { line ->
        val (title, seriesPath, items) = line.split('|')
        val year = Regex("""\(((?:19|20)\d{2})""").find(title)
            ?.groupValues?.get(1)?.toIntOrNull()
        val displayTitle = title.replace(Regex("""\s*\((?:19|20)\d{2}[^)]*\)$"""), "")
        items.split(',').asSequence().map { item ->
            val (positionText, number, chapterIdText) = item.split(':')
            val position = positionText.toInt()
            val chapterId = chapterIdText.toIntOrNull()
            CivilWarEntry(
                InkShelf2GuideIssue(
                    id = position,
                    position = position,
                    series = displayTitle,
                    volume = null,
                    number = number,
                    role = if (position in civilWarEssential) "Core Issue" else "Optional tie-in",
                    yearBegan = year,
                    batcaveSeriesPath = seriesPath.takeIf { it.isNotBlank() },
                    batcaveIssuePath = chapterId?.let { "/event/civil-war-2006/$position" },
                ),
                chapterId,
            )
        }
    }.sortedBy { it.issue.position }.toList()

    // Website ID retained as metadata, not assumed to be an extension URL.
    fun civilWarChapterId(position: Int): Int? =
        civilWar.firstOrNull { it.issue.position == position }?.chapterId

    fun get(slug: String, page: Int): InkShelf2GuidePage? {
        val order = when (slug) {
            "house-of-m" -> houseOfM
            "civil-war-2006" -> civilWar.map { it.issue }
            else -> return InkShelf2SavedEventOrders.get(slug, page)
        }
        return InkShelf2GuidePage(
            issues = if (page == 1) order else emptyList(),
            more = false,
        )
    }
}
