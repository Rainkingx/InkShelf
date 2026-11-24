package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Verified Metron user reading lists, not generated story orders. */
internal data class InkShelf2Guide(
    val id: Int,
    val name: String,
    val type: String,
    val author: String,
    val attribution: String,
    val rating: Double,
    val publisher: String = "",
    val imageUrl: String? = null,
    val batcaveSlug: String? = null,
)

internal data class InkShelf2GuideIssue(
    val id: Int,
    val position: Int,
    val series: String,
    val volume: Int?,
    val number: String,
    val role: String,
    // Metron volume is a volume NUMBER, not a publication year.
    val yearBegan: Int? = null,
    val seriesId: Int? = null,
    val batcaveSeriesPath: String? = null,
    val batcaveIssuePath: String? = null,
) {
    val isEssential: Boolean get() =
        role.equals("Core Issue", true) || role.equals("Prologue", true) ||
            role.equals("Epilogue", true) || role.isBlank()
    val displayName: String get() = series + (yearBegan?.let { " ($it)" } ?: "") +
        (if (number.isBlank()) "" else if (number == "Full") " · One-shot" else " #" + number)
}

internal data class InkShelf2GuidePage(
    val issues: List<InkShelf2GuideIssue>,
    val more: Boolean,
)

internal object InkShelf2GuidesData {
    private const val PREFS = "inkshelf2_guides_v1"
    private const val LIST_CACHE_MS = 6 * 60 * 60 * 1000L
    private const val ISSUE_CACHE_MS = 24 * 60 * 60 * 1000L
    private const val MAX_BYTES = 450_000
    private const val COVER_CACHE_MS = 7L * 24 * 60 * 60 * 1000
    private const val COVER_MISS_CACHE_MS = 2L * 60 * 60 * 1000
    private const val SEARCH_CACHE_MS = 30L * 60 * 1000
    private val networkMutex = Mutex()
    private val coverMutex = Mutex()

    fun configured(context: Context): Boolean =
        InkShelf2MetronTokenStore(context).hasToken() ||
            InkShelfMetronCredentials.isConfigured(context)

    private fun token(context: Context): String? =
        InkShelf2MetronTokenStore(context).read()?.takeIf(String::isNotBlank)
            ?: InkShelfMetronCredentials.get(context)?.takeIf(String::isNotBlank)

