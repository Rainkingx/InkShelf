package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl
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
    private const val CACHE_PREFS = "inkshelf_metron_covers_v1"
    private const val CACHE_OK_MS = 24L * 60L * 60L * 1000L
    private const val CACHE_MISS_MS = 60L * 60L * 1000L
    private const val MIN_CALL_INTERVAL_MS = 3_500L

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

    fun requestIssueCover(
        context: Context,
        manga: Manga,
        issueNumber: Int,
        onFound: (String) -> Unit,
    ) {
        if (issueNumber !in 1..999 || !InkShelfMetronCredentials.isConfigured(context)) return
        val appContext = context.applicationContext
        val cleanTitle = seriesTitle(manga.title)
        if (cleanTitle.length < 3) return
        val year = publicationYear(manga.title)
        val key = "${normalized(cleanTitle)}|${year ?: "unknown"}"
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
            val token = InkShelfMetronCredentials.get(appContext)
            val found = if (token.isNullOrBlank()) emptyMap() else runCatching {
                lookupSeriesIssues(cleanTitle, year, token)
            }.getOrDefault(emptyMap())
            val expiresAt = System.currentTimeMillis() +
                if (found.isEmpty()) CACHE_MISS_MS else CACHE_OK_MS
            val entry = Entry(expiresAt, found)
            saveCache(appContext, key, entry)
            val listeners = synchronized(lock) {
                cache[key] = entry
                pending.remove(key).orEmpty()
            }
            withContext(Dispatchers.Main) {
                listeners.forEach { (number, callback) ->
                    entry.covers[number]?.let { cover -> callback(cover) }
                }
            }
        }
    }

    /** This same cover service can be reused by the upcoming Daily Recommendations shelf. */
    private suspend fun lookupSeriesIssues(title: String, year: Int?, token: String): Map<Int, String> {
        val searchUrl = endpoint("series")
            .newBuilder()
            .addQueryParameter("name", title)
            .build()
        val result = getJson(searchUrl, token) ?: return emptyMap()
        val candidates = result.optJSONArray("results") ?: return emptyMap()
        val matches = buildList {
            for (i in 0 until candidates.length()) {
                val candidate = candidates.optJSONObject(i) ?: continue
                val name = candidate.optString("name").ifBlank { candidate.optString("series") }
                if (normalized(seriesTitle(name)) != normalized(title)) continue
                if (year != null && candidate.optInt("year_began") != year) continue
                val id = candidate.optInt("id", 0)
                if (id > 0) add(id)
            }
        }.distinct()
        // Do not cross-match two separate publication runs.
        val seriesId = matches.singleOrNull() ?: return emptyMap()

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
            nextUrl = next?.let { runCatching { HttpUrl.get(it) }.getOrNull() }
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
                if (!response.isSuccessful) return@use null
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
        parts.forEach(builder::addPathSegment)
        return builder.addPathSegment("").build()
    }

    private fun seriesTitle(raw: String): String = raw
        .replace(Regex("""\s*[\(\[]((?:19|20)\d{2})[\)\]]\s*"""), " ")
        .replace(Regex("""\s+Vol(?:ume)?\.?\s*\d+\s*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun publicationYear(raw: String): Int? =
        Regex("""[\(\[]((?:19|20)\d{2})[\)\]]""")
            .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

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
