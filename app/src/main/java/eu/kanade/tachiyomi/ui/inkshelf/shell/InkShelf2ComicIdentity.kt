package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Locale

/**
 * Stable presentation identity for the SAME edition across installed sources.
 *
 * Never use this key for database deletion: a source-specific J2K Manga and
 * its chapters, categories, downloads and progress continue to belong to
 * their original source. Missing years require corroborating synopsis evidence.
 */
internal object InkShelf2ComicIdentity {
    private val yearTag = Regex(
        """\s*[\(\[]((?:19|20)\d{2})(?:\s*[-–—]\s*(?:(?:19|20)\d{2}|ongoing|present)?)?\s*[\)\]]\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    fun searchTitle(title: String): String = title.replace(yearTag, "").trim()

    fun normalizedTitle(title: String): String =
        searchTitle(title).lowercase(Locale.ROOT).replace(separators, " ").trim()

    // Automatic source switching requires a matching, explicitly dated run.
    // A yearless title is not evidence that two editions are identical.
    fun isVerifiedAlternative(requested: String, candidate: String): Boolean {
        val requestedYear = year(requested) ?: return false
        val name = normalizedTitle(requested)
        return name.isNotBlank() && name == normalizedTitle(candidate) &&
            requestedYear == year(candidate)
    }

    fun year(title: String): Int? =
        yearTag.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /**
     * Conservative edition key: same title and known publication year.
     * Yearless titles only match other yearless titles. This avoids
     * merging distinct Spider-Man or Batman reboots into one comic.
     */
    fun editionKey(title: String): String {
        val name = normalizedTitle(title)
        if (name.isEmpty()) return ""
        return name + "|" + (year(title)?.toString() ?: "unknown")
    }

    fun editionKey(manga: Manga) = editionKey(manga.title)

    private fun synopsisFingerprint(manga: Manga): String =
        manga.description.orEmpty().lowercase(Locale.ROOT)
            .replace(separators, " ").trim().take(120)

    /**
     * Sources sometimes omit the year entirely. Exact normalized title plus
     * a substantial matching synopsis is strong evidence for duplicate
     * library display, but never for automatically switching reader sources.
     * Preserve all title qualifiers (Noir, Deluxe, etc.) and conflicting years.
     */
    fun isSameEdition(left: Manga, right: Manga): Boolean {
        if (left.source == right.source && left.url.isNotBlank() && left.url == right.url) return true
        val name = normalizedTitle(left.title)
        if (name.isBlank() || name != normalizedTitle(right.title)) return false
        val leftYear = year(left.title)
        val rightYear = year(right.title)
        if (leftYear != null && rightYear != null) return leftYear == rightYear
        val synopsis = synopsisFingerprint(left)
        return synopsis.length == 120 && synopsis == synopsisFingerprint(right)
    }

    /** Keep ambiguous yearless copies separate if they match conflicting runs. */
    fun matchingLibraryCopy(candidate: Manga, existing: List<Manga>): Manga? {
        val matches = existing.filter { isSameEdition(candidate, it) }
        if (matches.mapNotNull { year(it.title) }.distinct().size > 1) return null
        return matches.firstOrNull()
    }

    /**
     * Group only mutually compatible copies. Never let a yearless row bridge
     * two conflicting known years; keep ambiguous copies separate.
     */
    fun groupLibraryCopies(entries: List<InkShelf2LibraryItem>): List<List<InkShelf2LibraryItem>> =
        entries.groupBy { normalizedTitle(it.manga.title) }.values.flatMap { sameTitle ->
            val groups = mutableListOf<MutableList<InkShelf2LibraryItem>>()
            val ordered = sameTitle.sortedBy { year(it.manga.title) == null }
            for (entry in ordered) {
                val compatible = groups.filter { group ->
                    group.all { isSameEdition(it.manga, entry.manga) }
                }
                if (compatible.size == 1) compatible.single().add(entry)
                else groups.add(mutableListOf(entry))
            }
            groups
        }

    /** Prioritise whichever saved record has the most reading progress. */
    fun preferLibraryRecord(left: InkShelf2LibraryItem, right: InkShelf2LibraryItem): InkShelf2LibraryItem =
        listOf(left, right).maxWithOrNull(
            compareBy<InkShelf2LibraryItem> { it.readChapters }
                .thenBy { it.progress ?: -1 }
                .thenBy { it.totalChapters }
                .thenBy { it.manga.id ?: Long.MAX_VALUE }
        ) ?: left
}
