package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.inkshelf.shell.InkShelf2MetronTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Optional, user-authorized issue covers from Metron.
 *
 * A series is accepted only with a strict title match AND (where available)
 * its publication start year. If several different volumes match and no year
 * is known, use the extension cover instead. No guesswork and no scraping.
 *
 * The series issue list is fetched once and cached for a day rather than
 * asking Metron for every visible RecyclerView row.
 */
internal object InkShelfMetronCovers {
    // Bump version so old failed title matches cannot keep masking correct covers.
    private const val CACHE_PREFS = "inkshelf_metron_covers_v3"
    private const val CACHE_OK_MS = 24L * 60L * 60L * 1000L
    private const val CACHE_MISS_MS = 60L * 60L * 1000L
    private const val MIN_CALL_INTERVAL_MS = 3_500L

    private class MetronApiException(val status: Int) : Exception("Metron returned HTTP $status")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val callMutex = Mutex()
    private var lastCallAt = 0L
    private val lock = Any()
    private val cache = mutableMapOf<String, Entry>()
    private val pending = mutableMapOf<String, MutableList<Pair<Int, (String) -> Unit>>>()

    private data class Entry(
        val expiresAt: Long,
        val covers: Map<Int, String>,
    )

    private fun configuredToken(context: Context): String? =
        // InkShelf 2.0 has its own Keystore-backed credential form. Prefer it
        // while preserving existing InkShelf 1 issue cover settings.
        InkShelf2MetronTokenStore(context).read()?.takeIf(String::isNotBlank)
            ?: InkShelfMetronCredentials.get(context)?.takeIf(String::isNotBlank)

    fun requestIssueCover(
        context: Context,
        manga: Manga,
        issueNumber: Int,
        anchorIssueNumber: Int? = null,
        onFound: (String) -> Unit,
    ) {
        if (issueNumber !in 1..999) return
        if (configuredToken(context) == null) return
        val appContext = context.applicationContext
        val cleanTitle = seriesTitle(manga.title)
        if (cleanTitle.length < 3) return
        val year = publicationYear(manga.title)
        val anchor = anchorIssueNumber?.takeIf { it in 1..999 }
        val key = "${normalized(cleanTitle)}|${year ?: "unknown"}|${anchor ?: "unanchored"}"
        var cachedUrl: String? = null
        var shouldFetch = false
        var hadCache = false
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val entry = cache[key] ?: loadCache(appContext, key)?.also { cache[key] = it }
            if (entry != null && entry.expiresAt > now) {
                hadCache = true
                cachedUrl = entry.covers[issueNumber]
            } else {
                val listeners = pending[key]
                if (listeners != null) {
                    listeners.add(issueNumber to onFound)
                } else {
                    pending[key] = mutableListOf(issueNumber to onFound)
                    shouldFetch = true
                }
            }
        }
        if (hadCache) {
            cachedUrl?.let(onFound)
            return
        }
        if (!shouldFetch) return

