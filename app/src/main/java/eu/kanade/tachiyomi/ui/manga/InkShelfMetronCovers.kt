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
    private data class Listener(val number: Int, val onFound: (String) -> Unit, val onFinished: () -> Unit)
    private val pending = mutableMapOf<String, MutableList<Listener>>()
    // Covers discovered on an early Metron page stay available to newly
    // composed rows while later metadata pages are still loading.
    private val inFlightCovers = mutableMapOf<String, Map<Int, String>>()

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
        requestIssueCover(context, manga.title, issueNumber, anchorIssueNumber, onFound = onFound)
    }

    /** Metadata-only entry point for event issues; uses the same series cache as Discover. */
    fun requestIssueCover(
        context: Context,
        title: String,
        issueNumber: Int,
        anchorIssueNumber: Int? = null,
        onFinished: () -> Unit = {},
        onFound: (String) -> Unit,
    ) {
        if (issueNumber !in 1..999) {
            onFinished()
            return
        }
        // Avoid decrypting the Android Keystore token for every row on the UI
        // thread. The actual credential is read once on Dispatchers.IO.
        if (!InkShelf2MetronTokenStore(context).hasToken() &&
            !InkShelfMetronCredentials.isConfigured(context)) {
            onFinished()
            return
        }
        val appContext = context.applicationContext
        val cleanTitle = seriesTitle(title)
        if (cleanTitle.length < 3) {
            onFinished()
            return
        }
        val year = publicationYear(title)
        val anchor = anchorIssueNumber?.takeIf { it in 1..999 }
        val key = "${normalized(cleanTitle)}|${year ?: "unknown"}|${anchor ?: "unanchored"}"
        // Only a completely disambiguated title/year may be shared across different anchors.
        val sharedKey = year?.let { "${normalized(cleanTitle)}|$it|verified-unique" }
        val listener = Listener(issueNumber, onFound, onFinished)
        var cachedUrl: String? = null
        var inFlightUrl: String? = null
        var shouldFetch = false
        var hadCache = false
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val shared = sharedKey?.let { alias ->
                (cache[alias] ?: loadCache(appContext, alias)?.also { cache[alias] = it })
                    ?.takeIf { it.expiresAt > now && it.covers.containsKey(issueNumber) }
            }
            val entry = shared ?: cache[key] ?: loadCache(appContext, key)?.also { cache[key] = it }
            if (entry != null && entry.expiresAt > now) {
                hadCache = true
                cachedUrl = entry.covers[issueNumber]
            } else {
                val listeners = pending[key]
                if (listeners != null) {
                    listeners.add(listener)
                    inFlightUrl = inFlightCovers[key]?.get(issueNumber)
                } else {
                    pending[key] = mutableListOf(listener)
                    inFlightCovers[key] = emptyMap()
                    shouldFetch = true
                }
            }
        }
        if (hadCache) {
            cachedUrl?.let(onFound)
            onFinished()
            return
        }
        inFlightUrl?.let(onFound)
        if (!shouldFetch) return

        scope.launch {
            val token = configuredToken(appContext)
            // Deliver the first matching issue covers as soon as that Metron
            // page is parsed. Don't wait for up to three pages (and their
            // rate-limit pauses) to finish.
            val result = if (token.isNullOrBlank()) null else
                runCatching {
                    lookupSeriesIssues(cleanTitle, year, token, anchor) { partial ->
                        synchronized(lock) {
                            if (pending.containsKey(key)) inFlightCovers[key] = partial
                        }
                        withContext(Dispatchers.Main) {
                            val listeners = synchronized(lock) {
                                pending[key]?.toList().orEmpty()
                            }
                            listeners.forEach { listener ->
                                partial[listener.number]?.let(listener.onFound)
                            }
                        }
                    }
                }.getOrNull()
            // A bad token, outage or rate limit must not become a long-lived
            // missing-cover entry. Keep previously delivered covers visible.
            val entry = result?.let { lookup ->
                Entry(
                    System.currentTimeMillis() + if (lookup.covers.isEmpty()) CACHE_MISS_MS else CACHE_OK_MS,
                    lookup.covers,
                )
            }
            if (entry != null) saveCache(appContext, key, entry)
            val share = result?.uniqueEdition == true && entry != null && entry.covers.isNotEmpty() && sharedKey != null
            if (share && entry != null && sharedKey != null) saveCache(appContext, sharedKey, entry)
            val listeners = synchronized(lock) {
                if (entry != null) cache[key] = entry
                if (share && entry != null && sharedKey != null) cache[sharedKey] = entry
                inFlightCovers.remove(key)
                pending.remove(key).orEmpty()
            }
            withContext(Dispatchers.Main) {
                listeners.forEach { listener ->
                    entry?.covers?.get(listener.number)?.let(listener.onFound)
                    listener.onFinished()
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
    private data class SeriesLookup(val covers: Map<Int, String>, val uniqueEdition: Boolean = false)

    private suspend fun lookupSeriesIssues(
        title: String,
        year: Int?,
        token: String,
        anchorNumber: Int?,
        onPartialCovers: suspend (Map<Int, String>) -> Unit,
    ): SeriesLookup {
        val searchUrl = endpoint("series").newBuilder()
            .addQueryParameter("name", title)
            .apply { if (year != null) addQueryParameter("year_began", year.toString()) }
            .build()
        val matches = mutableSetOf<Int>()
        var nextSeries: HttpUrl? = searchUrl
        var searchPages = 0
        while (nextSeries != null && searchPages < 2) {
            val page = getJson(nextSeries, token) ?: return SeriesLookup(emptyMap())
            val candidates = page.optJSONArray("results") ?: return SeriesLookup(emptyMap())
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

        val uniqueEdition = matches.size == 1 && nextSeries == null && year != null
        val seriesId = if (matches.size == 1 && nextSeries == null) {
            matches.single()
        } else if (matches.size > 1 && nextSeries == null && anchorNumber != null) {
            // Metron supports exact issue-number filters, including the image.
            // Require ONE matching series among the already exact-title matches.
            // A reprint/alternate run that also has this issue remains ambiguous.
            val anchorUrl = endpoint("issue").newBuilder()
                .addQueryParameter("series_name", title)
                .addQueryParameter("number", anchorNumber.toString())
                .apply { if (year != null) addQueryParameter("series_year_began", year.toString()) }
                .build()
            val anchorPage = getJson(anchorUrl, token) ?: return SeriesLookup(emptyMap())
            val issues = anchorPage.optJSONArray("results") ?: return SeriesLookup(emptyMap())
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
        } ?: return SeriesLookup(emptyMap())

        val covers = mutableMapOf<Int, String>()
        var nextUrl: HttpUrl? = endpoint("series", seriesId.toString(), "issue_list")
        var pages = 0
        while (nextUrl != null && pages < 3) {
            val body = getJson(nextUrl, token) ?: break
            val issues = body.optJSONArray("results") ?: break
            var foundOnThisPage = false
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
                if (covers.putIfAbsent(number, image) == null) foundOnThisPage = true
            }
            if (foundOnThisPage) onPartialCovers(covers.toMap())
            pages++
            val next = body.optString("next").takeIf { it.startsWith("https://metron.cloud/api/") }
            nextUrl = next?.toHttpUrlOrNull()
        }
        return SeriesLookup(covers, uniqueEdition)
    }

    /** Strict, single-issue fallback for long or ambiguous series; same API throttle. */
    suspend fun exactIssueCover(context: Context, title: String, year: Int, number: Int): String? =
        withContext(Dispatchers.IO) {
            val token = configuredToken(context.applicationContext) ?: return@withContext null
            val url = endpoint("issue").newBuilder()
                .addQueryParameter("series_name", title)
                .addQueryParameter("series_year_began", year.toString())
                .addQueryParameter("number", number.toString())
                .build()
            val response = getJson(url, token) ?: return@withContext null
            if (!response.isNull("next") && response.optString("next").isNotBlank())
                return@withContext null
            val rows = response.optJSONArray("results") ?: return@withContext null
            val matches = buildList<Pair<Int, String>> {
                for (index in 0 until rows.length()) {
                    val issue = rows.optJSONObject(index) ?: continue
                    val series = issue.optJSONObject("series") ?: continue
                    val suppliedYear = series.optInt("year_began", 0)
                    if (normalized(seriesTitle(series.optString("name"))) != normalized(seriesTitle(title)) ||
                        (suppliedYear > 0 && suppliedYear != year) ||
                        issue.optString("number").trim().toIntOrNull() != number) continue
                    val id = issue.optInt("id", 0)
                    val image = issue.optString("image")
                    if (id > 0 && validImageUrl(image)) add(id to image)
                }
            }.distinct()
            matches.singleOrNull()?.second
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
