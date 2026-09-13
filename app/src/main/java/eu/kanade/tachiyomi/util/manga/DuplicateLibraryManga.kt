package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga

/**
 * Ids of the [mangas] that share a title with a library entry from a *different* source, i.e. the
 * ones browse should badge as already being in the library under another extension.
 */
fun DatabaseHelper.duplicateLibraryMangaIds(mangas: List<Manga>): Set<Long> {
    val candidates = mangas.filterNot { it.favorite }
    if (candidates.isEmpty()) return emptySet()

    val sourcesByTitle =
        getDuplicateLibraryMangas(candidates)
            .executeAsBlocking()
            .groupBy({ it.title.lowercase() }, { it.source })
    if (sourcesByTitle.isEmpty()) return emptySet()

    return candidates
        .filter { manga ->
            sourcesByTitle[manga.title.lowercase()]?.any { it != manga.source } == true
        }.mapNotNull { it.id }
        .toSet()
}
