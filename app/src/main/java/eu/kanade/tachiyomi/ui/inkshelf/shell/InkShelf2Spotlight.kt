package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Editorial Discover Spotlight is deliberately independent of extension popular/latest lists.
 *
 * A small verified Marvel/DC lineup lives in editorial/spotlight.json on our own GitHub
 * branch. That file can be updated without a new APK. It is not a comics source:
 * tapping a feature searches the user's enabled extensions for the exact run instead.
 */
internal object InkShelf2Spotlight {
    data class Feature(
        val id: String,
        val title: String,
        val year: Int,
        val publisher: String,
        val label: String,
        val caption: String,
        val releaseDate: String,
        val featuredFrom: String,
        val expires: String,
        val officialUrl: String,
    ) {
        val editionTitle: String get() = title + " (" + year + ")"
        fun isUpcoming(today: String): Boolean = releaseDate > today
    }

    enum class Edition { VERIFIED, UNVERIFIED, DIFFERENT_RUN }

    private const val URL =
        "https://raw.githubusercontent.com/Rainkingx/InkShelf/inkshelf-2.0/editorial/spotlight.json"
    private const val CACHE = "inkshelf2_editorial_spotlight_v1"
    private const val REFRESH_MS = 6L * 60L * 60L * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun utcToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    // Offline/first-launch fallback. The manifest is the updatable source of truth.
    private val starter = listOf(
        Feature("midnight-xmen", "Midnight X-Men", 2026, "Marvel", "MIDNIGHT UNIVERSE",
            "A bold new X-Men world", "2026-10-07", "2026-11-01", "2027-05-31",
            "https://www.marvel.com/articles/comics/midnight-universe-series-announcement-fall-2026"),
        Feature("absolute-batman", "Absolute Batman", 2024, "DC", "ABSOLUTE UNIVERSE",
            "DC's reimagined Dark Knight", "2024-10-09", "2026-11-01", "2027-06-30",
            "https://www.dc.com/allin"),
        Feature("midnight-spiderman", "Midnight Spider-Man", 2026, "Marvel", "MIDNIGHT UNIVERSE",
            "A darker take on Spider-Man", "2026-10-07", "2026-11-01", "2027-05-31",
            "https://www.marvel.com/articles/comics/midnight-universe-series-announcement-fall-2026"),
        Feature("teen-titans", "Teen Titans", 2026, "DC", "DC NEXT LEVEL",
            "A new generation of Titans", "2026-09-02", "2026-11-01", "2027-04-30",
            "https://www.dc.com/blog/2026-06-17/dc-announces-three-new-next-level-comic-book-series"),
        Feature("avengers", "Avengers", 2026, "Marvel", "NEW MARVEL ERA",
            "A new Avengers team rises", "2026-11-01", "2026-11-01", "2027-06-30",
            "https://www.marvel.com/articles/comics/avengers-1-assembles-earths-mightiest-survivors-november-2026"),
        Feature("doom-patrol", "The Doom Patrol", 2026, "DC", "DC NEXT LEVEL",
            "The World's Strangest Heroes return", "2026-09-02", "2026-11-01", "2027-04-30",
            "https://www.dc.com/blog/2026-06-17/dc-announces-three-new-next-level-comic-book-series"),
        Feature("ultimate-finale", "Ultimate Universe: Finale", 2026, "Marvel", "ULTIMATE UNIVERSE",
            "The closing chapter of the Ultimate saga", "2026-04-01", "2026-11-01", "2027-02-28",
            "https://share.marvel.com/comics/discover/2529/ultimate-endgame"),
    )

    fun fallback(): List<Feature> = active(starter)

    private fun active(list: List<Feature>): List<Feature> {
        val today = utcToday()
        return list.filter { it.featuredFrom <= today && it.expires >= today }
            .distinctBy { it.publisher + "|" + it.title + "|" + it.year }
            .take(16)
    }

    fun edition(feature: Feature, sourceTitle: String): Edition {
        if (InkShelf2GuideEdition.titleKey(feature.title) !=
            InkShelf2GuideEdition.titleKey(sourceTitle)) return Edition.DIFFERENT_RUN
        val year = InkShelf2GuideEdition.year(sourceTitle)
        return when (year) {
            feature.year -> Edition.VERIFIED
            null -> Edition.UNVERIFIED
            else -> Edition.DIFFERENT_RUN
        }
    }

    suspend fun load(context: Context): List<Feature> = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val cached = prefs.getString("manifest", null)
        val lastCheck = prefs.getLong("checked", 0L)
        val now = System.currentTimeMillis()
        if (cached != null && now - lastCheck in 0 until REFRESH_MS) {
            val entries = parse(cached)
            if (entries.isNotEmpty()) return@withContext active(entries)
        }

        val remote = runCatching {
            client.newCall(
                Request.Builder().url(URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", "InkShelf/2.0 (editorial spotlight)")
                    .get().build(),
            ).execute().use { response ->
                if (response.code != 200) return@use null
                val body = response.body?.string() ?: return@use null
                body.takeIf { it.length in 20..50_000 }
            }
        }.getOrNull()
        val updated = remote?.let(::parse).orEmpty()
        if (updated.isNotEmpty()) {
            prefs.edit().putString("manifest", remote)
                .putLong("checked", now).apply()
            return@withContext active(updated)
        }
        // Do not turn an outage or an expired record into imaginary new releases.
        val saved = cached?.let(::parse).orEmpty()
        active(if (saved.isNotEmpty()) saved else starter)
    }

    private fun parse(input: String): List<Feature> = runCatching {
        val root = JSONObject(input)
        if (root.optInt("schema") != 1) return@runCatching emptyList()
        val arr: JSONArray = root.optJSONArray("features") ?: return@runCatching emptyList()
        buildList {
            for (i in 0 until minOf(arr.length(), 35)) {
                val item = arr.optJSONObject(i) ?: continue
                val feature = Feature(
                    id = item.optString("id"),
                    title = item.optString("title").trim(),
                    year = item.optInt("year"),
                    publisher = item.optString("publisher"),
                    label = item.optString("label").trim(),
                    caption = item.optString("caption").trim(),
                    releaseDate = item.optString("release_date"),
                    featuredFrom = item.optString("featured_from"),
                    expires = item.optString("expires"),
                    officialUrl = item.optString("official_url"),
                )
                val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")
                val officialHost = runCatching { URI(feature.officialUrl) }.getOrNull()
                val permittedHost = if (feature.publisher == "Marvel")
                    setOf("www.marvel.com", "marvel.com", "share.marvel.com")
                    else setOf("www.dc.com", "dc.com")
                if (feature.publisher !in setOf("Marvel", "DC") ||
                    feature.id.length !in 3..80 ||
                    feature.title.length !in 3..100 ||
                    feature.year !in 2000..2099 ||
                    feature.label.length !in 3..50 ||
                    feature.caption.length !in 3..140 ||
                    !datePattern.matches(feature.releaseDate) ||
                    !datePattern.matches(feature.featuredFrom) ||
                    !datePattern.matches(feature.expires) ||
                    officialHost?.scheme != "https" ||
                    officialHost.host !in permittedHost
                ) continue
                add(feature)
            }
        }
    }.getOrDefault(emptyList())
}