        scope.launch {
            val token = configuredToken(appContext)
            // A bad token, outage, or rate limit must not become a long-lived
            // "no matching covers" result in the cache.
            val result = if (token.isNullOrBlank()) null else
                runCatching { lookupSeriesIssues(cleanTitle, year, token, anchor) }.getOrNull()
            val entry = result?.let { covers ->
                Entry(
                    System.currentTimeMillis() + if (covers.isEmpty()) CACHE_MISS_MS else CACHE_OK_MS,
                    covers,
                )
            }
            if (entry != null) saveCache(appContext, key, entry)
            val listeners = synchronized(lock) {
                if (entry != null) cache[key] = entry
                pending.remove(key).orEmpty()
            }
            if (entry != null) {
                withContext(Dispatchers.Main) {
                    listeners.forEach { (number, callback) ->
                        entry.covers[number]?.let { cover -> callback(cover) }
                    }
                }
            }
        }
    }

    /**
     * A safe, user-triggered health check. Reports HTTP errors and confirmed
     * matches without exposing the user's token in logs or messages.
     */
    suspend fun testConnection(context: Context): String = withContext(Dispatchers.IO) {
        val token = InkShelfMetronCredentials.get(context)
            ?: return@withContext "No Metron token saved. Add one first."
        try {
            val search = endpoint("series").newBuilder()
                .addQueryParameter("name", "Absolute Wonder Woman")
                .addQueryParameter("year_began", "2024")
                .build()
            val response = getJson(search, token)
                ?: return@withContext "Connected to Metron, but received no series data."
            val results = response.optJSONArray("results")
                ?: return@withContext "Connected to Metron, but series data was unavailable."
            val ids = buildList {
                for (index in 0 until results.length()) {
                    val candidate = results.optJSONObject(index) ?: continue
                    val title = candidate.optString("series")
                        .ifBlank { candidate.optString("name") }
                    if (normalized(seriesTitle(title)) != "absolute wonder woman") continue
                    if (candidate.optInt("year_began") != 2024) continue
                    val id = candidate.optInt("id")
                    if (id > 0) add(id)
                }
            }.distinct()
            if (ids.isEmpty()) {
                return@withContext "Metron token works, but no exact Absolute Wonder Woman (2024) series was found."
            }
            if (ids.size > 1) {
                return@withContext "Metron token works, but multiple matching series need disambiguation."
            }
            val issueResponse = getJson(
                endpoint("series", ids.single().toString(), "issue_list"),
                token,
            ) ?: return@withContext "Series matched, but Metron returned no issue list."
            val issues = issueResponse.optJSONArray("results")
                ?: return@withContext "Series matched, but Metron returned no issue data."
            var validCovers = 0
            for (index in 0 until issues.length()) {
                val issue = issues.optJSONObject(index) ?: continue
                if (validImageUrl(issue.optString("image"))) validCovers++
            }
            "Metron connected. Absolute Wonder Woman (2024) matched: $validCovers issue covers on the first page."
        } catch (error: MetronApiException) {
            when (error.status) {
                401 -> "Metron rejected the token (HTTP 401). Check or regenerate it."
                403 -> "Metron denied access (HTTP 403). Check API permissions."
                429 -> "Metron rate limit reached (HTTP 429). Try again later."
                else -> "Metron request failed (HTTP ${error.status})."
            }
        } catch (_: Exception) {
            "Could not connect to Metron. Check your internet connection and retry."
        }
    }

    /**
     * Match the exact series name and start year where present. When the name
     * belongs to multiple publication runs, verify the highest named issue
     * that the installed source actually lists. This avoids guessing which
     * Invincible, Batman or X-Men volume corresponds to the reader chapters.
     */
    private suspend fun lookupSeriesIssues(
        title: String,
        year: Int?,
        token: String,
        anchorNumber: Int?,
    ): Map<Int, String> {
        val searchUrl = endpoint("series").newBuilder()
            .addQueryParameter("name", title)
            .apply { if (year != null) addQueryParameter("year_began", year.toString()) }
            .build()
        val matches = mutableSetOf<Int>()
        var nextSeries: HttpUrl? = searchUrl
        var searchPages = 0
        while (nextSeries != null && searchPages < 2) {
            val page = getJson(nextSeries, token) ?: return emptyMap()
            val candidates = page.optJSONArray("results") ?: return emptyMap()
            for (i in 0 until candidates.length()) {
                val item = candidates.optJSONObject(i) ?: continue
                val itemTitle = item.optString("series")
                    .ifBlank { item.optString("name") }
                if (normalized(seriesTitle(itemTitle)) != normalized(title)) continue
                if (year != null && item.optInt("year_began") != year) continue
                val id = item.optInt("id", 0)
                if (id > 0) matches.add(id)
            }
            searchPages++
            val next = page.optString("next")
                .takeIf { it.startsWith("https://metron.cloud/api/series/") }
            nextSeries = next?.toHttpUrlOrNull()
        }

        val seriesId = if (matches.size == 1) {
            matches.single()
        } else if (matches.size > 1 && anchorNumber != null) {
            // Metron supports exact issue-number filters, including the image.
            // Require ONE matching series among the already exact-title matches.
            // A reprint/alternate run that also has this issue remains ambiguous.
            val anchorUrl = endpoint("issue").newBuilder()
                .addQueryParameter("series_name", title)
                .addQueryParameter("number", anchorNumber.toString())
                .apply { if (year != null) addQueryParameter("series_year_began", year.toString()) }
                .build()
            val anchorPage = getJson(anchorUrl, token) ?: return emptyMap()
            val issues = anchorPage.optJSONArray("results") ?: return emptyMap()
            val matchingSeries = mutableSetOf<Int>()
            for (i in 0 until issues.length()) {
                val issue = issues.optJSONObject(i) ?: continue
                if (issue.optString("number").trim() != anchorNumber.toString()) continue
                val series = issue.optJSONObject("series") ?: continue
                val id = series.optInt("id", 0)
                if (id !in matches) continue
                if (normalized(seriesTitle(series.optString("name"))) != normalized(title)) continue
                if (year != null && series.optInt("year_began") != year) continue
                matchingSeries.add(id)
            }
            matchingSeries.singleOrNull()
        } else {
            null
        } ?: return emptyMap()

        val covers = mutableMapOf<Int, String>()
        var nextUrl: HttpUrl? = endpoint("series", seriesId.toString(), "issue_list")
        var pages = 0
        while (nextUrl != null && pages < 3) {
            val body = getJson(nextUrl, token) ?: break
            val issues = body.optJSONArray("results") ?: break
            for (i in 0 until issues.length()) {
                val issue = issues.optJSONObject(i) ?: continue
                val number = issue.optString("number")
                    .trim()
                    .removePrefix("#")
                    .toIntOrNull() ?: continue
                if (number !in 1..999) continue
                val image = issue.optString("image")
                if (!validImageUrl(image)) continue
                // Standard cover only, never a guessed variant.
                covers.putIfAbsent(number, image)
            }
            pages++
            val next = body.optString("next").takeIf { it.startsWith("https://metron.cloud/api/") }
            nextUrl = next?.toHttpUrlOrNull()
        }
        return covers
    }

    private suspend fun getJson(url: HttpUrl, token: String): JSONObject? {
        callMutex.lock()
        try {
            val remaining = MIN_CALL_INTERVAL_MS - (System.currentTimeMillis() - lastCallAt)
            if (remaining > 0) delay(remaining)
            lastCallAt = System.currentTimeMillis()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("User-Agent", "InkShelf/2.0 (comic metadata)")
                .build()
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw MetronApiException(response.code)
                }
                val text = response.body?.string() ?: return@use null
                JSONObject(text)
            }
        } finally {
            callMutex.unlock()
        }
    }

    private fun endpoint(vararg parts: String): HttpUrl {
        val builder = HttpUrl.Builder().scheme("https").host("metron.cloud")
            .addPathSegment("api")
        parts.forEach { builder.addPathSegment(it) }
        return builder.addPathSegment("").build()
    }

    // Supports (2024), (2024-), (2024–), and (2024–Ongoing), as
    // commonly returned by Western comic reading extensions.
    private val seriesYearTag = Regex(
        """\s*[\(\[]((?:19|20)\d{2})(?:\s*[-–—]\s*(?:(?:19|20)\d{2}|ongoing|present)?)?\s*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )

    private fun seriesTitle(raw: String): String = raw
        .replace(seriesYearTag, " ")
        .replace(Regex("""\s+Vol(?:ume)?\.?\s*\d+\s*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun publicationYear(raw: String): Int? =
        seriesYearTag.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun normalized(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

    private fun validImageUrl(url: String): Boolean =
        url.length < 2048 && runCatching {
            val parsed = Uri.parse(url)
            parsed.scheme.equals("https", ignoreCase = true) &&
                !parsed.host.isNullOrBlank() && !parsed.authority.orEmpty().contains("@")
        }.getOrDefault(false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    private fun loadCache(context: Context, key: String): Entry? = runCatching {
        val raw = prefs(context).getString(key, null) ?: return null
        val root = JSONObject(raw)
        val covers = root.optJSONObject("covers") ?: JSONObject()
        val map = buildMap<Int, String> {
            val keys = covers.keys()
            while (keys.hasNext()) {
                val number = keys.next().toIntOrNull() ?: continue
                val url = covers.optString(number.toString())
                if (validImageUrl(url)) put(number, url)
            }
        }
        Entry(root.optLong("expires", 0L), map)
    }.getOrNull()

    private fun saveCache(context: Context, key: String, entry: Entry) {
        val images = JSONObject()
        entry.covers.forEach { (number, url) -> images.put(number.toString(), url) }
        val data = JSONObject().put("expires", entry.expiresAt).put("covers", images)
        prefs(context).edit().putString(key, data.toString()).apply()
    }
}
