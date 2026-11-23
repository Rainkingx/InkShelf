package eu.kanade.tachiyomi.ui.source

/**
 * Refreshing Picked for You should prefer titles unseen within 90 days, but a
 * limited selection of installed sources must not leave the shelf empty when
 * there are other, older titles worth showing.
 *
 * The currently visible selection is NEVER recycled by a manual refresh.
 * Title-level identity is supplied by the existing history normalizer, so
 * a different source URL or publication mirror is not a new recommendation.
 */
internal object InkShelfRecommendationRotation {
    data class Pools<T>(
        val unseen: List<T>,
        val older: List<T>,
    )

    fun <T> eligible(
        candidates: List<T>,
        titleKey: (T) -> String,
        recentHistory: Set<String>,
        visibleTitles: Set<String>,
    ): Pools<T> {
        val seen = mutableSetOf<String>()
        val unseen = mutableListOf<T>()
        val older = mutableListOf<T>()
        for (candidate in candidates) {
            val key = titleKey(candidate)
            if (key.isBlank() || key in visibleTitles || !seen.add(key)) continue
            if (key in recentHistory) older += candidate else unseen += candidate
        }
        return Pools(unseen, older)
    }
}
