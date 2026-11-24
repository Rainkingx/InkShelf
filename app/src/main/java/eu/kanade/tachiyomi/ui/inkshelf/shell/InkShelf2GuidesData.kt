package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCredentials
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCovers
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
    val description: String = "",
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
    // Optional named issue in a longer source series (e.g. Pulse: House of M Special).
    val issueLabel: String? = null,
    // Explicitly unavailable in a saved source index; do not guess another edition.
    val sourceUnavailable: Boolean = false,
    val explicitSource: Long? = null,
    val explicitSeriesUrl: String? = null,
    val explicitChapterUrl: String? = null,
) {
    val isEssential: Boolean get() =
        role.equals("Core Issue", true) || role.equals("Prologue", true) ||
            role.equals("Epilogue", true) || role.isBlank()
    val displayName: String get() = series +
        (yearBegan?.let { year ->
            if (series.contains("($year)")) "" else " ($year)"
        } ?: "") +
        (issueLabel?.let { " · " + it }
            ?: if (number.isBlank()) "" else if (number == "Full") " · One-shot"
            else " #" + number)
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
            InkShelf2PersonalEvents.cover(context, guide.id)?.let { return@withContext it }
            if (guide.type == "CUSTOM") return@withContext null
            guide.batcaveSlug?.let { slug ->
                return@withContext verifiedEventCover(context, slug)
            }
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

    // Only exact main-series issue #1 matches are used for artwork. This
    // mapping has no influence on the saved guide order or reader identifiers.
    private val verifiedEventArt = mapOf(
        "civil-war-2006" to ("Civil War" to 2006),
        "absolute-power" to ("Absolute Power" to 2024),
        "dark-nights-metal" to ("Dark Nights: Metal" to 2017),
        "infinite-crisis" to ("Infinite Crisis" to 2005),
        "king-in-black" to ("King in Black" to 2020),
        "secret-invasion" to ("Secret Invasion" to 2008),
        "secret-wars-2015" to ("Secret Wars" to 2015),
        "war-of-the-realms" to ("War of the Realms" to 2019),
        "batman-zero-year" to ("Batman: Zero Year" to 2013),
        "blood-hunt" to ("Blood Hunt" to 2024),
        "civil-war-ii" to ("Civil War II" to 2016),
        "dc-k-o" to ("DC K.O." to 2025),
        "death-of-the-family" to ("Death of the Family" to 2012),
        "fear-itself" to ("Fear Itself" to 2011),
        "flashpoint" to ("Flashpoint" to 2011),
        "house-of-m" to ("House of M" to 2005),
        "night-of-the-owls" to ("Night of the Owls" to 2012),
        "original-sin" to ("Original Sin" to 2014),
        "spider-verse" to ("Spider-Verse" to 2014),
        "world-war-hulk" to ("World War Hulk" to 2007),
        "x-men-original-sin" to ("X-Men: Original Sin" to 2008),
    )

    fun publicationYear(guide: InkShelf2Guide): Int? =
        guide.batcaveSlug?.let { verifiedEventArt[it]?.second }

    /** The existing catalogue is a baseline, not falsely advertised as new.
     * Later additions receive a local badge for fourteen days.
     */
    fun newlyAddedGuideIds(context: Context, guides: List<InkShelf2Guide>): Set<Int> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "catalogue_seen_v1"
        val seen = prefs.getStringSet(key, null)?.toSet()
        val current = guides.map { it.id.toString() }.toSet()
        val now = System.currentTimeMillis()
        val edit = prefs.edit()
        val baseline = if (seen == null) guides.filter { guide ->
            InkShelf2AdditionalEventOrders.events.none { it.slug == guide.batcaveSlug }
        }.map { it.id.toString() }.toSet() else seen
        (current - baseline).forEach { edit.putLong("catalogue_added_" + it, now) }
        edit.putStringSet(key, seen.orEmpty() + current).apply()
        val duration = 14L * 24 * 60 * 60 * 1000
        return guides.filter { guide ->
            val added = prefs.getLong("catalogue_added_" + guide.id, 0L)
            added > 0L && now - added in 0L until duration
        }.map { it.id }.toSet()
    }

    fun timelineEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("catalogue_timeline_v1", false)

    fun setTimelineEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("catalogue_timeline_v1", enabled).apply()
    }

    private fun artTitle(value: String): String = value.lowercase(java.util.Locale.ROOT)
        .replace(Regex("""\s*\((?:19|20)\d{2}(?:[-–—][^)]*)?\)\s*$"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim().removePrefix("the ")

    private suspend fun verifiedEventCover(context: Context, slug: String): String? {
        val (title, year) = verifiedEventArt[slug] ?: return null
        return coverMutex.withLock {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = if (slug == "king-in-black") "verified_event_cover_v3_" + slug
                else "verified_event_cover_v2_" + slug
            val cached = prefs.getString(key, null)
                ?: prefs.getString("verified_event_cover_v2_" + slug, null)?.takeIf { it.isNotBlank() }
                ?: prefs.getString("verified_event_cover_v1_" + slug, null)?.takeIf { it.isNotBlank() }
            val age = System.currentTimeMillis() - prefs.getLong(key + "_time", 0L)
            val ttl = if (cached.isNullOrBlank()) 10L * 60 * 1000 else COVER_CACHE_MS
            if (cached != null && age in 0L until ttl) return@withLock imageUrl(cached)
            val secret = token(context) ?: return@withLock imageUrl(cached)
            try {
                // King in Black #1 went on sale in Dec 2020, but its
                // cover date is Feb 2021. Some catalogues use the latter.
                val years = if (slug == "king-in-black") listOf(year, 2021) else listOf(year)
                var found: String? = null
                for (lookupYear in years) {
                    delay(2_650)
                    val result = JSONObject(get(
                        "https://metron.cloud/api/issue/?series_name=" + Uri.encode(title) +
                            "&series_year_began=" + lookupYear + "&number=1",
                        secret,
                    ))
                    val rows = result.optJSONArray("results") ?: JSONArray()
                    val matches = (0 until rows.length()).mapNotNull { index ->
                        val issue = rows.optJSONObject(index) ?: return@mapNotNull null
                        val series = issue.optJSONObject("series") ?: return@mapNotNull null
                        val suppliedYear = series.optInt("year_began", 0)
                        if (artTitle(series.optString("name")) != artTitle(title) ||
                            (suppliedYear > 0 && suppliedYear != lookupYear) ||
                            issue.optString("number").trim() != "1") return@mapNotNull null
                        val id = issue.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                        val url = imageUrl(issue.optString("image")) ?: return@mapNotNull null
                        id to url
                    }.distinct()
                    val more = !result.isNull("next") && result.optString("next").isNotBlank()
                    if (!more && matches.size == 1) {
                        found = matches.single().second
                        break
                    }
                }
                val retained = found ?: imageUrl(cached)
                prefs.edit().putString(key, retained ?: "")
                    .putLong(key + "_time", System.currentTimeMillis()).apply()
                retained
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Network errors and rate limits are not authoritative misses.
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

    /** Match a known series edition only. Do not guess named specials or unknown years. */
    data class IssueArtworkKey(val title: String, val year: Int, val number: Int) {
        val seriesKey: String get() = artTitle(title) + "|" + year
        val cacheKey: String get() = "guide_issue_cover_v1_" + Uri.encode(artTitle(title)) + "_" + year + "_" + number
    }

    fun issueArtworkKey(issue: InkShelf2GuideIssue): IssueArtworkKey? {
        val year = issue.yearBegan ?: InkShelf2GuideAutoMatch.year(issue.series) ?: return null
        val number = issue.number.trim().removePrefix("#").toIntOrNull() ?: return null
        if (number !in 1..999 || !issue.issueLabel.isNullOrBlank()) return null
        return IssueArtworkKey(InkShelf2GuideAutoMatch.seriesName(issue.series), year, number)
    }

    /** Reuse exact covers already retrieved by Phase 2A without waiting for a network call. */
    fun cachedIssueCover(context: Context, key: IssueArtworkKey): String? =
        imageUrl(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key.cacheKey, null))

    fun rememberIssueCover(context: Context, key: IssueArtworkKey, url: String) {
        val valid = imageUrl(url) ?: return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key.cacheKey, valid)
            .putLong(key.cacheKey + "_time", System.currentTimeMillis()).apply()
    }

    /** One-shot fallback for a missing numbered cover, using the shared rate-limited service. */
    suspend fun issueCover(context: Context, issue: InkShelf2GuideIssue): String? =
        withContext(Dispatchers.IO) {
            val key = issueArtworkKey(issue) ?: return@withContext null
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cached = prefs.getString(key.cacheKey, null)
            val ttl = if (cached.isNullOrBlank()) COVER_MISS_CACHE_MS else COVER_CACHE_MS
            if (cached != null && System.currentTimeMillis() - prefs.getLong(key.cacheKey + "_time", 0L) in 0L until ttl)
                return@withContext imageUrl(cached)
            try {
                val found = imageUrl(InkShelfMetronCovers.exactIssueCover(context, key.title, key.year, key.number))
                val retained = found ?: imageUrl(cached)
                if (configured(context)) {
                    prefs.edit().putString(key.cacheKey, retained ?: "")
                        .putLong(key.cacheKey + "_time", System.currentTimeMillis()).apply()
                }
                retained
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                imageUrl(cached)
            }
        }

    fun clearReadingMarks(context: Context, guideId: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val opened = prefs.getStringSet("started_reader_v2", emptySet())?.toSet().orEmpty()
        prefs.edit().remove("read_" + guideId).remove("manual_unread_" + guideId)
            .remove("last_opened_" + guideId).remove("last_opened_at_" + guideId)
            .putStringSet("started_reader_v2", opened - guideId.toString()).apply()
    }

    fun displayOption(context: Context, guideId: Int, option: String): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("display_" + option + "_" + guideId, false)

    fun setDisplayOption(context: Context, guideId: Int, option: String, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("display_" + option + "_" + guideId, value).apply()
    }

    fun savedGuideIds(context: Context): Set<Int> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("saved_events_v1", emptySet())?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty()

    fun setSaved(context: Context, guideId: Int, saved: Boolean): Set<Int> {
        val old = savedGuideIds(context)
        val updated = if (saved) old + guideId else old - guideId
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet("saved_events_v1", updated.map(Int::toString).toSet()).apply()
        return updated
    }

    fun cachedEventCover(context: Context, guide: InkShelf2Guide): String? {
        InkShelf2PersonalEvents.cover(context, guide.id)?.let { return it }
        val slug = guide.batcaveSlug ?: return guide.imageUrl
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return listOf("verified_event_cover_v3_", "verified_event_cover_v2_", "verified_event_cover_v1_")
            .firstNotNullOfOrNull { prefix -> imageUrl(prefs.getString(prefix + slug, null)) }
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

    // Manual guide-only marks remain independent of the original chapter table.
    fun manualUnreadIds(context: Context, guideId: Int): Set<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("manual_unread_" + guideId, emptySet())?.toSet().orEmpty()

    fun setGuideRead(context: Context, guideId: Int, issueId: Int, read: Boolean) {
        setRead(context, guideId, issueId, read)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = manualUnreadIds(context, guideId)
        val updated = if (read) old - issueId.toString() else old + issueId.toString()
        prefs.edit().putStringSet("manual_unread_" + guideId, updated).apply()
    }

    fun rememberChapter(context: Context, guideId: Int, issueId: Int, mangaId: Long, chapterId: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("matched_" + guideId + "_" + issueId, "$mangaId:$chapterId").apply()
    }

    fun matchedChapter(context: Context, guideId: Int, issueId: Int): Pair<Long, Long>? {
        val parts = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("matched_" + guideId + "_" + issueId, null)?.split(':') ?: return null
        if (parts.size != 2) return null
        return (parts[0].toLongOrNull() ?: return null) to (parts[1].toLongOrNull() ?: return null)
    }

    fun essentialMode(context: Context, guideId: Int): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("essential_mode_" + guideId, true)

    fun setEssentialMode(context: Context, guideId: Int, essential: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("essential_mode_" + guideId, essential).apply()
    }

    fun startedGuides(context: Context): Set<Int> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val opened = prefs.getStringSet("started_reader_v2", emptySet())
            ?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty()
        // Previous builds marked a guide started merely by viewing it.
        // Preserve actual marked issues, not the unreliable viewed flag.
        val marked = InkShelf2PersonalEvents.catalogue(context).filter {
            readIds(context, it.id).isNotEmpty()
        }.map { it.id }
        return opened + marked
    }

    fun start(context: Context, guideId: Int, issueId: Int? = null) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val opened = prefs.getStringSet("started_reader_v2", emptySet())?.toSet().orEmpty()
        val edit = prefs.edit().putStringSet("started_reader_v2", opened + guideId.toString())
        if (issueId != null) {
            edit.putInt("last_opened_" + guideId, issueId)
                .putLong("last_opened_at_" + guideId, System.currentTimeMillis())
        }
        edit.apply()
    }

    fun lastOpenedAt(context: Context, guideId: Int): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("last_opened_at_" + guideId, 0L)

    fun lastOpenedIssue(context: Context, guideId: Int): Int? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("last_opened_" + guideId, 0).takeIf { it > 0 }

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
