package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Locale

/**
 * Stable presentation identity for the SAME edition across installed sources.
 *
 * Never use this key for database deletion: a source-specific J2K Manga and
 * its chapters, categories, downloads and progress continue to belong to
 * their original source. Unknown years are never merged with known years.
 */
internal object InkShelf2ComicIdentity {
    private val yearTag = Regex(
        """\s*[\(\[]((?:19|20)\d{2})(?:\s*[-–—]\s*(?:(?:19|20)\d{2}|ongoing|present)?)?\s*[\)\]]\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    fun normalizedTitle(title: String): String =
        title.replace(yearTag, "").lowercase(Locale.ROOT).replace(separators, " ").trim()

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

    fun isSameEdition(left: Manga, right: Manga): Boolean =
        editionKey(left).isNotBlank() && editionKey(left) == editionKey(right)

    /** Prioritise whichever saved record has the most reading progress. */
    fun preferLibraryRecord(left: InkShelf2LibraryItem, right: InkShelf2LibraryItem): InkShelf2LibraryItem =
        listOf(left, right).maxWithOrNull(
            compareBy<InkShelf2LibraryItem> { it.readChapters }
                .thenBy { it.progress ?: -1 }
                .thenBy { it.totalChapters }
                .thenBy { it.manga.id ?: Long.MAX_VALUE }
        ) ?: left
}
