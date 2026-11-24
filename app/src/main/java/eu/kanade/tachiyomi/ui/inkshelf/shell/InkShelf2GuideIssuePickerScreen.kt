package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Dedicated Reading Guide issue resolver, NOT a general Discover search.
 *
 * Metron provides the intended series + publication year + issue number.
 * Extensions provide the readable series and their actual chapter lists.
 * Only a verified issue may create a remembered source link or open directly.
 */
@Composable
internal fun InkShelf2GuideIssuePickerScreen(
    requestedIssue: InkShelf2GuideIssue,
    sources: List<CatalogueSource>,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenComic: (Manga) -> Unit,
    onOpenExactIssue: (Manga, Chapter) -> Unit,
) {
    val context = LocalContext.current
    val engine = remember { InkShelf2SourcesEngine() }
    val libraryEngine = remember { InkShelf2Engine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var target by remember(requestedIssue.id) { mutableStateOf(requestedIssue) }
    var query by rememberSaveable(requestedIssue.id) {
        mutableStateOf(InkShelf2GuideEdition.sourceSearchQuery(requestedIssue))
    }
    var searchEpoch by remember(requestedIssue.id) { mutableIntStateOf(0) }
    var results by remember(requestedIssue.id) {
        mutableStateOf<List<InkShelf2SourcesEngine.SourceResult>>(emptyList())
    }
    var loading by remember(requestedIssue.id) { mutableStateOf(false) }
    var checking by remember(requestedIssue.id) { mutableStateOf<String?>(null) }
    var message by remember(requestedIssue.id) { mutableStateOf<String?>(null) }
    var failedSources by remember(requestedIssue.id) { mutableIntStateOf(0) }
    var showOtherRuns by remember(requestedIssue.id) { mutableStateOf(false) }
    var navigated by remember(requestedIssue.id) { mutableStateOf(false) }
    val enabledIds = sources.map { it.id }
    val sourceIdentity = enabledIds.joinToString(",")

    BackHandler { onBack() }

    LaunchedEffect(requestedIssue.id, sourceIdentity, searchEpoch) {
        loading = true
        results = emptyList()
        message = null
        showOtherRuns = false
        try {
            target = if (requestedIssue.batcaveSeriesPath != null) requestedIssue
                else InkShelf2GuidesData.resolveIssueEdition(context, requestedIssue)
            // BatCave events expose the real series page path alongside the
            // ordered issue. Probe that exact path using the installed BatCave
            // extension, never through a browser or a synthetic library row.
            val batcave = sources.firstOrNull {
                it.name.contains("batcave", ignoreCase = true)
            }
            val linkedPath = target.batcaveSeriesPath
            if (searchEpoch == 0 && batcave != null && linkedPath != null &&
                Regex("""^/\d{2,9}-[a-z0-9-]+\.html$""").matches(linkedPath) &&
                target.number.toIntOrNull() != null
            ) {
                val probe = Manga.create(linkedPath, target.series, batcave.id)
                try {
                    val remoteChapters = withTimeout(14_000L) {
                        batcave.getChapterList(probe)
                    }
                    if (InkShelf2GuideChapterFinder.find(target, remoteChapters) != null) {
                        val (resolved, chapters) = engine.ensureIssues(probe)
                        val exact = InkShelf2GuideChapterFinder.find(target, chapters)
                        if (exact != null) {
                            navigated = true
                            onOpenExactIssue(resolved, exact.chapter)
                            return@LaunchedEffect
                        }
                    }
                    message = "The BatCave link could not verify this exact issue. Searching source titles instead."
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    message = "The BatCave direct link is unavailable. Trying normal source matching."
                }
            }
            // Resume a previously confirmed Metron-series-to-source link.
            // Recheck this exact issue: a source might not contain every tie-in.
            if (searchEpoch == 0) {
                val linkedId = InkShelf2GuideSourceLinks.get(context, target)
                if (linkedId != null) {
                    val linked = libraryEngine.mangaById(linkedId)
                    if (linked != null && linked.source in enabledIds &&
                        InkShelf2GuideEdition.classify(target, linked.title) ==
                            InkShelf2GuideEdition.Match.MATCHING
                    ) {
                        try {
                            val (resolved, chapters) = engine.ensureIssues(linked)
                            if (InkShelf2GuideChapterFinder.find(target, chapters) != null) {
                                navigated = true
                                val exact = InkShelf2GuideChapterFinder.find(target, chapters)
                                if (exact != null) onOpenExactIssue(resolved, exact.chapter)
                                return@LaunchedEffect
                            }
                            message = "Your linked source does not list this issue. Looking for another source."
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            message = "Your linked source is unavailable. Looking for another source."
                        }
                    }
                }
            }
            val lookup = query.trim()
            if (lookup.length >= 2 && enabledIds.isNotEmpty()) {
                val orderedSources = sources.sortedBy {
                    if (it.name.contains("batcave", ignoreCase = true)) 0 else 1
                }
                val found = engine.searchAcross(orderedSources, lookup)
                failedSources = found.failedSources
                results = found.results
                val verifiedTitles = results.filter {
                    InkShelf2GuideEdition.classify(target, it.manga.title) ==
                        InkShelf2GuideEdition.Match.MATCHING
                }
                // A single unambiguous edition may open after its REAL
                // issue list confirms the requested number is present.
                if (searchEpoch == 0 && verifiedTitles.size == 1) {
                    try {
                        val (resolved, chapters) = engine.ensureIssues(verifiedTitles.single().manga)
                        if (InkShelf2GuideChapterFinder.find(target, chapters) != null) {
                            InkShelf2GuideSourceLinks.put(context, target, resolved)
                            navigated = true
                            val exact = InkShelf2GuideChapterFinder.find(target, chapters)
                            if (exact != null) onOpenExactIssue(resolved, exact.chapter)
                            return@LaunchedEffect
                        }
                        message = "The matching series was found, but issue #" +
                            target.number + " could not be verified in that source."
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        message = "Could not verify that source. You can inspect other results below."
                    }
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            message = "Could not search installed comic sources. Check the extension connections."
        } finally {
            loading = false
        }
    }

    val matching = results.filter {
        InkShelf2GuideEdition.classify(target, it.manga.title) ==
            InkShelf2GuideEdition.Match.MATCHING
    }
    val uncertain = results.filter {
        InkShelf2GuideEdition.classify(target, it.manga.title) ==
            InkShelf2GuideEdition.Match.UNVERIFIED
    }
    val other = results.filter {
        InkShelf2GuideEdition.classify(target, it.manga.title) in setOf(
            InkShelf2GuideEdition.Match.OTHER_RUN,
            InkShelf2GuideEdition.Match.OTHER_TITLE,
        )
    }
    val visible = matching + uncertain + (if (showOtherRuns) other else emptyList())

    fun inspect(candidate: InkShelf2SourcesEngine.SourceResult) {
        if (checking != null || navigated) return
        val classification = InkShelf2GuideEdition.classify(target, candidate.manga.title)
        if (classification != InkShelf2GuideEdition.Match.MATCHING) {
            Toast.makeText(
                context,
                "Edition not verified — check the year and issue list before reading.",
                Toast.LENGTH_LONG,
            ).show()
        } else if (message?.contains("could not be verified", ignoreCase = true) == true ||
            message?.contains("wasn't confirmed", ignoreCase = true) == true
        ) {
            Toast.makeText(
                context,
                "Issue #" + target.number + " wasn't confirmed. Opening the source's full issue list.",
                Toast.LENGTH_LONG,
            ).show()
        }
        // A series title can be correct while a provider omits one issue or
        // uses an unfamiliar chapter-number scheme. Never let a failed
        // automatic issue verification make this otherwise valid card inert.
        // Details loads the original source's chapters; only separately
        // confirmed series+issue matches are persisted as verified links.
        navigated = true
        onOpenComic(candidate.manga)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to reading guide",
                        tint = InkShelf2Colors.Text)
                }
                Text("Find Exact Issue", color = InkShelf2Colors.Text,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(16.dp))
                    .border(1.dp, InkShelf2Colors.AccentLight.copy(alpha = 0.45f),
                        RoundedCornerShape(16.dp))
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(if (target.batcaveSeriesPath != null) "BATCAVE EVENT ORDER"
                    else "METRON READING ORDER", color = InkShelf2Colors.AccentLight,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(target.displayName, color = InkShelf2Colors.Text,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (target.yearBegan != null)
                        "Matching series start year: " + target.yearBegan +
                            " · Requested issue: #" + target.number
                    else "The edition year needs confirmation before automatic reading.",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
                Text("Comic series are checked against your installed extensions — not just their covers.",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp)
            }
        }
        item {
            Row(modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                InkShelf2SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholderText = "Search series in your sources",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { searchEpoch += 1 }) {
                    Text("Search", color = InkShelf2Colors.AccentLight)
                }
            }
        }
        if (loading || checking != null) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp),
                        color = InkShelf2Colors.AccentLight, strokeWidth = 2.dp)
                    Text(
                        if (checking != null) "Checking the source's actual issues…"
                        else "Finding the correct comic edition…",
                        color = InkShelf2Colors.Muted, fontSize = 12.sp,
                    )
                }
            }
        }
        if (message != null) {
            item {
                Text(message.orEmpty(), modifier = Modifier.padding(horizontal = 17.dp),
                    color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
            }
        }
        if (!loading) {
            item {
                Text(
                    matching.size.toString() + " matching year · " +
                        uncertain.size + " year unknown · " +
                        other.size + " other editions" +
                        (if (failedSources > 0) " · $failedSources sources unavailable" else ""),
                    modifier = Modifier.padding(horizontal = 17.dp),
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
            }
            if (results.isEmpty()) {
                item {
                    Text("No matching series returned. Try a shorter title, or check your enabled extensions.",
                        modifier = Modifier.padding(horizontal = 17.dp),
                        color = InkShelf2Colors.Muted, fontSize = 12.sp)
                }
            }
            items(visible, key = { "${it.manga.source}:${it.manga.url}" }) { candidate ->
                val kind = InkShelf2GuideEdition.classify(target, candidate.manga.title)
                val label = when (kind) {
                    InkShelf2GuideEdition.Match.MATCHING -> "MATCHING YEAR · OPEN SERIES DETAILS"
                    InkShelf2GuideEdition.Match.UNVERIFIED -> "YEAR UNKNOWN · INSPECT SERIES"
                    InkShelf2GuideEdition.Match.OTHER_RUN -> "DIFFERENT SERIES YEAR · INSPECT"
                    InkShelf2GuideEdition.Match.OTHER_TITLE -> "OTHER TITLE · INSPECT"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(13.dp))
                        .border(1.dp, InkShelf2Colors.Border, RoundedCornerShape(13.dp))
                        .clickable(enabled = checking == null) { inspect(candidate) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = candidate.manga,
                        contentDescription = "Cover of " + candidate.manga.title,
                        modifier = Modifier.width(72.dp).aspectRatio(0.70f)
                            .clip(RoundedCornerShape(7.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(candidate.manga.title, color = InkShelf2Colors.Text,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(candidate.sourceName, color = InkShelf2Colors.Muted, fontSize = 11.sp)
                        Text(label, color = InkShelf2Colors.AccentLight,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("TAP TO VIEW COMIC AND ISSUES  ›",
                            color = InkShelf2Colors.Text, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (other.isNotEmpty()) {
                item {
                    TextButton(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = { showOtherRuns = !showOtherRuns },
                    ) {
                        Text(if (showOtherRuns) "Hide other editions"
                            else "Show " + other.size + " other editions",
                            color = InkShelf2Colors.AccentLight)
                    }
                }
            }
        }
    }
}

/** Saves a verified source per Metron series, not per guide or generic title. */
internal object InkShelf2GuideSourceLinks {
    private const val PREFS = "inkshelf2_guide_verified_sources"
    private fun key(issue: InkShelf2GuideIssue): String? =
        issue.seriesId?.takeIf { it > 0 }?.let { "series_" + it }

    fun get(context: Context, issue: InkShelf2GuideIssue): Long? {
        val name = key(issue) ?: return null
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(name, -1).takeIf { it > 0 }
    }

    fun put(context: Context, issue: InkShelf2GuideIssue, manga: Manga) {
        if (InkShelf2GuideEdition.classify(issue, manga.title) !=
            InkShelf2GuideEdition.Match.MATCHING
        ) return
        val name = key(issue) ?: return
        val id = manga.id ?: return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(name, id).apply()
    }
}
