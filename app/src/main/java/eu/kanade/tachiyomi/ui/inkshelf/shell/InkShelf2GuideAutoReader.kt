package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens event issues through the installed J2K extension. All UI stays on the
 * guide details page: only a verified source chapter opens the original reader.
 *
 * Real BatCave series paths take priority. If unavailable, search the native
 * extension using edition-aware aliases, never a generic cover/title guess.
 * Successful series links are cached so other issues in the same run are fast.
 */
internal object InkShelf2GuideAutoReader {
    data class Resolved(val manga: Manga, val chapter: Chapter)

    private const val PREFS = "inkshelf2_guide_series_links_v2"
    private val safeSeriesPath = Regex("""^/\d{2,9}-[a-z0-9-]+\.html$""")

    private fun key(issue: InkShelf2GuideIssue): String =
        InkShelf2GuideAutoMatch.key(issue.series) + ":" +
            (issue.yearBegan ?: InkShelf2GuideAutoMatch.year(issue.series) ?: 0)

    private fun canonicalPath(url: String): String {
        val parsed = runCatching { java.net.URI(url) }.getOrNull()
        return parsed?.path?.takeIf { it.startsWith("/") }
            ?: "/" + url.substringBefore('?').trimStart('/')
    }

    suspend fun resolve(
        context: Context,
        engine: InkShelf2SourcesEngine,
        sources: List<CatalogueSource>,
        requested: InkShelf2GuideIssue,
    ): Resolved? = withContext(Dispatchers.IO) {
        if (sources.isEmpty() || (requested.number.toIntOrNull() == null &&
                !requested.number.equals("Full", ignoreCase = true))) {
            return@withContext null
        }

        val issue = if (requested.batcaveIssuePath != null ||
                requested.batcaveSeriesPath != null) requested
            else InkShelf2GuidesData.resolveIssueEdition(context, requested)
        val preferred = sources.firstOrNull {
            it.name.contains("batcave", ignoreCase = true)
        }
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cacheKey = key(issue)

        suspend fun verify(comic: Manga): Resolved? = try {
            withTimeoutOrNull(35_000L) {
                val (actual, chapters) = engine.ensureIssues(comic)
                val chapter = InkShelf2GuideChapterFinder.find(issue, chapters)?.chapter
                if (chapter?.id != null) Resolved(actual, chapter) else null
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }

        fun remember(resolved: Resolved): Resolved {
            val manga = resolved.manga
            store.edit().putString(
                cacheKey,
                manga.source.toString() + "\n" + manga.url + "\n" + manga.title,
            ).apply()
            return resolved
        }

        // Preserve a resolved source/series across all event chapters while
        // rechecking that the exact requested issue is actually present.
        val saved = store.getString(cacheKey, null)?.split('\n', limit = 3)
        if (saved != null && saved.size == 3) {
            val sourceId = saved[0].toLongOrNull()
            val source = sources.firstOrNull { it.id == sourceId }
            if (source != null && saved[1].isNotBlank()) {
                val manga = Manga.create(saved[1], saved[2], source.id)
                verify(manga)?.let { return@withContext it }
            }
            store.edit().remove(cacheKey).apply()
        }

        // For the bundled House of M order, these paths come from actual
        // verified BatCave comic pages. Do not construct paths from titles.
        val expectedPath = issue.batcaveSeriesPath
        if (preferred != null && expectedPath != null && safeSeriesPath.matches(expectedPath)) {
            val remote = Manga.create(expectedPath, issue.series, preferred.id)
            val listed = try {
                withTimeoutOrNull(13_000L) {
                    preferred.getChapterList(remote).isNotEmpty()
                } ?: false
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            if (listed) {
                verify(remote)?.let { return@withContext remember(it) }
            }
        }

        // The catalogue's naming differs from guide terminology:
        // "Excalibur Vol. 3" is "Excalibur (2004)" on BatCave.
        // Try an exact-year query first and a plain series search second.
        // A different known publication year is never auto-selected.
        val ordered = (listOfNotNull(preferred) + sources.filterNot {
            it.id == preferred?.id
        }).distinctBy { it.id }.take(5)
        val queries = InkShelf2GuideAutoMatch.queries(issue)
        for (source in ordered) {
            for (query in queries) {
                val found = try {
                    engine.searchAcross(listOf(source), query).results
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    emptyList()
                }
                if (found.isEmpty()) continue

                if (expectedPath != null && safeSeriesPath.matches(expectedPath)) {
                    val linked = found.filter {
                        canonicalPath(it.manga.url) == expectedPath
                    }.distinctBy { it.manga.url }
                    if (linked.size == 1) {
                        verify(linked.single().manga)?.let {
                            return@withContext remember(it)
                        }
                    }
                }

                val ranked = found.mapNotNull { candidate ->
                    val rank = InkShelf2GuideAutoMatch.rank(issue, candidate.manga.title)
                    if (rank > 0) candidate to rank else null
                }
                val highest = ranked.maxOfOrNull { it.second } ?: continue
                val eligible = ranked.filter { it.second == highest }
                    .map { it.first.manga }
                    .distinctBy { it.url }
                // Ambiguous title/year results must not silently open the
                // wrong run, even if both runs contain the same issue number.
                if (eligible.size != 1) continue
                verify(eligible.single())?.let { return@withContext remember(it) }
            }
        }
        null
    }
}
