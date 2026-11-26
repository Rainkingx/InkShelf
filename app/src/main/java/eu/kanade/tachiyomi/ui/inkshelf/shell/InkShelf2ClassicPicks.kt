package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * A small, source-backed classic catalogue. Populate after startup, never
 * invent source links or substitute a modern edition to fill Thursday.
 */
internal object InkShelf2ClassicPicks {
    private const val PREFS = "inkshelf2_classic_picks_v1"
    private const val MAX_CACHE = 60
    private const val TARGET = 12
    private const val HOUR = 60L * 60L * 1000L
    private val seeds = listOf(
        "Fantastic Four", "Detective Comics", "Uncanny X-Men", "The Sandman",
        "Daredevil", "Superman", "Wonder Woman", "Hellboy",
        "The Amazing Spider-Man", "Action Comics", "The Incredible Hulk", "Spawn",
        "Green Lantern", "Thor", "Captain America", "Swamp Thing",
        "Iron Man", "The Flash", "Teen Titans", "Bone",
    )

    internal fun eligible(comic: Manga, enabled: Set<Long>): Boolean =
        comic.source in enabled && comic.url.isNotBlank() &&
            !comic.thumbnail_url.isNullOrBlank() &&
            (InkShelf2ComicIdentity.year(comic.title) ?: Int.MAX_VALUE) in 1900..1999

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, enabled: Set<Long>): List<Manga> = runCatching {
        val array = JSONArray(prefs(context).getString("items", "[]") ?: "[]")
        buildList {
            for (i in 0 until minOf(array.length(), MAX_CACHE)) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optLong("source", -1)
                if (id !in enabled) continue
                val manga = Manga.create(item.optString("url"), item.optString("title"), id)
                manga.thumbnail_url = item.optString("cover")
                if (eligible(manga, enabled)) add(manga)
            }
        }
    }.getOrDefault(emptyList())

    suspend fun refresh(context: Context, sources: List<CatalogueSource>): Boolean =
        withContext(Dispatchers.IO) {
            val ordered = sources.distinctBy { it.id }.sortedBy {
                InkShelf2SourceBalance.sourceOrder(it.name)
            }
            if (ordered.isEmpty()) return@withContext false
            val enabled = ordered.map { it.id }.toSet()
            val store = prefs(context)
            val signature = enabled.sorted().joinToString(",")
            val old = load(context, enabled)
            val now = System.currentTimeMillis()
            val oldCount = old.distinctBy(InkShelf2DailyRotation::seriesKey).size
            val cooldown = if (oldCount >= TARGET) 24 * HOUR else 3 * HOUR
            if (store.getString("sources", "") == signature &&
                now - store.getLong("attempt", 0L) in 0 until cooldown) return@withContext false
            store.edit().putString("sources", signature).putLong("attempt", now).apply()

            val engine = InkShelf2SourcesEngine()
            val pool = old.toMutableList()
            val offset = Math.floorMod(InkShelf2DailyRotation.localDayStamp(), seeds.size)
            val queries = seeds.drop(offset) + seeds.take(offset)
            // Bounded requests, at most eight per refresh. Try another enabled
            // catalogue when the preferred one has too few matching editions.
            val plan = queries.take(4).map { ordered[0] to it } +
                queries.drop(4).take(4).map { ordered.getOrElse(1) { ordered[0] } to it }
            for ((source, query) in plan) {
                val found = try {
                    withTimeoutOrNull(5_000L) {
                        engine.page(source, 1, query).comics
                    }.orEmpty()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    emptyList()
                }
                pool += found.filter { eligible(it, enabled) }
            }
            val merged = pool.distinctBy { "${it.source}:${it.url}" }
                .distinctBy { InkShelf2ComicIdentity.editionKey(it) }
                .takeLast(MAX_CACHE)
            if (merged.isEmpty()) return@withContext false
            val array = JSONArray()
            merged.forEach { manga ->
                array.put(JSONObject().put("source", manga.source).put("url", manga.url)
                    .put("title", manga.title).put("cover", manga.thumbnail_url.orEmpty()))
            }
            store.edit().putString("items", array.toString()).apply()
            merged.map { "${it.source}:${it.url}" } != old.map { "${it.source}:${it.url}" }
        }
}
