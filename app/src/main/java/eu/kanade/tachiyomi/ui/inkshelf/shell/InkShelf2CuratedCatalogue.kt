package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Small, persistent catalogue of genuine source search results. No synthetic URLs. */
internal object InkShelf2CuratedCatalogue {
    const val GEMS = 0
    private const val PREFS = "inkshelf_curated_catalogue_v1"
    private const val HOUR = 3_600_000L
    private const val MAX_PER_THEME = 48
    private val mutex = Mutex()

    private val seeds = linkedMapOf(
        Calendar.MONDAY to listOf("X-Men", "Wolverine", "X-Force", "New Mutants", "Excalibur", "X-Factor", "Magneto", "Cable", "Deadpool", "Generation X", "Marauders", "X-23", "Gambit", "Storm", "Psylocke", "Nightcrawler", "Cyclops", "Magik", "Hellions", "Emma Frost", "Bishop", "Dazzler", "Mystique", "Sabretooth", "Legion of X"),
        Calendar.TUESDAY to listOf("Saga", "Monstress", "Hellboy", "Radiant Black", "Black Science", "The Walking Dead", "Invincible", "Spawn", "Descender", "East of West", "Paper Girls", "The Department of Truth", "Gideon Falls", "Lazarus", "The Sixth Gun", "Eight Billion Genies", "Black Hammer", "Once and Future", "Chew", "Harrow County", "The Many Deaths of Laila Starr", "Fear Agent", "Geiger", "Junkyard Joe", "Local Man", "Kaya"),
        Calendar.WEDNESDAY to listOf(""),
        Calendar.THURSDAY to listOf("Fantastic Four", "Detective Comics", "Uncanny X-Men", "The Sandman", "Daredevil", "Superman", "Wonder Woman", "Hellboy", "The Amazing Spider-Man", "Action Comics", "The Incredible Hulk", "Spawn", "Green Lantern", "Thor", "Captain America", "Swamp Thing"),
        Calendar.FRIDAY to listOf("Fantastic Four", "Silver Surfer", "Doctor Doom", "Future Foundation", "The Thing", "Human Torch", "Invisible Woman", "Fantastic Force", "FF", "Galactus", "Marvel Two-in-One", "Fantastic Four Life Story", "Fantastic Four 1234", "Fantastic Four Grand Design", "Fantastic Four Full Circle", "Fantastic Four Antithesis", "The Inhumans", "Fantastic Four Unstable Molecules", "Fantastic Four The End", "Fantastic Four First Family"),
        Calendar.SATURDAY to listOf("The Amazing Spider-Man", "Venom", "Miles Morales", "Spider-Gwen", "Carnage", "Silk", "Spider-Woman", "Spider-Man 2099", "Spider-Man Noir", "Ultimate Spider-Man", "The Spectacular Spider-Man", "Spider-Punk", "Spider-Verse", "Scarlet Spider", "Spider-Girl", "Spider-Boy", "Black Cat", "Ghost-Spider", "Spider-Ham", "Toxin", "Agent Venom", "Spider-Man Blue"),
        Calendar.SUNDAY to listOf("Superman", "Action Comics", "Supergirl", "Superboy", "Super Sons", "Superman Man of Steel", "Superman Birthright", "Superman Red Son", "All Star Superman", "Superman Secret Origin", "Superman For All Seasons", "Superman Up in the Sky", "Superwoman", "Power Girl", "Krypto", "Lois Lane", "Jimmy Olsen"),
        GEMS to listOf("East of West", "Black Science", "Descender", "Ascender", "Gideon Falls", "Seven to Eternity", "Lazarus", "Chew", "The Sixth Gun", "Fear Agent", "Once and Future", "Paper Girls", "The Department of Truth", "Die", "Radiant Black", "Eight Billion Genies", "Something is Killing the Children", "The Wicked and the Divine", "Little Bird", "Ultramega", "The Weatherman", "The Manhattan Projects", "Black Hammer", "The Autumnal", "Harrow County", "The Nice House on the Lake", "The Many Deaths of Laila Starr", "The Good Asian", "The New World", "The Spire", "Coda", "Kaya", "The Sacrificers", "Do a Powerbomb", "The Magic Order", "The Hunger and the Dusk", "The Last Ronin", "Geiger", "Junkyard Joe", "Rogue Sun", "Local Man", "The Deviant", "Wytches", "Kill or Be Killed", "Fatale", "The Fade Out", "Reckless", "Criminal"),
    )

    internal fun gemTitle(title: String): Boolean {
        val key = InkShelf2ComicIdentity.normalizedTitle(title)
        return seeds.getValue(GEMS).any { InkShelf2ComicIdentity.normalizedTitle(it) == key }
    }

