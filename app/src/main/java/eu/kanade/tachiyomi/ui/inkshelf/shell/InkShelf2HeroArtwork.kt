package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Improved art for Discover's five featured comic cards, without changing the
 * source manga or reader. Metron covers are used only for an exact title match,
 * including the start year when available. Source thumbnails always remain the
 * immediate fallback if metadata is missing or the network fails.
 */
internal object InkShelf2HeroArtwork {
    private const val PREFS = "inkshelf2_hero_art_v1"
    private const val SUCCESS_TTL_MS = 14L * 24L * 60L * 60L * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .callTimeout(13, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val yearTag = Regex(
        """\s*[\(\[]((?:19|20)\d{2})(?:\s*[-–—]\s*(?:(?:19|20)\d{2}|ongoing|present)?)?\s*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val volumeTag = Regex("""\s+Vol(?:ume)?\.?\s*\d+\s*$""", RegexOption.IGNORE_CASE)

    private fun titleWithoutYear(value: String) =
        value.replace(yearTag, " ").replace(volumeTag, "").trim()

    private fun normalized(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

    private fun publicationYear(value: String): Int? =
        yearTag.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun validMetronImage(url: String): Boolean {
        if (url.length > 2_048) return false
        return runCatching {
            val parsed = Uri.parse(url)
            parsed.scheme.equals("https", ignoreCase = true) &&
                parsed.host in setOf("static.metron.cloud", "metron.cloud") &&
                !parsed.authority.orEmpty().contains("@")
        }.getOrDefault(false)
    }

    private fun token(context: Context): String? =
        InkShelf2MetronTokenStore(context).read()?.takeIf { it.isNotBlank() }
            ?: InkShelfMetronCredentials.get(context)?.takeIf { it.isNotBlank() }

    /**
     * Metron's issue endpoint includes the full image URL, so issue #1 can be
     * retrieved in a single request instead of loading entire issue lists.
     * Successful URLs are cached on the device and loaded by Coil's disk cache.
     */
    suspend fun findCover(context: Context, manga: Manga): String? = withContext(Dispatchers.IO) {
        val name = titleWithoutYear(manga.title)
        if (name.length < 3) return@withContext null
        val startYear = publicationYear(manga.title)
        val key = normalized(name) + "|" + (startYear?.toString() ?: "?")
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (prefs.getLong(key + ":expires", 0L) > now) {
            return@withContext prefs.getString(key + ":image", null)?.takeIf(::validMetronImage)
        }

        // Never fetch without the user's personal API token or expose it to an extension.
        val authToken = token(appContext) ?: return@withContext null
        val requestUrl = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("metron.cloud")
            .addPathSegment("api")
            .addPathSegment("issue")
            .addPathSegment("")
            .addQueryParameter("series_name", name)
            .addQueryParameter("number", "1")
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("Authorization", "Bearer $authToken")
            .header("Accept", "application/json")
            .header("User-Agent", "InkShelf/2.0 (hero comic art)")
            .build()

        val result = runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return@use null
                val responseText = response.body?.string() ?: return@use null
                if (responseText.length > 500_000) return@use null
                val issues = JSONObject(responseText).optJSONArray("results") ?: return@use null
                val matches = buildList {
                    for (index in 0 until issues.length()) {
                        val issue = issues.optJSONObject(index) ?: continue
                        val series = issue.optJSONObject("series") ?: continue
                        val seriesTitle = series.optString("name")
                        if (normalized(titleWithoutYear(seriesTitle)) != normalized(name)) continue
                        val candidateYear = series.optInt("year_began", 0)
                        if (startYear != null && candidateYear != startYear) continue
                        val imageUrl = issue.optString("image")
                        if (!validMetronImage(imageUrl)) continue
                        val seriesId = series.optInt("id", 0)
                        if (seriesId > 0) add(seriesId to imageUrl)
                    }
                }
                // Ambiguous reboots must not show mismatched artwork.
                if (matches.map { it.first }.distinct().size != 1) null
                else matches.firstOrNull()?.second
            }
        }.getOrNull()

        if (result != null) {
            prefs.edit()
                .putString(key + ":image", result)
                .putLong(key + ":expires", now + SUCCESS_TTL_MS)
                .apply()
        }
        // Do not cache failures, expired tokens or rate limits as missing art.
        result
    }
}
