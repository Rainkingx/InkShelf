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
 * One-tap event reader. Never shows a search page or opens a guessed issue.
 * Prefer the BatCave event's exact series URL; fall back to unambiguous
 * year-verified installed-source matches. The original J2K reader is unchanged.
 */
internal object InkShelf2GuideAutoReader {
    data class Resolved(val manga: Manga, val chapter: Chapter)

    private val safeSeriesPath = Regex("""^/\d{2,9}-[a-z0-9-]+\.html$""")

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

        suspend fun verify(comic: Manga): Resolved? {
            return try {
                withTimeoutOrNull(32_000L) {
                    val (actual, chapters) = engine.ensureIssues(comic)
                    val exact = InkShelf2GuideChapterFinder.find(issue, chapters)
                    if (exact != null && exact.chapter.id != null)
                        Resolved(actual, exact.chapter)
                    else null
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
        }

        // BatCave event pages provide the actual source comic URL, not just
        // a title. Probe the extension before creating a J2K database entry.
        val path = issue.batcaveSeriesPath
        if (preferred != null && path != null && safeSeriesPath.matches(path)) {
            val remote = Manga.create(path, issue.series, preferred.id)
            val available = try {
                withTimeoutOrNull(13_000L) {
                    preferred.getChapterList(remote).isNotEmpty()
                } ?: false
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            if (available) {
                verify(remote)?.let { return@withContext it }
            }
        }

        // No interactive "find comic" step. Search the preferred extension,
        // then other enabled sources. Only a single confirmed publication-year
        // edition is eligible for automatic reading.
        val query = InkShelf2GuideEdition.sourceSearchQuery(issue)
        val ordered = (listOfNotNull(preferred) + sources.filterNot {
            it.id == preferred?.id
        }).distinctBy { it.id }.take(5)
        for (source in ordered) {
            val found = try {
                engine.searchAcross(listOf(source), query).results
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }
            val exactEditions = found.filter {
                InkShelf2GuideEdition.classify(issue, it.manga.title) ==
                    InkShelf2GuideEdition.Match.MATCHING
            }.distinctBy { it.manga.url }
            if (exactEditions.size != 1) continue
            verify(exactEditions.single().manga)?.let { return@withContext it }
        }
        null
    }
}