    suspend fun lists(context: Context): List<InkShelf2Guide> = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cached = prefs.getString("lists", null)
        val updated = prefs.getLong("lists_time", 0L)
        if (cached != null && now - updated in 0L until LIST_CACHE_MS) {
            return@withContext parseLists(cached)
        }
        val secret = token(context) ?: return@withContext parseLists(cached ?: "[]")
        try {
            val fetched = listOf("marvel", "dc").flatMap { publisher ->
                try {
                    parseLists(
                        get(
                            "https://metron.cloud/api/reading_list/?is_private=false&list_type=EVENT&publisher=" +
                                Uri.encode(publisher),
                            secret,
                        ),
                    ).map { it.copy(publisher = publisher) }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    emptyList()
                }
            }.distinctBy { it.id }
            if (fetched.isNotEmpty()) {
                val rows = JSONArray()
                fetched.forEach { guide ->
                    rows.put(JSONObject().apply {
                        put("id", guide.id)
                        put("name", guide.name)
                        put("type", guide.type)
                        put("author", guide.author)
                        put("attribution", guide.attribution)
                        put("rating", guide.rating)
                        put("publisher", guide.publisher)
                        put("image", guide.imageUrl ?: "")
                    })
                }
                prefs.edit().putString("lists", rows.toString()).putLong("lists_time", now).apply()
                fetched
            } else parseLists(cached ?: "[]")
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            parseLists(cached ?: "[]")
        }
    }

    suspend fun issues(context: Context, id: Int, page: Int): InkShelf2GuidePage =
        withContext(Dispatchers.IO) {
            if (id <= 0 || page !in 1..30) return@withContext InkShelf2GuidePage(emptyList(), false)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "issues_" + id + "_" + page
            val stored = prefs.getString(key, null)
            val time = prefs.getLong(key + "_time", 0L)
            if (stored != null && System.currentTimeMillis() - time in 0L until ISSUE_CACHE_MS)
                return@withContext parseIssues(stored)

            val secret = token(context) ?: return@withContext parseIssues(stored ?: "{}")
            try {
                val json = get(
                    "https://metron.cloud/api/reading_list/" + id + "/items/?page=" + page,
                    secret,
                )
                val result = parseIssues(json)
                prefs.edit().putString(key, json)
                    .putLong(key + "_time", System.currentTimeMillis()).apply()
                result
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                parseIssues(stored ?: "{}")
            }
        }

    /**
     * A real list-specific cover is preferred. If the creator did not upload
     * one, try the first core issue (or first listed issue) once, then use
     * the UI's decorative fallback. Every request is rate-limited and cached.
     */
    suspend fun coverUrl(context: Context, guide: InkShelf2Guide): String? =
        withContext(Dispatchers.IO) {
            guide.imageUrl?.let { imageUrl(it) }?.let { return@withContext it }
            if (guide.id <= 0) return@withContext null
            coverMutex.withLock {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val key = "cover_" + guide.id
                val cached = prefs.getString(key, null)
                val updatedAt = prefs.getLong(key + "_time", 0L)
                val age = System.currentTimeMillis() - updatedAt
                val ttl = if (cached.isNullOrEmpty()) COVER_MISS_CACHE_MS else COVER_CACHE_MS
                if (cached != null && age in 0L until ttl) {
                    return@withLock imageUrl(cached)
                }
                val secret = token(context) ?: return@withLock imageUrl(cached)
                try {
                    val detail = JSONObject(
                        get("https://metron.cloud/api/reading_list/" + guide.id + "/", secret),
                    )
                    var found = imageUrl(detail.optString("image"))
                    if (found == null) {
                        val page = issues(context, guide.id, 1).issues
                        val first = page.firstOrNull { it.isEssential } ?: page.firstOrNull()
                        if (first != null) {
                            val issue = JSONObject(
                                get("https://metron.cloud/api/issue/" + first.id + "/", secret),
                            )
                            found = imageUrl(issue.optString("image"))
                        }
                    }
                    prefs.edit().putString(key, found ?: "")
                        .putLong(key + "_time", System.currentTimeMillis()).apply()
                    found
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // Temporary outage or rate limit: retain a prior good URL.
                    prefs.edit().putString(key, cached ?: "")
                        .putLong(key + "_time", System.currentTimeMillis()).apply()
                    imageUrl(cached)
                }
            }
        }

    /**
     * Search Metron by name, not just the small initial Marvel/DC carousel
     * snapshots. Remote search returns genuine public event list records.
     * Failures fall back to a short-lived cached response.
     */
    suspend fun search(context: Context, text: String, publisher: String? = null): List<InkShelf2Guide> =
        withContext(Dispatchers.IO) {
            val query = text.trim().take(80)
            if (query.length < 2) return@withContext emptyList()
            val validPublisher = publisher?.lowercase(java.util.Locale.ROOT)
                ?.takeIf { it == "marvel" || it == "dc" }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "search_" + query.lowercase(java.util.Locale.ROOT).hashCode() +
                "_" + (validPublisher ?: "all")
            val cached = prefs.getString(key, null)
            val time = prefs.getLong(key + "_time", 0L)
            if (cached != null && System.currentTimeMillis() - time in 0L until SEARCH_CACHE_MS) {
                return@withContext parseLists(cached)
            }
            val secret = token(context) ?: return@withContext parseLists(cached ?: "[]")
            try {
                val json = get(
                    "https://metron.cloud/api/reading_list/?name=" + Uri.encode(query) +
                        "&is_private=false&list_type=EVENT" +
                        (validPublisher?.let { "&publisher=" + Uri.encode(it) } ?: ""),
                    secret,
                )
                val found = parseLists(json).map { guide ->
                    if (validPublisher != null) guide.copy(publisher = validPublisher) else guide
                }
                val rows = JSONArray()
                found.forEach { guide ->
                    rows.put(JSONObject().apply {
                        put("id", guide.id)
                        put("name", guide.name)
                        put("type", guide.type)
                        put("author", guide.author)
                        put("attribution", guide.attribution)
                        put("rating", guide.rating)
                        put("publisher", guide.publisher)
                        put("image", guide.imageUrl ?: "")
                    })
                }
                prefs.edit().putString(key, rows.toString())
                    .putLong(key + "_time", System.currentTimeMillis()).apply()
                found
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                parseLists(cached ?: "[]")
            }
        }

    // Never load arbitrary artwork URLs supplied by a third-party list.
    private fun imageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        return raw.trim().takeIf {
            uri.scheme.equals("https", true) &&
                (host == "metron.cloud" || host.endsWith(".metron.cloud"))
        }
    }

    /**
     * Reading-list entries include a series ID but can omit year_began.
     * Resolve the official start year only when the user selects an issue.
     * Cache by Metron series ID; never mistake volume=1 for 2004/2005.
     */
    suspend fun resolveIssueEdition(
        context: Context,
        issue: InkShelf2GuideIssue,
    ): InkShelf2GuideIssue = withContext(Dispatchers.IO) {
        if (issue.yearBegan != null || issue.seriesId == null)
            return@withContext issue
        val id = issue.seriesId
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cacheKey = "series_year_" + id
        val cached = prefs.getInt(cacheKey, 0)
        if (cached in 1900..2100) return@withContext issue.copy(yearBegan = cached)
        val secret = token(context) ?: return@withContext issue
        try {
            val series = JSONObject(get("https://metron.cloud/api/series/" + id + "/", secret))
            val year = series.optInt("year_began", 0)
                .takeIf { it in 1900..2100 }
                ?: Regex("""\(((?:19|20)\d{2})\)""")
                    .find(series.optString("series"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (year != null) {
                prefs.edit().putInt(cacheKey, year).apply()
                issue.copy(yearBegan = year)
            } else issue
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // The source and reading guide remain usable without Metron.
            issue
        }
    }

    fun readIds(context: Context, guideId: Int): Set<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("read_" + guideId, emptySet())?.toSet().orEmpty()

    fun setRead(context: Context, guideId: Int, issueId: Int, read: Boolean): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = readIds(context, guideId).toMutableSet()
        if (read) updated += issueId.toString() else updated -= issueId.toString()
        prefs.edit().putStringSet("read_" + guideId, updated).apply()
        return updated
    }

    fun startedGuides(context: Context): Set<Int> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("started", emptySet())?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty()

    fun start(context: Context, guideId: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(
            "started",
            (startedGuides(context) + guideId).map(Int::toString).toSet(),
        ).apply()
    }

    private fun parseLists(json: String): List<InkShelf2Guide> = try {
        val rows = if (json.trim().startsWith("[")) JSONArray(json)
            else JSONObject(json).optJSONArray("results") ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optInt("id", 0)
            val name = row.optString("name").trim()
            if (id <= 0 || name.isBlank() || row.optBoolean("is_private", false))
                return@mapNotNull null
            InkShelf2Guide(
                id = id,
                name = name,
                type = row.optString("list_type", "Event"),
                author = row.optJSONObject("user")?.optString("username")
                    ?: row.optString("author"),
                attribution = row.optString("attribution_source"),
                rating = (if (row.has("rating")) row.optDouble("rating", 0.0)
                    else row.optDouble("average_rating", 0.0)).takeUnless(Double::isNaN) ?: 0.0,
                publisher = row.optString("publisher"),
                imageUrl = imageUrl(row.optString("image")),
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun parseIssues(json: String): InkShelf2GuidePage = try {
        val root = JSONObject(json)
        val rows = root.optJSONArray("results") ?: JSONArray()
        val items = (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val issue = item.optJSONObject("issue") ?: return@mapNotNull null
            val series = issue.optJSONObject("series") ?: return@mapNotNull null
            val issueId = issue.optInt("id", 0)
            val title = series.optString("name").trim()
            val number = issue.optString("number").trim()
            if (issueId <= 0 || title.isEmpty() || number.isEmpty()) return@mapNotNull null
            InkShelf2GuideIssue(
                id = issueId,
                position = item.optInt("order", index + 1),
                series = title,
                volume = series.optInt("volume", 0).takeIf { it > 0 },
                number = number,
                role = item.optString("issue_type"),
                yearBegan = series.optInt("year_began", 0)
                    .takeIf { it in 1900..2100 }
                    ?: Regex("""\(((?:19|20)\d{2})\)""")
                        .find(issue.optString("issue"))
                        ?.groupValues?.getOrNull(1)?.toIntOrNull(),
                seriesId = series.optInt("id", 0).takeIf { it > 0 },
            )
        }.sortedBy { it.position }
        InkShelf2GuidePage(items, !root.isNull("next") && root.optString("next").isNotBlank())
    } catch (_: Exception) { InkShelf2GuidePage(emptyList(), false) }

    private suspend fun get(url: String, token: String): String = networkMutex.withLock {
        delay(850)
        require(url.startsWith("https://metron.cloud/api/reading_list/") ||
            url.startsWith("https://metron.cloud/api/issue/") ||
            url.startsWith("https://metron.cloud/api/series/"))
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer " + token)
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("Metron unavailable")
            conn.inputStream.buffered().use { input ->
                val buffer = ByteArray(4_096)
                val output = java.io.ByteArrayOutputStream()
                while (output.size() < MAX_BYTES) {
                    val n = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - output.size()))
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                }
                output.toString("UTF-8")
            }
        } finally { conn.disconnect() }
    }
}
