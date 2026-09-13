package eu.kanade.tachiyomi.ui.library.search

import eu.kanade.tachiyomi.ui.library.LibraryItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun QueryNode.matches(item: LibraryItem): Boolean =
    when (this) {
        is AndNode -> children.all { it.matches(item) }
        is OrNode -> children.any { it.matches(item) }
        is NotNode -> !child.matches(item)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
        is ComparisonQueryNode -> matches(item)
    }

private fun GeneralQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.manga

    val match =
        MangaField.entries.any { field ->
            if (field.fieldOnly) return@any false

            when (field) {
                MangaField.TITLE -> manga.title.contains(value, ignoreCase = true)
                MangaField.AUTHOR -> manga.author?.contains(value, ignoreCase = true) ?: false
                MangaField.ARTIST -> manga.artist?.contains(value, ignoreCase = true) ?: false
                MangaField.GENRE -> item.matchesGenreOrType(value)
                MangaField.SOURCE -> item.matchesSourceName(value)

                // field-only queries; unreachable; added here to make `when` exhaustive
                MangaField.DESCRIPTION,
                MangaField.LANGUAGE,
                MangaField.SOURCE_ID,
                MangaField.SERIES_TYPE,
                -> error("How did we get here?")
            }
        }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.manga

    val match =
        when (field) {
            MangaField.GENRE -> {
                if (value.isEmpty()) {
                    manga.getGenres().isNullOrEmpty()
                } else {
                    item.matchesGenreOrType(value)
                }
            }

            MangaField.SOURCE -> {
                if (value.isEmpty()) {
                    item.sourceName.isEmpty()
                } else {
                    item.matchesSourceName(value)
                }
            }

            MangaField.SOURCE_ID -> {
                value.toLongOrNull()?.let { it == manga.source } ?: false
            }

            MangaField.SERIES_TYPE -> {
                value.isNotEmpty() && item.matchesSeriesType(value)
            }

            else -> {
                val text =
                    when (field) {
                        MangaField.TITLE -> manga.title
                        MangaField.AUTHOR -> manga.author
                        MangaField.ARTIST -> manga.artist
                        MangaField.DESCRIPTION -> manga.description
                        MangaField.LANGUAGE -> item.searchLanguage()

                        // unreachable; added here to make `when` exhaustive
                        MangaField.GENRE, MangaField.SOURCE, MangaField.SOURCE_ID, MangaField.SERIES_TYPE ->
                            error("How did we get here?")
                    }

                if (value.isEmpty()) {
                    text.isNullOrEmpty()
                } else {
                    text?.contains(value, ignoreCase = true) ?: false
                }
            }
        }

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.manga

    fun compareDates(
        timestamp: Long,
        value: String,
    ): Boolean? {
        val inputDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val mangaDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return queryComparator.apply(mangaDate, inputDate)
    }

    val match =
        when (field) {
            ComparisonField.ID -> manga.id?.let { id -> value.toLongOrNull()?.let { queryComparator.apply(id, it) } }

            ComparisonField.DATE_ADDED -> compareDates(manga.date_added, value)

            ComparisonField.UNREAD -> value.toIntOrNull()?.let { queryComparator.apply(manga.unread, it) }

            ComparisonField.READ -> value.toIntOrNull()?.let { queryComparator.apply(manga.read, it) }

            ComparisonField.TOTAL -> value.toIntOrNull()?.let { queryComparator.apply(manga.totalChapters, it) }

            ComparisonField.SCORE ->
                value.toFloatOrNull()?.let {
                    // A score of 0 means "no score data" (untracked, or tracked but unrated),
                    // not "rated 0" - exclude it from more-than/less-than matches so those
                    // manga don't leak in, but still allow an explicit `score=0` to find them.
                    if (queryComparator == Comparator.EQ) {
                        queryComparator.apply(manga.score, it)
                    } else {
                        manga.score > 0 && queryComparator.apply(manga.score, it)
                    }
                }
        } ?: false

    return if (negated) !match else match
}
