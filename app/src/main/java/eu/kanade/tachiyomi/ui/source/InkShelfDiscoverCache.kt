package eu.kanade.tachiyomi.ui.source

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import org.json.JSONArray
import org.json.JSONObject

/** Small app-private stale-while-revalidate cache for the Discover shelves. */
internal object InkShelfDiscoverCache {
    enum class Section(
        val itemsKey: String,
        val timestampKey: String,
    ) {
        PICKED_FOR_YOU("picked_for_you", "picked_for_you_at"),
        TRENDING("trending", "trending_at"),
        NEW_THIS_WEEK("new_this_week", "new_this_week_at"),
    }

    private const val PREFS = "inkshelf_discover_cache_v1"

    fun save(
        context: Context,
        section: Section,
        items: List<DiscoverRecommendation>,
    ) {
        if (items.isEmpty()) return

        val array = JSONArray()
        items.forEach { item ->
            val manga = item.manga
            array.put(
                JSONObject()
                    .put("source", manga.source)
                    .put("url", manga.url)
                    .put("title", manga.title)
                    .put("cover", manga.thumbnail_url.orEmpty())
                    .put("reason", item.reason),
            )
        }

        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(section.itemsKey, array.toString())
            .putLong(section.timestampKey, System.currentTimeMillis())
            .apply()
    }

    fun lastUpdatedAt(context: Context, section: Section): Long =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(section.timestampKey, 0L)

    /**
     * Restores cached cards without touching the network. Manga rows are resolved from J2K's DB;
     * if a row has been pruned, a minimal local row is recreated so covers/details still open.
     */
    fun load(
        context: Context,
        section: Section,
        db: DatabaseHelper,
        allowedSourceIds: Set<Long>? = null,
    ): List<DiscoverRecommendation> {
        val raw =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(section.itemsKey, null)
                ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val sourceId = json.optLong("source", -1L)
                    val url = json.optString("url").trim()
                    val title = json.optString("title").trim()
                    val cover = json.optString("cover").trim().takeIf(String::isNotBlank)
                    val reason = json.optString("reason").trim()
                    if (sourceId < 0L || url.isBlank() || title.isBlank()) continue
                    // Ignore paused/hidden sources BEFORE resolving cache rows.
                    // Displaying recommendations must never recreate stale
                    // disabled-source manga in the underlying J2K database.
                    if (allowedSourceIds != null && sourceId !in allowedSourceIds) continue

                    var manga = db.getManga(url, sourceId).executeAsBlocking()
                    if (manga == null) {
                        val created = Manga.create(url, title, sourceId)
                        created.thumbnail_url = cover
                        val result = db.insertManga(created).executeAsBlocking()
                        created.id = result.insertedId()
                        manga = created
                    } else if (!manga.favorite && manga.thumbnail_url.isNullOrBlank() && cover != null) {
                        manga.thumbnail_url = cover
                    }

                    add(DiscoverRecommendation(manga, reason))
                }
            }
        }.getOrDefault(emptyList())
    }
}
