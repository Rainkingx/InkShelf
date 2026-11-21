package eu.kanade.tachiyomi.ui.source

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Shared, private 90-day history for InkShelf's personalised picks.
 *
 * Tracks the *series title*, not a particular extension URL, so a title cannot
 * reappear immediately just because another source serves the same comic.
 * No network calls, accounts, or reading-history uploads are involved.
 */
internal object InkShelfRecommendationHistory {
    private const val PREFS = "inkshelf_picked_for_you_rotation_v1"
    private const val HISTORY_KEY = "recent_series"
    private const val WINDOW_MS = 90L * 24L * 60L * 60L * 1_000L
    private const val MAX_ENTRIES = 1_000

    fun titleKey(title: String): String =
        title.lowercase(Locale.ROOT)
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\b(?:19|20)\\d{2}\\b"), " ")
            .replace(
                Regex("\\b(?:digital|ongoing|complete|completed|english|official)\\b"),
                " ",
            )
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun entries(context: Context, now: Long): MutableMap<String, Long> {
        val result = mutableMapOf<String, Long>()
        val raw = preferences(context).getString(HISTORY_KEY, null) ?: return result
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return result
        val iterator = json.keys()
        val cutoff = now - WINDOW_MS
        while (iterator.hasNext()) {
            val key = iterator.next()
            val timestamp = json.optLong(key, 0L)
            if (key.isNotBlank() && timestamp > cutoff && timestamp <= now) {
                result[key] = timestamp
            }
        }
        return result
    }

    @Synchronized
    fun recentTitles(context: Context): Set<String> =
        entries(context, System.currentTimeMillis()).keys.toSet()

    /**
     * Call only after a recommendation was actually shown/saved. Refresh
     * failures never consume fresh titles or overwrite the previous feed.
     */
    @Synchronized
    fun record(context: Context, picks: List<DiscoverRecommendation>) {
        if (picks.isEmpty()) return
        val now = System.currentTimeMillis()
        val updated = entries(context, now)
        picks.forEach { pick ->
            val key = titleKey(pick.manga.title)
            if (key.isNotBlank()) updated[key] = now
        }
        val trimmed = updated.entries
            .sortedByDescending { it.value }
            .take(MAX_ENTRIES)
        val json = JSONObject()
        trimmed.forEach { (key, timestamp) -> json.put(key, timestamp) }
        preferences(context).edit().putString(HISTORY_KEY, json.toString()).apply()
    }
}
