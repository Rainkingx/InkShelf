package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    data class Series(
        val id: String,
        val title: String,
        val year: Int,
        val tagline: String,
        val issues: String? = null,
        val aliases: List<String> = emptyList(),
        val alternateYears: List<Int> = emptyList(),
        val artworkUrl: String? = null,
        val group: String? = null,
    ) {
        val editionTitle: String get() = title + " (" + year + ")"
        val searchTerms: List<String> get() = (listOf(title) + aliases).distinct()
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
    private const val CACHE = "inkshelf2_editorial_events_v3"
    const val MAX_ACTIVE = 6
    const val TTL_MS = 15L * 60L * 1000L
    private val refreshMutex = Mutex()
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
        val allowedTitles = series.searchTerms.map(InkShelf2GuideEdition::titleKey)
        val acceptsMarvelShortTitle = target == "the ultimates" && actual == "ultimates"
        if (actual !in allowedTitles && !acceptsMarvelShortTitle) return Edition.DIFFERENT
        val year = InkShelf2GuideEdition.year(sourceTitle)
        return when {
            year == series.year -> Edition.VERIFIED
            year == null || year in series.alternateYears -> Edition.CHECK_YEAR
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
        Event("doom-essential", "DOCTOR DOOM", "Marvel", "DOOMSDAY · ESSENTIAL READING",
            "Ahead of Avengers: Doomsday, explore the origins and major turning points of Victor von Doom. This curated comic reading path is not a confirmed film storyline.",
            "https://share.marvel.com/comics/discover/2493/one-world-under-doom",
            "2026-12-01", "2027-02-28",
            listOf("doctor doom", "one world under doom", "avengers: doomsday", "books of doom"),
            listOf(
                Series("origin", "Books of Doom", 2005, "Doom tells his own origin in Latveria.", "#1–6"),
                Series("debut", "Fantastic Four", 1961, "Doom's first appearance.", "#5"),
                Series("classic-secret-wars", "Marvel Super-Heroes Secret Wars", 1984,
                    "Doom seizes cosmic power.", "#1–12", listOf("Secret Wars", "Marvel Super Heroes Secret Wars")),
                Series("emperor", "Emperor Doom", 1987, "Doom conquers the world.", "Graphic novel"),
                Series("triumph", "Doctor Strange & Doctor Doom: Triumph and Torment", 1989,
                    "Doom's mystical past.", "Graphic novel",
                    listOf("Doctor Strange and Doctor Doom: Triumph and Torment", "Triumph and Torment")),
                Series("doomwar", "Doomwar", 2010, "Doom challenges Wakanda.", "#1–6"),
                Series("modern-secret-wars", "Secret Wars", 2015, "God Emperor Doom rules Battleworld.", "#1–9"),
                Series("infamous", "Infamous Iron Man", 2016, "Doom attempts to be a hero.", "#1–12"),
                Series("doctor-doom-solo", "Doctor Doom", 2019, "Doom's solo adventure.", "#1–10"),
                Series("one-world", "One World Under Doom", 2025,
                    "Doom conquers Earth as Sorcerer Supreme.", "#1–9"),
            ), artworkUrl = "https://cdn.marvel.com/content/2x/oneworlddoom2025005_cover.jpg"),
    )

    internal fun selectVisible(input: List<Event>, today: String): List<Event> =
        input.filter { it.featuredFrom <= today && it.expires >= today }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Event> { it.id == "doom-essential" })
            .take(MAX_ACTIVE)

    private fun active(input: List<Event>): List<Event> {
        return selectVisible(input, todayUtc())
    }

    fun starter(): List<Event> = active(fallback())

    data class LoadResult(val events: List<Event>, val fetched: Boolean)

    suspend fun load(context: Context): List<Event> = loadWithStatus(context).events

    suspend fun loadWithStatus(context: Context, force: Boolean = false): LoadResult =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val prefs = context.applicationContext.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
                val saved = prefs.getString("manifest", null)
                val now = System.currentTimeMillis()
                val cached = saved?.let(::parse).orEmpty()
                if (!force && cached.isNotEmpty() && now - prefs.getLong("checked", 0L) in 0 until TTL_MS) {
                    return@withLock LoadResult(active(cached), false)
                }
                // Explicit user refresh bypasses both the app TTL and CDN cache.
                val address = if (force) "$URL?refresh=$now" else URL
                val remote = runCatching {
                    val request = Request.Builder().url(address)
                        .header("User-Agent", "InkShelf/2.0 (editorial events)")
                        .header("Accept", "application/json")
                    if (force) request.header("Cache-Control", "no-cache")
                    client.newCall(request.get().build()).execute().use { response ->
                        if (response.code != 200) return@use null
                        response.body?.string()?.takeIf { it.length in 30..65_000 }
                    }
                }.getOrNull()
                val updated = remote?.let(::parse).orEmpty()
                if (updated.isNotEmpty()) {
                    prefs.edit().putString("manifest", remote)
                        .putLong("checked", System.currentTimeMillis()).apply()
                    return@withLock LoadResult(active(updated), true)
                }
                // Keep the last valid manifest on offline/server errors.
                LoadResult(active(cached.ifEmpty { fallback() }), false)
            }
        }

    private fun artworkHosts(publisher: String): Set<String> = when (publisher) {
        "Marvel" -> setOf("cdn.marvel.com")
        "DC" -> setOf("static.dc.com")
        "Marvel, DC & Indie" -> setOf("cdn.marvel.com", "static.dc.com")
        else -> emptySet()
    }

    private fun parse(raw: String): List<Event> = runCatching {
        val root = JSONObject(raw)
        if (root.optInt("schema") != 2) return@runCatching emptyList()
        val entries = root.optJSONArray("events") ?: return@runCatching emptyList()
        val date = Regex("""\d{4}-\d{2}-\d{2}""")
        buildList {
            for (index in 0 until minOf(entries.length(), 24)) {
                val item = entries.optJSONObject(index) ?: continue
                val items = item.optJSONArray("series") ?: continue
                val series = buildList {
                    for (n in 0 until minOf(items.length(), 14)) {
                        val element = items.optJSONObject(n) ?: continue
                        val alternatives = element.optJSONArray("aliases") ?: JSONArray()
                        val aliases = buildList {
                            for (a in 0 until minOf(alternatives.length(), 4)) {
                                val candidate = alternatives.optString(a)
                                if (candidate.length in 3..100) add(candidate)
                            }
                        }
                        val year = element.optInt("year")
                        val alternativeYears = element.optJSONArray("alternate_years") ?: JSONArray()
                        val years = buildList {
                            for (a in 0 until minOf(alternativeYears.length(), 2)) {
                                val candidate = alternativeYears.optInt(a, 0)
                                if (candidate in 1900..2099 && kotlin.math.abs(candidate - year) == 1) add(candidate)
                            }
                        }.distinct()
                        val cover = element.optString("artwork_url").takeIf { candidate ->
                            candidate.length in 10..2048 && runCatching {
                                val uri = URI(candidate)
                                val hosts = artworkHosts(item.optString("publisher"))
                                uri.scheme == "https" && uri.host in hosts && uri.userInfo == null
                            }.getOrDefault(false)
                        }
                        val s = Series(element.optString("id"), element.optString("title"),
                            year, element.optString("tagline"),
                            element.optString("issues").takeIf { it.isNotBlank() }, aliases, years, cover,
                            element.optString("group").takeIf { it in setOf("Marvel", "DC", "Indie") })
                        if (s.id.length in 2..65 && s.title.length in 3..100 &&
                            s.year in 1900..2099 && s.tagline.length in 5..200 &&
                            (s.issues == null || s.issues.length <= 80)) add(s)
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
                val allowed = when (e.publisher) {
                    "Marvel" -> setOf("www.marvel.com", "marvel.com", "share.marvel.com")
                    "DC" -> setOf("www.dc.com", "dc.com")
                    "Marvel, DC & Indie" -> setOf("www.marvel.com", "marvel.com", "share.marvel.com", "www.dc.com", "dc.com", "www.boom-studios.com", "imagecomics.com", "www.darkhorse.com", "miragelicensing.com")
                    else -> emptySet()
                }
                val allowedArtwork = artworkHosts(e.publisher)
                if (e.artworkUrl != null &&
                    (artUri?.scheme != "https" || artUri.host !in allowedArtwork)) continue
                if (e.id.length !in 3..80 || e.title.length !in 5..80 ||
                    e.publisher !in setOf("Marvel", "DC", "Marvel, DC & Indie") ||
                    e.label.length !in 3..80 || e.summary.length !in 20..1000 ||
                    !date.matches(e.featuredFrom) || !date.matches(e.expires) ||
                    url?.scheme != "https" || url.host !in allowed) continue
                add(e)
            }
        }
    }.getOrDefault(emptyList())
}
