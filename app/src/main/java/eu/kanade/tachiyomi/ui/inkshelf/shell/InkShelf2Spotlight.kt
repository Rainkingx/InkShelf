package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.data.database.models.Manga
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
 * Three curated comic *events*, not three random popular-extension results.
 * Data can be edited remotely in editorial/spotlight.json; searching and
 * reading always use the existing installed J2K sources and local library.
 */
internal object InkShelf2Spotlight {
    data class Series(val id: String, val title: String, val year: Int, val tagline: String) {
        val editionTitle: String get() = title + " (" + year + ")"
    }
    data class Event(
        val id: String,
        val title: String,
        val publisher: String,
        val label: String,
        val summary: String,
        val officialUrl: String,
        val featuredFrom: String,
        val expires: String,
        val pulseKeywords: List<String>,
        val series: List<Series>,
        val artworkUrl: String? = null,
    ) {
        val leadSeries: Series get() = series.first()
        val heroArtwork: String? get() = artworkUrl
    }
    enum class Edition { VERIFIED, CHECK_YEAR, DIFFERENT }
    data class Match(
        val manga: Manga,
        val sourceName: String,
        val edition: Edition,
    )

    private const val URL =
        "https://raw.githubusercontent.com/Rainkingx/InkShelf/inkshelf-2.0/editorial/spotlight.json"
    private const val CACHE = "inkshelf2_editorial_events_v2"
    private const val TTL_MS = 6L * 60L * 60L * 1000L
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(11, TimeUnit.SECONDS)
        .followRedirects(false).build()

    fun todayUtc(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    fun preferredSource(sources: List<CatalogueSource>): CatalogueSource? =
        sources.firstOrNull { it.name.contains("batcave", ignoreCase = true) }
            ?: sources.firstOrNull()

    fun metronTitle(series: Series): String =
        if (series.title.equals("The Ultimates", ignoreCase = true)) "Ultimates (" + series.year + ")"
        else series.editionTitle

    fun edition(series: Series, sourceTitle: String): Edition {
        val target = InkShelf2GuideEdition.titleKey(series.title)
        val actual = InkShelf2GuideEdition.titleKey(sourceTitle)
        val acceptsMarvelShortTitle = target == "the ultimates" && actual == "ultimates"
        if (target != actual && !acceptsMarvelShortTitle) return Edition.DIFFERENT
        return when (InkShelf2GuideEdition.year(sourceTitle)) {
            series.year -> Edition.VERIFIED
            null -> Edition.CHECK_YEAR
            else -> Edition.DIFFERENT
        }
    }

    fun bestMatch(series: Series, results: List<InkShelf2SourcesEngine.SourceResult>): Match? {
        val candidates = results.mapNotNull { result ->
            val safety = edition(series, result.manga.title)
            if (safety == Edition.DIFFERENT) null
            else Match(result.manga, result.sourceName, safety)
        }
        // Never silently pick a different publication year. If the source
        // omits year labels, label that result as needing confirmation.
        return candidates.firstOrNull { it.edition == Edition.VERIFIED }
            ?: candidates.firstOrNull()
    }

    fun related(event: Event, articles: List<InkShelf2PulseArticle>): List<InkShelf2PulseArticle> {
        val keywords = event.pulseKeywords.map { it.lowercase(Locale.ROOT) }
        return articles.filter { article ->
            val headline = article.title.lowercase(Locale.ROOT)
                .replace('–', '-').replace('—', '-')
            keywords.any { headline.contains(it) }
        }.distinctBy { it.url }.sortedByDescending { it.publishedAt }.take(8)
    }

    private fun fallback(): List<Event> = listOf(
        Event("marvel-midnight", "MARVEL MIDNIGHT", "Marvel", "THE MIDNIGHT UNIVERSE",
            "Marvel's darker, horror-inspired universe began with three interconnected series on 7 October 2026.",
            "https://www.marvel.com/midnight", "2026-11-01", "2027-12-31",
            listOf("midnight universe", "midnight x-men", "midnight spider-man", "midnight fantastic four"),
            listOf(
                Series("xmen", "Midnight X-Men", 2026, "A dark new world for the X-Men."),
                Series("spiderman", "Midnight Spider-Man", 2026, "A terrifying Spider-Man reimagining."),
                Series("fantastic-four", "Midnight Fantastic Four", 2026, "The First Family, reimagined."),
            ), artworkUrl = "https://cdn.marvel.com/content/2x/20260504-midnight-alllogos_4x5-c.jpg"),
        Event("dc-absolute", "DC ABSOLUTE UNIVERSE", "DC", "DC ALL IN",
            "DC heroes are reinvented in a new world, with their own connected Absolute Universe series.",
            "https://www.dc.com/allin", "2026-11-01", "2027-12-31",
            listOf("absolute universe", "absolute batman", "absolute superman", "absolute wonder woman"),
            listOf(
                Series("batman", "Absolute Batman", 2024, "An all-new Dark Knight."),
                Series("superman", "Absolute Superman", 2024, "The Man of Steel, reinvented."),
                Series("wonder-woman", "Absolute Wonder Woman", 2024, "Diana's new world."),
                Series("flash", "Absolute Flash", 2025, "A new Scarlet Speedster."),
                Series("green-lantern", "Absolute Green Lantern", 2025, "A strange new light."),
                Series("martian-manhunter", "Absolute Martian Manhunter", 2025, "A visionary alien mystery."),
                Series("green-arrow", "Absolute Green Arrow", 2026, "An urban horror mystery."),
                Series("catwoman", "Absolute Catwoman", 2026, "Selina joins the Absolute line."),
            ), artworkUrl = "https://static.dc.com/2026-07/ABS_2026SDCC-FOIL.jpg"),
        Event("marvel-ultimate", "ULTIMATE UNIVERSE", "Marvel", "MARVEL'S NEW ULTIMATE ERA",
            "Explore Marvel's modern Ultimate series and the final confrontation with the Maker.",
            "https://share.marvel.com/comics/discover/2529/ultimate-endgame", "2026-11-01", "2027-12-31",
            listOf("ultimate universe", "ultimate endgame", "ultimate spider-man", "ultimate x-men"),
            listOf(
                Series("spiderman", "Ultimate Spider-Man", 2024, "Peter Parker's new beginning."),
                Series("black-panther", "Ultimate Black Panther", 2024, "Wakanda, reinvented."),
                Series("xmen", "Ultimate X-Men", 2024, "A different mutant world."),
                Series("ultimates", "The Ultimates", 2024, "A new hero team assembles."),
                Series("wolverine", "Ultimate Wolverine", 2025, "A new Wolverine story."),
                Series("endgame", "Ultimate Endgame", 2025, "The Maker returns."),
                Series("finale", "Ultimate Universe: Finale", 2026, "The saga concludes."),
            ), artworkUrl = "https://cdn.marvel.com/content/2x/ultimateendgame_teaser2.jpg"),
    )

    private fun active(input: List<Event>): List<Event> {
        val today = todayUtc()
        return input.filter { it.featuredFrom <= today && it.expires >= today }
            .distinctBy { it.id }.take(3)
    }

    fun starter(): List<Event> = active(fallback())

    suspend fun load(context: Context): List<Event> = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val saved = prefs.getString("manifest", null)
        val now = System.currentTimeMillis()
        if (saved != null && now - prefs.getLong("checked", 0L) in 0 until TTL_MS) {
            val entries = parse(saved)
            if (entries.isNotEmpty()) return@withContext active(entries)
        }
        val remote = runCatching {
            client.newCall(Request.Builder().url(URL).get()
                .header("User-Agent", "InkShelf/2.0 (editorial events)")
                .header("Accept", "application/json").build()).execute().use { response ->
                if (response.code != 200) return@use null
                response.body?.string()?.takeIf { it.length in 30..65_000 }
            }
        }.getOrNull()
        val updated = remote?.let(::parse).orEmpty()
        if (updated.isNotEmpty()) {
            prefs.edit().putString("manifest", remote)
                .putLong("checked", now).apply()
            return@withContext active(updated)
        }
        active(saved?.let(::parse).orEmpty().ifEmpty { fallback() })
    }

