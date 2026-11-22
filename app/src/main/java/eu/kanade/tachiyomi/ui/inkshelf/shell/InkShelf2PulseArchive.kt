package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.net.Uri
import android.text.Html
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * An optional, on-device one-year index for comic-specific news. The normal
 * Pulse Home feed stays small and current. Only titles/links/dates and
 * publisher-provided artwork are stored; no article bodies are downloaded.
 *
 * Fresh RSS news and YouTube entries are gradually accumulated in the index.
 * For older comic-specific articles, each publisher's public WordPress API
 * is searched by series name, with a one-day per-title cooldown. A blocked
 * or unsupported publisher API simply yields no results.
 *
 * Public YouTube channel RSS has no historical search endpoint; older videos
 * only become available after they have been seen by the app's live feeds.
 */
internal object InkShelf2PulseArchive {
    private const val PREFS = "inkshelf2_pulse_archive_v1"
    private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000
    private const val SEARCH_TTL_MS = 24L * 60 * 60 * 1000
    private const val MAX_NEWS = 650
    private const val MAX_VIDEOS = 180
    private val lock = Any()

    private data class Publisher(val name: String, val host: String)

    private val publishers = listOf(
        Publisher("AIPT Comics", "aiptcomics.com"),
        Publisher("The Beat", "comicsbeat.com"),
    )

    suspend fun cached(context: Context): List<InkShelf2PulseArticle> = withContext(Dispatchers.IO) {
        synchronized(lock) { read(context.applicationContext) }
    }

    suspend fun record(
        context: Context,
        entries: List<InkShelf2PulseArticle>,
    ): List<InkShelf2PulseArticle> = withContext(Dispatchers.IO) {
        synchronized(lock) { merge(context.applicationContext, entries) }
    }

    suspend fun searchForComic(
        context: Context,
        comicTitle: String,
    ): List<InkShelf2PulseArticle> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val series = InkShelf2PulseMatcher.searchTerm(comicTitle)
        if (series == null) return@withContext cached(appContext)

        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val searchKey = "searched_" + series.lowercase(Locale.ROOT).hashCode()
        val prior = prefs.getLong(searchKey, 0L)
        if (now - prior in 0L until SEARCH_TTL_MS) return@withContext cached(appContext)

        // Mark before fetching: rapid navigation never queues a burst of
        // duplicate historical lookups for the same title.
        prefs.edit().putLong(searchKey, now).apply()
        val result = coroutineScope {
            publishers.map { publisher ->
                async(Dispatchers.IO) {
                    try {
                        queryPublisher(publisher, series, now - YEAR_MS)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        record(appContext, result)
    }

    private fun queryPublisher(
        publisher: Publisher,
        series: String,
        earliest: Long,
    ): List<InkShelf2PulseArticle> {
        val after = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(earliest))
        val uri = Uri.Builder()
            .scheme("https")
            .authority(publisher.host)
            .appendPath("wp-json")
            .appendPath("wp")
            .appendPath("v2")
            .appendPath("posts")
            .appendQueryParameter("search", series)
            .appendQueryParameter("after", after)
            .appendQueryParameter("per_page", "50")
            .appendQueryParameter("orderby", "date")
            .appendQueryParameter("order", "desc")
            .appendQueryParameter("_embed", "wp:featuredmedia")
            .appendQueryParameter("_fields", "link,title,date_gmt,date,jetpack_featured_media_url,_embedded")
            .build()
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 6_000
            connection.setRequestProperty("User-Agent", "InkShelf/2.0 editorial RSS reader")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return emptyList()

            // Cap the response to avoid retaining unexpectedly large API bodies.
            val payload = connection.inputStream.bufferedReader().use { it.readText().take(850_000) }
            val rows = JSONArray(payload)
            val output = mutableListOf<InkShelf2PulseArticle>()
            val now = System.currentTimeMillis()
            for (index in 0 until minOf(rows.length(), 50)) {
                val row = rows.optJSONObject(index) ?: continue
                val url = row.optString("link")
                if (!InkShelf2PulseFeed.isApprovedArticleLink(url)) continue
                val rendered = row.optJSONObject("title")?.optString("rendered").orEmpty()
                val title = Html.fromHtml(rendered, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                if (title.isBlank()) continue
                val date = row.optString("date_gmt").ifBlank { row.optString("date") }
                val publishedAt = try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                        isLenient = false
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(date)?.time ?: 0L
                } catch (_: Exception) { 0L }
                if (publishedAt !in earliest..now) continue

                val media = row.optJSONObject("_embedded")
                    ?.optJSONArray("wp:featuredmedia")?.optJSONObject(0)
                val image = row.optString("jetpack_featured_media_url").ifBlank {
                    media?.optString("source_url").orEmpty()
                }.takeIf { it.startsWith("https://", ignoreCase = true) }

                val candidate = InkShelf2PulseArticle(
                    title = title,
                    url = url,
                    publisher = publisher.name,
                    publishedAt = publishedAt,
                    imageUrl = image,
                )
                // WordPress search includes the body; only keep posts whose
                // public headlines specifically match the actual series.
                if (InkShelf2PulseMatcher.isMatch(series, candidate)) output += candidate
            }
            return output
        } finally {
            connection.disconnect()
        }
    }

    private fun merge(
        context: Context,
        incoming: List<InkShelf2PulseArticle>,
    ): List<InkShelf2PulseArticle> {
        val now = System.currentTimeMillis()
        val eligible = (read(context) + incoming)
            .filter { it.publishedAt in (now - YEAR_MS)..now }
            .filter { InkShelf2PulseFeed.isApprovedArticleLink(it.url) }
            .distinctBy { it.url }

        val news = eligible.filterNot { it.isVideo }
            .sortedByDescending { it.publishedAt }.take(MAX_NEWS)
        val videos = eligible.filter { it.isVideo }
            .sortedByDescending { it.publishedAt }.take(MAX_VIDEOS)
        val output = (news + videos).sortedByDescending { it.publishedAt }
        val json = JSONArray()
        output.forEach { article ->
            json.put(JSONObject().apply {
                put("title", article.title)
                put("url", article.url)
                put("publisher", article.publisher)
                put("publishedAt", article.publishedAt)
                put("imageUrl", article.imageUrl)
                put("video", article.isVideo)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("entries", json.toString()).apply()
        return output
    }

    private fun read(context: Context): List<InkShelf2PulseArticle> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("entries", "[]") ?: "[]"
        return try {
            val now = System.currentTimeMillis()
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                val row = json.optJSONObject(index) ?: return@mapNotNull null
                val url = row.optString("url")
                val publishedAt = row.optLong("publishedAt")
                if (publishedAt !in (now - YEAR_MS)..now ||
                    !InkShelf2PulseFeed.isApprovedArticleLink(url)
                ) return@mapNotNull null

                InkShelf2PulseArticle(
                    title = row.optString("title"),
                    url = url,
                    publisher = row.optString("publisher"),
                    publishedAt = publishedAt,
                    imageUrl = row.optString("imageUrl").takeIf {
                        it.startsWith("https://", ignoreCase = true)
                    },
                    isVideo = row.optBoolean("video", false),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
