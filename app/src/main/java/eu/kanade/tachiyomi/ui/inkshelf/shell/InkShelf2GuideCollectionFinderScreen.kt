package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException

/**
 * Pilot: collection-first Civil War guide.
 * Publisher/reprint contents are known; source availability is discovered live.
 * Results open the SAME InkShelf 2 comic details route as normal covers.
 */
@Composable
internal fun InkShelf2GuideCollectionFinderScreen(
    collection: InkShelf2GuideCollections.Collection,
    padding: PaddingValues,
    sources: List<CatalogueSource>,
    onBack: () -> Unit,
    onOpenComic: (Manga) -> Unit,
) {
    val engine = remember { InkShelf2SourcesEngine() }
    var query by rememberSaveable(collection.id) { mutableStateOf(collection.query) }
    var searchEpoch by remember(collection.id) { mutableIntStateOf(0) }
    var results by remember(collection.id) {
        mutableStateOf<List<InkShelf2SourcesEngine.SourceResult>>(emptyList())
    }
    var loading by remember(collection.id) { mutableStateOf(false) }
    var problem by remember(collection.id) { mutableStateOf<String?>(null) }
    var failedSources by remember(collection.id) { mutableIntStateOf(0) }
    var showOther by remember(collection.id) { mutableStateOf(false) }
    val sourceIds = sources.map { it.id }

    BackHandler { onBack() }

    LaunchedEffect(collection.id, sourceIds, searchEpoch) {
        loading = true
        results = emptyList()
        problem = null
        failedSources = 0
        showOther = false
        try {
            val lookup = query.trim()
            if (lookup.length < 2) {
                problem = "Enter at least two letters to search collections."
            } else if (sources.isEmpty()) {
                problem = "Enable a comic source in Menu to find this collected edition."
            } else {
                val found = engine.searchAcross(sources, lookup)
                failedSources = found.failedSources
                results = found.results
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            problem = "Could not search installed sources. Check your extensions."
        } finally {
            loading = false
        }
    }

    val matches = results.filter {
        InkShelf2GuideCollections.classifySourceTitle(collection, it.manga.title) ==
            InkShelf2GuideCollections.SourceMatch.TITLE_MATCH
    }
    val possible = results.filter {
        InkShelf2GuideCollections.classifySourceTitle(collection, it.manga.title) ==
            InkShelf2GuideCollections.SourceMatch.POSSIBLE
    }
    val other = results.filter {
        InkShelf2GuideCollections.classifySourceTitle(collection, it.manga.title) ==
            InkShelf2GuideCollections.SourceMatch.OTHER
    }
    val visible = matches + possible + (if (showOther) other else emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back to event guide",
                        tint = InkShelf2Colors.Text,
                    )
                }
                Text(
                    "Collected Editions",
                    color = InkShelf2Colors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        InkShelf2Colors.AccentLight.copy(alpha = 0.55f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("COLLECTED EDITION · " + collection.scope.uppercase(java.util.Locale.ROOT), color = InkShelf2Colors.AccentLight,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(collection.title, color = InkShelf2Colors.Text,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Published edition (" + collection.publishedYear + ") · " +
                        collection.issueCount + " included original issues",
                    color = InkShelf2Colors.AccentLight, fontSize = 12.sp,
                )
                collection.included.forEach { group ->
                    Text("• " + group.label, color = InkShelf2Colors.Text, fontSize = 12.sp)
                }
                Text(
                    "This book's listed contents are verified. Search results from extensions " +
                        "are only candidates until their contents can be checked.",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
                Text(
                    (if (collection.scope == "Prelude")
                        "This book covers the event prelude, not the whole event. "
                    else
                        "This book collects the main storyline; other event tie-ins may be separate. ") +
                        "Opening it does not mark individual guide issues as read.",
                    color = InkShelf2Colors.Muted, fontSize = 11.sp,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkShelf2SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholderText = "Search a collected edition",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { searchEpoch += 1 }) {
                    Text("Search", color = InkShelf2Colors.AccentLight)
                }
            }
        }
        if (loading) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 17.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = InkShelf2Colors.AccentLight,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "Searching your installed comic sources for the collection…",
                        color = InkShelf2Colors.Muted, fontSize = 12.sp,
                    )
                }
            }
        }
        if (problem != null) {
            item {
                Text(problem.orEmpty(), color = InkShelf2Colors.AccentLight,
                    modifier = Modifier.padding(horizontal = 17.dp),
                    fontSize = 12.sp)
            }
        }
        if (!loading && problem == null) {
            item {
                Text(
                    matches.size.toString() + " title matches · " +
                        possible.size + " possible editions" +
                        (if (failedSources > 0) " · $failedSources sources failed" else ""),
                    modifier = Modifier.padding(horizontal = 17.dp),
                    fontSize = 12.sp, color = InkShelf2Colors.Muted,
                )
            }
            if (matches.isEmpty() && possible.isEmpty()) {
                item {
                    Text(
                        "No clear collection match returned. Try the full title or inspect other search results.",
                        modifier = Modifier.padding(horizontal = 17.dp),
                        fontSize = 12.sp, color = InkShelf2Colors.Muted,
                    )
                }
            }
            items(visible, key = { "${it.manga.source}:${it.manga.url}" }) { candidate ->
                val classification = InkShelf2GuideCollections.classifySourceTitle(
                    collection, candidate.manga.title,
                )
                val descriptionVerified = InkShelf2GuideCollections.sourceDescriptionConfirmsContents(
                    collection, candidate.manga.description,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(13.dp))
                        .border(
                            1.dp, InkShelf2Colors.Border, RoundedCornerShape(13.dp),
                        )
                        .clickable { onOpenComic(candidate.manga) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = candidate.manga,
                        contentDescription = "Source cover of " + candidate.manga.title,
                        modifier = Modifier.width(76.dp).aspectRatio(0.70f)
                            .clip(RoundedCornerShape(7.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            candidate.manga.title,
                            color = InkShelf2Colors.Text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(candidate.sourceName, color = InkShelf2Colors.Muted, fontSize = 11.sp)
                        Text(
                            when {
                                descriptionVerified ->
                                    "SOURCE DESCRIPTION LISTS ALL " + collection.issueCount + " CORE ISSUES"
                                classification == InkShelf2GuideCollections.SourceMatch.TITLE_MATCH ->
                                    "TITLE MATCH · CONTENTS NOT CONFIRMED"
                                else -> "POSSIBLE EDITION · CONTENTS NOT CONFIRMED"
                            },
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "VIEW COMIC DETAILS AND ISSUES  ›",
                            color = InkShelf2Colors.Text,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            if (other.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { showOther = !showOther },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            if (showOther) "Hide unrelated search results"
                            else "Show " + other.size + " other results",
                            color = InkShelf2Colors.AccentLight,
                        )
                    }
                }
            }
        }
    }
}