    private fun parse(raw: String): List<Event> = runCatching {
        val root = JSONObject(raw)
        if (root.optInt("schema") != 2) return@runCatching emptyList()
        val entries = root.optJSONArray("events") ?: return@runCatching emptyList()
        val date = Regex("""\d{4}-\d{2}-\d{2}""")
        buildList {
            for (index in 0 until minOf(entries.length(), 3)) {
                val item = entries.optJSONObject(index) ?: continue
                val items = item.optJSONArray("series") ?: continue
                val series = buildList {
                    for (n in 0 until minOf(items.length(), 12)) {
                        val element = items.optJSONObject(n) ?: continue
                        val s = Series(element.optString("id"), element.optString("title"),
                            element.optInt("year"), element.optString("tagline"))
                        if (s.id.length in 2..65 && s.title.length in 3..100 &&
                            s.year in 2000..2099 && s.tagline.length in 5..200) add(s)
                    }
                }.distinctBy { it.id }
                if (series.isEmpty()) continue
                val terms = item.optJSONArray("pulse_keywords") ?: JSONArray()
                val keywords = buildList {
                    for (n in 0 until minOf(terms.length(), 15)) {
                        terms.optString(n).takeIf { it.length in 5..85 }?.let(::add)
                    }
                }
                val e = Event(item.optString("id"), item.optString("title"),
                    item.optString("publisher"), item.optString("label"),
                    item.optString("summary"), item.optString("official_url"),
                    item.optString("featured_from"), item.optString("expires"),
                    keywords, series, item.optString("artwork_url").takeIf { it.isNotBlank() })
                val url = runCatching { URI(e.officialUrl) }.getOrNull()
                val artUri = e.artworkUrl?.let { runCatching { URI(it) }.getOrNull() }
                val allowed = if (e.publisher == "Marvel")
                    setOf("www.marvel.com", "marvel.com", "share.marvel.com")
                    else setOf("www.dc.com", "dc.com")
                val allowedArtwork = if (e.publisher == "Marvel")
                    setOf("cdn.marvel.com")
                    else setOf("static.dc.com")
                if (e.artworkUrl != null &&
                    (artUri?.scheme != "https" || artUri.host !in allowedArtwork)) continue
                if (e.id.length !in 3..80 || e.title.length !in 5..80 ||
                    e.publisher !in setOf("Marvel", "DC") ||
                    e.label.length !in 3..80 || e.summary.length !in 20..1000 ||
                    !date.matches(e.featuredFrom) || !date.matches(e.expires) ||
                    url?.scheme != "https" || url.host !in allowed) continue
                add(e)
            }
        }
    }.getOrDefault(emptyList())
}
