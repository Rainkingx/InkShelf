package eu.kanade.tachiyomi.ui.inkshelf.shell

/** Publication years come from the existing verified event artwork catalogue.
 * Same-publisher suggestions are nearby releases, not claimed story sequels.
 */
internal object InkShelf2EventTimeline {
    fun ordered(guides: List<InkShelf2Guide>): List<InkShelf2Guide> =
        guides.sortedWith(compareBy<InkShelf2Guide> {
            InkShelf2GuidesData.publicationYear(it) ?: Int.MAX_VALUE
        }.thenBy { it.publisher }.thenBy { it.name })

    /** Keep the spotlight stable while artwork loads, newest published events first. */
    fun spotlight(guides: List<InkShelf2Guide>): List<InkShelf2Guide> =
        guides.filter { it.type != "CUSTOM" && it.batcaveSlug != null }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<InkShelf2Guide> {
                InkShelf2GuidesData.publicationYear(it) ?: 0
            }.thenBy { it.name })
            .take(3)

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