    private fun store(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, theme: Int, enabled: Set<Long>): List<Manga> = runCatching {
        val prefs = store(context)
        val age = System.currentTimeMillis() - prefs.getLong("latest_at", 0L)
        val raw = if (theme == Calendar.WEDNESDAY && age !in 0L until 48 * HOUR) "[]" else prefs.getString("items_$theme", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until minOf(array.length(), MAX_PER_THEME)) {
                val row = array.optJSONObject(i) ?: continue
                val source = row.optLong("source", -1)
                val url = row.optString("url")
                val title = row.optString("title")
                val cover = row.optString("cover")
                if (source !in enabled || url.isBlank() || title.isBlank() || cover.isBlank()) continue
                val manga = Manga.create(url, title, source)
                manga.thumbnail_url = cover
                manga.genre = row.optString("genre")
                if (eligible(theme, manga, enabled)) add(manga)
            }
        }
    }.getOrDefault(emptyList())

    private fun eligible(theme: Int, comic: Manga, enabled: Set<Long>): Boolean {
        if (comic.source !in enabled || comic.url.isBlank() || comic.thumbnail_url.isNullOrBlank()) return false
        return if (theme == GEMS) gemTitle(comic.title)
        else InkShelf2DailyRotation.matchesTheme(theme, comic, fromLatest = theme == Calendar.WEDNESDAY)
    }

    /** Up to ten bounded searches per pass, prioritising today and discovery.
     * Cache is committed per search so cancellation never loses the results.
     * An ordinary refresh is at most daily (12 hours when today's pool is sparse).
     */
    suspend fun refresh(context: Context, sources: List<CatalogueSource>): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ordered = sources.distinctBy { it.id }.sortedBy { InkShelf2SourceBalance.sourceOrder(it.name) }
            if (ordered.isEmpty()) return@withLock false
            val enabled = ordered.map { it.id }.toSet()
            val prefs = store(context)
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val dayStamp = InkShelf2DailyRotation.localDayStamp()
            val signature = "daily-themes-v3:" + enabled.sorted().joinToString(",")
            val now = System.currentTimeMillis()
            val todayCount = load(context, today, enabled).filter { eligible(today, it, enabled) }.distinctBy(InkShelf2DailyRotation::seriesKey).size
            val sparse = seeds.keys.any { it != GEMS && load(context, it, enabled).size < 3 }
            val cooldown = if (sparse || todayCount < 3) 6 * HOUR else 24 * HOUR
            if (prefs.getString("sources", "") == signature &&
                prefs.getInt("day", -1) == dayStamp &&
                now - prefs.getLong("attempt", 0L) in 0 until cooldown) return@withLock false
            // Mark attempts before starting: opening/closing the app cannot hammer sources.
            prefs.edit().putString("sources", signature).putInt("day", dayStamp).putLong("attempt", now).apply()
            val engine = InkShelf2SourcesEngine()
            // All seven themes get a bounded refresh slot; today's sparse
            // catalogue receives extra searches. Two slots remain for Hidden Gems.
            val buckets = seeds.keys.filter { it != GEMS && it != today }
                .sortedBy { load(context, it, enabled).size }
            val plan = buildList {
                add(today)
                if (todayCount < 6) add(today)
                addAll(buckets)
                add(GEMS)
                add(GEMS)
            }.take(10)
            var changed = false
            for (theme in plan) {
                val terms = seeds.getValue(theme)
                // Start with less familiar indie picks, not the old fixed top three.
                val initialCursor = if (theme == GEMS) 20 else 0
                val cursorKey = if (theme == GEMS) "cursor_$theme" else "daily_v3_cursor_$theme"
                val cursor = prefs.getInt(cursorKey, initialCursor).coerceAtLeast(0)
                val query = terms[cursor % terms.size]
                val candidates = if (theme == Calendar.WEDNESDAY) ordered.filter { it.supportsLatest } else ordered
                if (candidates.isEmpty()) continue
                val source = candidates[cursor % candidates.size]
                val old = load(context, theme, enabled).filter { eligible(theme, it, enabled) }
                val found = try {
                    withTimeoutOrNull(5_000L) { engine.page(source, 1, query, latest = theme == Calendar.WEDNESDAY).comics }.orEmpty()
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { emptyList() }
                val matches = found.filter { eligible(theme, it, enabled) }
                    .distinctBy(InkShelf2DailyRotation::seriesKey).take(6)
                // New results replace stale source/cover records for that series.
                // Latest results are time-sensitive; never accumulate weeks of old feed entries.
                val merged = if (theme == Calendar.WEDNESDAY && matches.isNotEmpty()) matches
                    else (matches + old).distinctBy(InkShelf2DailyRotation::seriesKey).take(MAX_PER_THEME)
                val array = JSONArray()
                merged.forEach { manga ->
                    array.put(JSONObject().put("source", manga.source).put("url", manga.url)
                        .put("title", manga.title).put("cover", manga.thumbnail_url.orEmpty())
                        .put("genre", listOfNotNull(manga.genre, manga.originalGenre).joinToString(", ")))
                }
                val edit = prefs.edit().putInt(cursorKey, cursor + 1).putString("items_$theme", array.toString())
                if (theme == Calendar.WEDNESDAY && matches.isNotEmpty()) edit.putLong("latest_at", System.currentTimeMillis())
                edit.apply()
                if (merged.map { it.source to it.url } != old.map { it.source to it.url }) changed = true
            }
            changed
        }
    }
}
