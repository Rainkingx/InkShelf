package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Event landing page, completely separate from extension comic details.
 * Search uses BatCave when enabled; each series is a real source Manga.
 * No synthetic book, chapter, or library entry is ever created here.
 */
@Composable
internal fun InkShelf2SpotlightEventScreen(
    event: InkShelf2Spotlight.Event,
    sources: List<CatalogueSource>,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (Manga) -> Unit,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val engine = remember { InkShelf2SourcesEngine() }
    val preferred = InkShelf2Spotlight.preferredSource(sources)
    val sourceIds = sources.map { it.id }
    var tryOtherSources by remember(event.id) { mutableStateOf(false) }
    var lookingUp by remember(event.id) { mutableStateOf(true) }
    var relatedFeed by remember(event.id) { mutableStateOf<List<InkShelf2PulseArticle>>(emptyList()) }
    val matches = remember(event.id) { mutableStateMapOf<String, InkShelf2Spotlight.Match>() }
    val artwork = remember(event.id) { mutableStateMapOf<String, String>() }

    LaunchedEffect(event.id, preferred?.id, sourceIds, tryOtherSources) {
        matches.clear()
        if (preferred == null) {
            lookingUp = false
            return@LaunchedEffect
        }
        lookingUp = true
        // Limit concurrent requests so a single extension is not overwhelmed.
        val limit = Semaphore(2)
        try {
            supervisorScope {
                event.series.map { series ->
                    async {
                        limit.withPermit {
                            val first = engine.searchAcross(listOf(preferred), series.title)
                            var match = InkShelf2Spotlight.bestMatch(series, first.results)
                            if (match == null && tryOtherSources) {
                                val other = engine.searchAcross(
                                    sources.filterNot { it.id == preferred.id },
                                    series.title,
                                )
                                match = InkShelf2Spotlight.bestMatch(series, other.results)
                            }
                            if (match != null) matches[series.id] = match
                        }
                    }
                }.awaitAll()
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Partial source results remain visible. Reading never depends on this page.
        } finally {
            lookingUp = false
        }
    }

    LaunchedEffect(event.id) {
        supervisorScope {
            val limiter = Semaphore(3)
            event.series.map { series ->
                async {
                    limiter.withPermit {
                        InkShelf2HeroArtwork.findCover(context, series.editionTitle)
                            ?.let { artwork[series.id] = it }
                    }
                }
            }.awaitAll()
        }
    }

    LaunchedEffect(event.id) {
        val old = InkShelf2PulseFeed.cached(context) + InkShelf2PulseArchive.cached(context)
        relatedFeed = old.distinctBy { it.url }
        try {
            relatedFeed = (InkShelf2PulseFeed.refresh(context) +
                InkShelf2PulseArchive.cached(context)).distinctBy { it.url }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Show the cached news if a publisher feed is temporarily unavailable.
        }
    }

    val related = InkShelf2Spotlight.related(event, relatedFeed)
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "event-title") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Back to Discover",
                            tint = InkShelf2Colors.Text)
                    }
                    Text("DISCOVER / SPOTLIGHT", color = InkShelf2Colors.AccentLight,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                val lead = event.leadSeries
                val leadCover = artwork[lead.id] ?: matches[lead.id]?.manga?.thumbnail_url
                Box(
                    modifier = Modifier.fillMaxWidth().height(224.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(InkShelf2Colors.SurfaceRaised),
                ) {
                    if (!leadCover.isNullOrBlank()) {
                        AsyncImage(
                            model = leadCover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(
                                InkShelf2Colors.Background.copy(alpha = 0.95f),
                                InkShelf2Colors.Background.copy(alpha = 0.7f),
                                InkShelf2Colors.Background.copy(alpha = 0.14f),
                            )),
                        ),
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                            .fillMaxWidth(0.85f),
                    ) {
                        Text(event.label, color = InkShelf2Colors.AccentLight,
                            fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(event.title, color = Color.White,
                            fontWeight = FontWeight.Black, fontSize = 27.sp,
                            lineHeight = 29.sp, maxLines = 3)
                        Spacer(Modifier.height(8.dp))
                        Text(event.series.size.toString() + " CONNECTED SERIES",
                            color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(event.summary, color = InkShelf2Colors.Text, fontSize = 14.sp,
                    lineHeight = 21.sp)
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.officialUrl)))
                    }
                }) {
                    Text("Official " + event.publisher + " event page",
                        color = InkShelf2Colors.AccentLight)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.OpenInNew, null,
                        tint = InkShelf2Colors.AccentLight, modifier = Modifier.size(15.dp))
                }
            }
        }

        item(key = "series-intro") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Explore the series", color = InkShelf2Colors.Text,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        preferred == null -> "No enabled comic extension. Enable BatCave or another source in Menu."
                        lookingUp -> "Checking " + preferred.name + " for the correct editions…"
                        tryOtherSources -> "BatCave first, then other enabled sources for missing series."
                        else -> "Using " + preferred.name + " · No duplicate source editions."
                    },
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
            }
        }

        items(event.series, key = { it.id }) { series ->
            val resolved = matches[series.id]
            val coverUrl = artwork[series.id] ?: resolved?.manga?.thumbnail_url
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.82f))
                    .border(1.dp, InkShelf2Colors.Border, RoundedCornerShape(15.dp))
                    .padding(13.dp),
            ) {
                Text(series.title + " (" + series.year + ")",
                    color = InkShelf2Colors.Text, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(100.dp).height(143.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(InkShelf2Colors.Background)) {
                        if (!coverUrl.isNullOrBlank()) {
                            AsyncImage(model = coverUrl,
                                contentDescription = "Cover for " + series.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(series.tagline, color = InkShelf2Colors.Text,
                            fontSize = 12.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                resolved?.edition == InkShelf2Spotlight.Edition.VERIFIED ->
                                    "MATCHED · " + resolved.sourceName
                                resolved?.edition == InkShelf2Spotlight.Edition.CHECK_YEAR ->
                                    "CHECK PUBLICATION YEAR · " + resolved.sourceName
                                lookingUp -> "Finding series…"
                                else -> "Not found on selected source"
                            },
                            color = if (resolved != null) InkShelf2Colors.AccentLight
                                else InkShelf2Colors.Muted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        )
                        Spacer(Modifier.height(5.dp))
                        if (resolved != null) {
                            TextButton(onClick = { onOpen(resolved.manga) }) {
                                Text(
                                    if (resolved.edition == InkShelf2Spotlight.Edition.VERIFIED)
                                        "OPEN SERIES" else "REVIEW EDITION",
                                    color = InkShelf2Colors.AccentLight,
                                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        } else if (lookingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = InkShelf2Colors.AccentLight, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }

        if (!lookingUp && !tryOtherSources && sources.size > 1 &&
            event.series.any { matches[it.id] == null }) {
            item(key = "source-fallback") {
                TextButton(
                    onClick = { tryOtherSources = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text("Try other enabled sources for missing series",
                        color = InkShelf2Colors.AccentLight)
                }
            }
        }

        item(key = "pulse-title") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("In The Pulse", color = InkShelf2Colors.Text,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text("News and previews about this universe",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp)
            }
        }

        if (related.isNotEmpty()) {
            item(key = "pulse-news") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(related, key = { it.url }) { article ->
                        Column(
                            modifier = Modifier.width(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(InkShelf2Colors.SurfaceRaised)
                                .clickable {
                                    InkShelf2PulseFeed.openArticle(context, article.url)
                                }.padding(12.dp),
                        ) {
                            if (!article.imageUrl.isNullOrBlank()) {
                                AsyncImage(model = article.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(105.dp)
                                        .clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.height(9.dp))
                            }
                            Text(article.publisher.uppercase(),
                                color = InkShelf2Colors.AccentLight, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(article.title, color = InkShelf2Colors.Text,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            item(key = "pulse-empty") {
                Text("No matching Pulse headlines yet. See the official event page for announcements.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = InkShelf2Colors.Muted, fontSize = 12.sp)
            }
        }
    }
}
