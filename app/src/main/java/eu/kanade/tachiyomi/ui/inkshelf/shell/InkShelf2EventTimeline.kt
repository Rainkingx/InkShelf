package eu.kanade.tachiyomi.ui.inkshelf.shell

/** Publication years come from the existing verified event artwork catalogue.
 * Same-publisher suggestions are nearby releases, not claimed story sequels.
 */
internal object InkShelf2EventTimeline {
    fun ordered(guides: List<InkShelf2Guide>): List<InkShelf2Guide> =
        guides.sortedWith(compareBy<InkShelf2Guide> {
            InkShelf2GuidesData.publicationYear(it) ?: Int.MAX_VALUE
        }.thenBy { it.publisher }.thenBy { it.name })

    /** Latest Marvel, latest DC and a weekly rotating pre-2015 classic. */
    fun spotlight(guides: List<InkShelf2Guide>, week: Long = System.currentTimeMillis() / (7L * 24 * 60 * 60 * 1000)): List<InkShelf2Guide> {
        val editorial = guides.filter { it.type != "CUSTOM" && it.batcaveSlug != null }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<InkShelf2Guide> {
                InkShelf2GuidesData.publicationYear(it) ?: 0
            }.thenBy { it.name })
        val marvel = editorial.firstOrNull { it.publisher.equals("Marvel", true) }
        val dc = editorial.firstOrNull { it.publisher.equals("DC", true) }
        val classics = editorial.filter { it.id != marvel?.id && it.id != dc?.id &&
            (InkShelf2GuidesData.publicationYear(it) ?: Int.MAX_VALUE) < 2015 }
        val classic = if (classics.isEmpty()) null else classics[Math.floorMod(week, classics.size.toLong()).toInt()]
        return listOfNotNull(marvel, dc, classic)
    }

    fun related(current: InkShelf2Guide, guides: List<InkShelf2Guide>): List<InkShelf2Guide> {
        val year = InkShelf2GuidesData.publicationYear(current)
        return guides.filter { it.id != current.id && it.publisher.equals(current.publisher, true) }
            .sortedWith(compareBy<InkShelf2Guide> { guide ->
                val otherYear = InkShelf2GuidesData.publicationYear(guide)
                if (year == null || otherYear == null) Int.MAX_VALUE else kotlin.math.abs(otherYear - year)
            }.thenBy { InkShelf2GuidesData.publicationYear(it) ?: Int.MAX_VALUE }.thenBy { it.name })
            .take(4)
    }
}
