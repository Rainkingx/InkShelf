package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.util.Log
import android.widget.Toast
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

    /**
     * Probe the existing source API, not a new page scraper. Persist only a
     * successful direct chapter in an isolated non-favourite guide record so
     * the canonical series, library entries and reading progress stay intact.
     */
    private data class PilotResult(val resolved: Resolved?, val detail: String)

    private suspend fun resolveDirectPilot(
        source: CatalogueSource,
        issue: InkShelf2GuideIssue,
    ): PilotResult {
        val path = issue.batcaveIssuePath ?: return PilotResult(null, "No issue URL")
        val chapter = Chapter.create().apply {
            url = path
            name = "${issue.series} #${issue.number}"
            chapter_number = issue.number.toFloatOrNull() ?: -1f
            date_upload = 0L
            date_fetch = System.currentTimeMillis()
            source_order = 0
        }
        return try {
            val pages = withTimeoutOrNull(15_000L) {
                source.getPageList(chapter)
            } ?: return PilotResult(null, "Timed out after 15 seconds")
            if (pages.isEmpty()) return PilotResult(null, "Extension returned zero pages")

            val database: DatabaseHelper = Injekt.get()
            val manga = database.getManga(path, source.id).executeAsBlocking()
                ?: Manga.create(path, "Excalibur #13 (House of M guide)", source.id).apply {
                    favorite = false
                    initialized = true
                    genre = "Comic"
                    id = database.insertManga(this).executeAsBlocking().insertedId()
                }
            val mangaId = manga.id ?: return PilotResult(null, "Missing reader series ID")
            val saved = database.getChapter(path, mangaId).executeAsBlocking()
                ?: chapter.apply {
                    manga_id = mangaId
                    id = database.insertChapter(this).executeAsBlocking().insertedId()
                }
            if (saved.id != null) PilotResult(Resolved(manga, saved), "${pages.size} pages")
            else PilotResult(null, "Missing reader chapter ID")
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w("InkShelfGuide", "Direct event probe failed", error)
            val status = Regex("""\b(401|403|404|408|429|500|502|503|504)\b""")
                .find(error.message.orEmpty())?.value
            val detail = if (status != null) "HTTP $status"
                else error.javaClass.simpleName + ": " +
                    error.message.orEmpty().replace(Regex("""\s+"""), " ").take(90)
            PilotResult(null, detail)
        }
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
        // Pilot only: the first event-reader link was confirmed on-device.
        // Other bundled positions are not verified URL-to-issue mappings.
        // Keep all other guide issues on the existing matching path.
        val pilot = issue.position == 1 &&
            issue.batcaveIssuePath == "/event/house-of-m/1" &&
            issue.series.equals("Excalibur", ignoreCase = true) && issue.number == "13"
        var pilotFailure: String? = null
        if (preferred != null && pilot) {
            val direct = resolveDirectPilot(preferred, issue)
            Log.i("InkShelfGuide", "Event probe: ${direct.detail}")
            if (direct.resolved != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context.applicationContext,
                        "Guide: direct URL opened (${direct.detail})", Toast.LENGTH_LONG).show()
                }
                return@withContext direct.resolved
            }
            pilotFailure = direct.detail
        } else if (pilot) {
            pilotFailure = "BatCave source not enabled"
        }
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cacheKey = key(issue)

        suspend fun verify(comic: Manga): Resolved? = try {
            withTimeoutOrNull(35_000L) {
                val (actual, chapters) = engine.ensureIssues(comic)
                val chapter = InkShelf2GuideChapterFinder.find(issue, chapters)?.chapter
                if (chapter?.id != null) {
                    if (pilot && pilotFailure != null) {
                        val matchedSource = sources.firstOrNull { it.id == actual.source }?.name
                            ?: actual.source.toString()
                        Log.i("InkShelfGuide",
                            "Fallback source=$matchedSource series=${actual.url} chapter=${chapter.url}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context.applicationContext,
                                "Direct: $pilotFailure\nFallback: $matchedSource",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                    Resolved(actual, chapter)
                } else null
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
        if (pilot && pilotFailure != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context.applicationContext,
                    "Direct: $pilotFailure; no fallback match", Toast.LENGTH_LONG).show()
            }
        }
        null
    }
}
