package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Locale

/**
 * Display-only balancing for Discover and Home. Source URLs and Manga IDs
 * are never rewritten, and each suggested card stays backed by its engine.
 */
internal object InkShelf2SourceBalance {
    /** Source ranking is a tie-breaker, never evidence that an issue works. */
    private fun priority(sourceName: String): Int {
        val n = sourceName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        return when {
            n.contains("readcomicsonlinelol") -> 0
            n.contains("comichubfree") -> 1
            n.contains("batcave") -> 2
            n.contains("readcomicsonline") || n.contains("readcomiconline") -> 4
            n.contains("xoxocomics") || n.contains("xoxo") -> 6
            else -> 3
        }
    }

    fun sourceOrder(name: String?): Int = priority(name.orEmpty())

    fun balance(
        comics: List<Manga>,
        sourceNames: Map<Long, String>,
        library: List<Manga> = emptyList(),
    ): List<Manga> {
        if (comics.isEmpty()) return emptyList()
        val favourites = library.filter { it.favorite }
        val savedByEdition = favourites
            .groupBy { InkShelf2ComicIdentity.editionKey(it) }
            .filterKeys { it.isNotBlank() }

        // For every edition, keep the reader-backed original source. If the
        // edition is already in the library, preserve that source in Discover.
        val unique = comics
            .filter { it.title.isNotBlank() && it.url.isNotBlank() }
            .groupBy {
                InkShelf2ComicIdentity.editionKey(it).ifBlank { "${it.source}:${it.url}" }
            }.values.mapNotNull { choices ->
                val linkedSavedIds = savedByEdition[InkShelf2ComicIdentity.editionKey(choices.first())]
                    .orEmpty().map { it.source to it.url }.toSet()
                choices.minWithOrNull(
                    compareBy<Manga> { if ((it.source to it.url) in linkedSavedIds) 0 else 1 }
                        .thenBy { priority(sourceNames[it.source].orEmpty()) }
                        .thenBy { comics.indexOf(it) },
                )
            }
        // Round-robin between sources rather than letting whichever source
        // fills the feed first dominate the hero carousel.
        val bySource = unique.groupBy { it.source }
            .mapValues { (_, entries) -> entries.toMutableList() }
            .toMutableMap()
        val sourceOrder = bySource.keys.sortedWith(
            compareBy<Long> { priority(sourceNames[it].orEmpty()) }.thenBy { it },
        )
        val result = ArrayList<Manga>(unique.size)
        while (result.size < unique.size) {
            for (source in sourceOrder) {
                val queue = bySource[source] ?: continue
                if (queue.isNotEmpty()) result += queue.removeAt(0)
            }
        }
        return result
    }

    fun balanceDiscover(
        discover: InkShelf2Engine.Discover,
        sourceNames: Map<Long, String>,
        library: List<Manga>,
    ): InkShelf2Engine.Discover {
        val trending = balance(discover.trending, sourceNames, library)
        val newest = balance(discover.newest, sourceNames, library)
        val ordered = balance(discover.recommendationDetails.map { it.manga }, sourceNames, library)
        val picksBySource = discover.recommendationDetails.associateBy {
            it.manga.source to it.manga.url
        }
        val seen = (trending.take(20) + newest.take(20)).map { InkShelf2DailyRotation.seriesKey(it) }.toSet()
        val orderedPicks = ordered.mapNotNull { picksBySource[it.source to it.url] }
        // Keep the live feed ordering intact. Prefer fresh series in personal picks.
        val picks = orderedPicks.sortedBy { pick ->
            val manga = pick.manga
            when {
                InkShelf2DailyRotation.isInvincible(manga) -> 2
                InkShelf2DailyRotation.seriesKey(manga) in seen -> 1
                else -> 0
            }
        }
        return discover.copy(
            trending = trending,
            newest = newest,
            recommended = picks.map { it.manga },
            recommendationDetails = picks,
        )
    }
}
