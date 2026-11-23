package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reading Guides: the approved ink-purple mock-up translated into a REAL
 * Metron-backed guide catalogue. Cards appear only for actual published lists.
 * Issue order comes from the list creator; the app never invents crossovers.
 */
@Composable
internal fun InkShelf2GuidesScreen(
    padding: PaddingValues,
    onReadIssue: (InkShelf2GuideIssue) -> Unit,
    onMetronSettings: () -> Unit,
) {
    val context = LocalContext.current
    var guides by remember { mutableStateOf<List<InkShelf2Guide>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf<InkShelf2Guide?>(null) }
    var started by remember { mutableStateOf(InkShelf2GuidesData.startedGuides(context)) }
    var guideSearch by rememberSaveable { mutableStateOf("") }
    var guideFilter by rememberSaveable { mutableStateOf("All") }
    var remoteResults by remember { mutableStateOf<List<InkShelf2Guide>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var featured by remember { mutableStateOf<List<InkShelf2FeaturedGuide>>(emptyList()) }
    var focused by remember { mutableStateOf<List<InkShelf2Guide>>(emptyList()) }
    val connected = InkShelf2GuidesData.configured(context)
    // Local hits appear immediately; server search extends beyond the first
    // page of events. Cancellation prevents stale results replacing new ones.
    LaunchedEffect(guideSearch, guideFilter) {
        remoteResults = emptyList()
        val query = guideSearch.trim()
        if (query.length < 2 || !connected || guideFilter == "Started") {
            searching = false
        } else {
            searching = true
            try {
                delay(450)
                remoteResults = InkShelf2GuidesData.search(
                    context, query,
                    when (guideFilter) {
                        "Marvel" -> "marvel"
                        "DC" -> "dc"
                        else -> null
                    },
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                remoteResults = emptyList()
            } finally {
                searching = false
            }
        }
    }
    BackHandler(enabled = chosen != null) { chosen = null }
    LaunchedEffect(Unit) {
        try {
            guides = InkShelf2GuidesData.lists(context)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Guides must never interfere with the existing comic engine.
        } finally {
            loading = false
        }
    }

    // Query a small, editorially selected set of modern events in the
    // background. Never infer event age from an issue series' start year.
    LaunchedEffect(loading, guides, connected) {
        if (!loading && connected) {
            try {
                InkShelf2GuideFocus.load(context, guides) { focused = it }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                focused = InkShelf2GuideFocus.choose(guides)
            }
        }
    }

    // Curated real crossover guides can sit beyond Metron's first catalogue
    // page. Cache results and preserve the generic hero as a safe fallback.
    LaunchedEffect(loading, guides, connected) {
        if (!loading && connected) {
            try {
                InkShelf2FeaturedGuides.load(context, guides) { resolved ->
                    featured = resolved
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // The regular guide shelves continue to work if discovery fails.
            }
        }
    }

    val selected = chosen
    if (selected != null) {
        InkShelf2GuideDetails(
            guide = selected,
            padding = padding,
            onBack = { chosen = null; started = InkShelf2GuidesData.startedGuides(context) },
            onReadIssue = onReadIssue,
        )
        return
    }

    val catalogue = (guides + focused + featured.map { it.guide } + remoteResults)
        .distinctBy { it.id }
    val curated = InkShelf2GuideFocus.choose(catalogue)
    val modern = InkShelf2GuideFocus.choose(catalogue, InkShelf2GuideFocus.Era.MODERN)
    val classics = InkShelf2GuideFocus.choose(catalogue, InkShelf2GuideFocus.Era.CLASSIC)
    val popular = modern.sortedWith(
        compareByDescending<InkShelf2Guide> { it.rating }.thenBy { it.name },
    ).take(10)
    val marvel = modern.filter { it.publisher.equals("marvel", true) }.take(12)
    val dc = modern.filter { it.publisher.equals("dc", true) }.take(12)
    val continuation = catalogue.firstOrNull { it.id in started }
    val finding = guideSearch.trim()
    val matching = catalogue
        .filter { guide ->
            (finding.isBlank() || listOf(
                guide.name, guide.publisher, guide.author, guide.attribution,
            ).any { it.contains(finding, ignoreCase = true) }) &&
                when (guideFilter) {
                    "Marvel" -> (finding.isNotBlank() ||
                        InkShelf2GuideFocus.identify(guide)?.era == InkShelf2GuideFocus.Era.MODERN) &&
                        guide.publisher.equals("marvel", ignoreCase = true)
                    "DC" -> (finding.isNotBlank() ||
                        InkShelf2GuideFocus.identify(guide)?.era == InkShelf2GuideFocus.Era.MODERN) &&
                        guide.publisher.equals("dc", ignoreCase = true)
                    "Classics" -> InkShelf2GuideFocus.identify(guide)?.era ==
                        InkShelf2GuideFocus.Era.CLASSIC
                    "Started" -> guide.id in started
                    "Archive" -> InkShelf2GuideFocus.identify(guide) == null
                    "Popular" -> finding.isNotBlank() || guide.id in curated.map { it.id }
                    else -> finding.isNotBlank() || guide.id in curated.map { it.id }
                }
        }
        .sortedWith(
            compareByDescending<InkShelf2Guide> {
                if (guideFilter == "Popular") it.rating else 0.0
            }.thenBy { it.name },
        )
    val browsingResults = finding.isNotBlank() || guideFilter != "All"
    val openGuide: (InkShelf2Guide) -> Unit = { item ->
        InkShelf2GuidesData.start(context, item.id)
        started = InkShelf2GuidesData.startedGuides(context)
        chosen = item
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 6.dp,
            bottom = padding.calculateBottomPadding() + 22.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        item(key = "guides-page-heading") {
            InkShelf2PageHeading(
                "Reading Guides",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
        item(key = "guides-hero") {
            if (featured.isNotEmpty()) {
                InkShelf2FeaturedGuidesHero(
                    featured = featured,
                    started = started,
                    onOpenGuide = openGuide,
                )
            } else {
                InkShelf2GuidesHero(
                    guideCount = guides.size,
                    startedCount = started.size,
                    onExplore = {
                        val first = continuation ?: popular.firstOrNull() ?: classics.firstOrNull()
                        if (first != null) openGuide(first) else onMetronSettings()
                    },
                )
            }
        }
        item(key = "guides-search-and-filters") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkShelf2SearchField(
                        value = guideSearch,
                        onValueChange = { guideSearch = it },
                        placeholderText = "Search reading guides…",
                        modifier = Modifier.weight(1f),
                    )
                    if (guideSearch.isNotBlank()) {
                        TextButton(onClick = { guideSearch = "" }) {
                            Text("Clear", color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Marvel", "DC", "Classics", "Started", "Archive")) { option ->
                        val active = guideFilter == option
                        val shape = RoundedCornerShape(22.dp)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) InkShelf2Colors.Accent
                                    else InkShelf2Colors.SurfaceRaised,
                                    shape,
                                )
                                .border(
                                    1.dp,
                                    if (active) InkShelf2Colors.AccentLight
                                    else InkShelf2Colors.Border,
                                    shape,
                                )
                                .clickable { guideFilter = option }
                                .padding(horizontal = 15.dp, vertical = 9.dp),
                        ) {
                            Text(
                                option,
                                color = InkShelf2Colors.Text,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        if (!connected) {
            item {
                InkShelf2GuideNotice(
                    "Connect your Metron token in Menu to load real community reading orders.",
                    onClick = onMetronSettings,
                    action = "Connect Metron",
                )
            }
        } else if (guides.isEmpty()) {
            item {
                InkShelf2GuideNotice(
                    if (loading) "Loading genuine Marvel and DC reading orders…"
                    else "No reading orders available from Metron right now. Check your connection or token.",
                    onClick = onMetronSettings,
                    action = "Metron settings",
                )
            }
        }
        if (browsingResults) {
            item(key = "guide-filter-summary") {
                Text(
                    if (searching) "Searching Metron…"
                    else "Reading Guides (" + matching.size + ")",
                    modifier = Modifier.padding(horizontal = 17.dp),
                    color = InkShelf2Colors.Text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (matching.isEmpty() && !searching) {
                item(key = "guide-no-results") {
                    Text(
                        if (finding.length == 1)
                            "Type another letter to search Metron. No local matches yet."
                        else "No guides found. Try a different title or open Archive.",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        color = InkShelf2Colors.Muted,
                        fontSize = 13.sp,
                    )
                }
            }
            items(matching, key = { "guide-result-" + it.id }) { guide ->
                InkShelf2GuideFeatureCard(
                    guide = guide,
                    readCount = InkShelf2GuidesData.readIds(context, guide.id).size,
                    started = guide.id in started,
                    onClick = { openGuide(guide) },
                )
            }
        } else {
        if (continuation != null) {
            item {
                InkShelf2SectionHeading("Continue Your Guide", onClick = null)
                Spacer(Modifier.height(10.dp))
                InkShelf2GuideFeatureCard(
                    guide = continuation,
                    readCount = InkShelf2GuidesData.readIds(context, continuation.id).size,
                    onClick = { openGuide(continuation) },
                )
            }
        }
        if (popular.isNotEmpty()) {
            item {
                InkShelf2SectionHeading("Popular Modern Events", onClick = null)
                Spacer(Modifier.height(10.dp))
                InkShelf2GuideShelf(
                    guides = popular,
                    onOpen = openGuide,
                    started = started,
                )
            }
        }
        if (marvel.isNotEmpty()) {
            item {
                InkShelf2SectionHeading("Modern Marvel Events", onClick = null)
                Spacer(Modifier.height(10.dp))
                InkShelf2GuideShelf(marvel, openGuide, started)
            }
        }
        if (dc.isNotEmpty()) {
            item {
                InkShelf2SectionHeading("Modern DC Events", onClick = null)
                Spacer(Modifier.height(10.dp))
                InkShelf2GuideShelf(dc, openGuide, started)
            }
        }
        if (classics.isNotEmpty()) {
            item {
                InkShelf2SectionHeading("Landmark Classics (1985–1999)", onClick = null)
                Spacer(Modifier.height(10.dp))
                InkShelf2GuideShelf(classics, openGuide, started)
            }
        }
        if (modern.isEmpty() && !loading) {
            item {
                Text(
                    "No featured modern Metron lists are available yet. Search for an event by name " +
                        "or use Archive to see other community guides.",
                    modifier = Modifier.padding(horizontal = 17.dp),
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
            }
        }
        }
        if (guides.isNotEmpty()) {
            item {
                Text(
                    "Featured events are selected by their event date, not the year a long-running " +
                        "comic series began. Earlier community lists remain in Archive. " +
                        "Issue order is supplied by Metron contributors.",
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 8.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun InkShelf2GuidesHero(
    guideCount: Int,
    startedCount: Int,
    onExplore: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(218.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        InkShelf2Colors.Accent.copy(alpha = 0.87f),
                        Color(0xFF2B123F),
                        InkShelf2Colors.Surface,
                    ),
                ),
                shape,
            )
            .border(1.dp, InkShelf2Colors.AccentLight.copy(alpha = 0.65f), shape)
            .clickable { onExplore() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.90f, size.height * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(InkShelf2Colors.AccentLight.copy(alpha = 0.35f), Color.Transparent),
                    center = center, radius = size.width * 0.7f,
                ),
                center = center,
                radius = size.width * 0.7f,
            )
            drawCircle(
                color = InkShelf2Colors.AccentLight.copy(alpha = 0.24f),
                radius = size.width * 0.51f,
                center = Offset(size.width * 0.93f, size.height * 0.65f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "FOLLOW THE STORY",
                    color = InkShelf2Colors.AccentLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Reading Guides",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Crossovers, events and story arcs — in the right order.",
                    color = Color.White.copy(alpha = 0.91f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkShelf2GuideMetric(guideCount.toString(), "Guides")
                InkShelf2GuideMetric(startedCount.toString(), "Started")
                InkShelf2GuideMetric("Essential", "or Complete")
            }
        }
    }
}

@Composable
private fun InkShelf2GuideMetric(value: String, label: String) {
    Column(
        modifier = Modifier
            .background(InkShelf2Colors.Surface.copy(alpha = 0.72f), RoundedCornerShape(11.dp))
            .border(1.dp, InkShelf2Colors.AccentLight.copy(alpha = 0.4f), RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(value, color = InkShelf2Colors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(label, color = InkShelf2Colors.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun InkShelf2GuideNotice(message: String, onClick: () -> Unit, action: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(13.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(message, color = InkShelf2Colors.Muted, fontSize = 13.sp)
        InkShelf2ActionButton(label = action, onClick = onClick)
    }
}

@Composable
private fun InkShelf2GuideShelf(
    guides: List<InkShelf2Guide>,
    onOpen: (InkShelf2Guide) -> Unit,
    started: Set<Int>,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(guides, key = { it.id }) { guide ->
            val shape = RoundedCornerShape(15.dp)
            Column(
                modifier = Modifier.width(208.dp)
                    .background(InkShelf2Colors.SurfaceRaised, shape)
                    .border(1.dp, InkShelf2Colors.Border, shape)
                    .clickable { onOpen(guide) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                InkShelf2GuideArt(guide, 102)
                Text(
                    guide.name,
                    color = InkShelf2Colors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    InkShelf2GuideFocus.identify(guide)?.let { event ->
                        event.year.toString() + " · " +
                            if (event.era == InkShelf2GuideFocus.Era.CLASSIC) "Landmark classic"
                            else "Modern event"
                    } ?: guide.attribution.ifBlank { "Community reading order" },
                    fontSize = 10.sp,
                    color = InkShelf2Colors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (guide.id in started) "RESUME GUIDE" else "START GUIDE",
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = InkShelf2Colors.AccentLight,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

/** Decorative abstract artwork, never passed off as an official comic cover. */
@Composable
private fun InkShelf2GuideArt(guide: InkShelf2Guide, height: Int) {
    val context = LocalContext.current
    var cover by remember(guide.id, guide.imageUrl) { mutableStateOf(guide.imageUrl) }
    LaunchedEffect(guide.id, guide.imageUrl) {
        cover = try {
            InkShelf2GuidesData.coverUrl(context, guide)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            guide.imageUrl
        }
    }
    val first = if (guide.id % 2 == 0) Color(0xFF332060) else Color(0xFF583077)
    Box(
        modifier = Modifier.fillMaxWidth().height(height.dp)
            .background(
                Brush.linearGradient(listOf(first, InkShelf2Colors.Accent, Color(0xFF0E1029))),
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val offset = Offset(size.width * 0.68f, size.height * 0.42f)
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.height * 0.66f,
                center = offset,
            )
            drawCircle(
                color = InkShelf2Colors.AccentLight.copy(alpha = 0.65f),
                radius = size.height * 0.45f,
                center = offset,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            )
        }
        Icon(
            Icons.Outlined.Public,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(34.dp),
        )
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = "Cover for " + guide.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun InkShelf2GuideFeatureCard(
    guide: InkShelf2Guide,
    readCount: Int,
    onClick: () -> Unit,
    started: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(InkShelf2Colors.SurfaceRaised, shape)
            .border(1.dp, InkShelf2Colors.AccentLight.copy(alpha = 0.55f), shape)
            .clickable { onClick() }.padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.width(94.dp)) { InkShelf2GuideArt(guide, 117) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (started) "CURRENT EVENT" else "EVENT GUIDE", color = InkShelf2Colors.AccentLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(guide.name, color = InkShelf2Colors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (readCount == 0) "Ready to start" else "$readCount issues marked read",
                color = InkShelf2Colors.Muted, fontSize = 11.sp,
            )
            Text(if (started) "RESUME GUIDE  ›" else "START GUIDE  ›", color = InkShelf2Colors.AccentLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The full ordered issue checklist is separate from the directory. */
@Composable
private fun InkShelf2GuideDetails(
    guide: InkShelf2Guide,
    padding: PaddingValues,
    onBack: () -> Unit,
    onReadIssue: (InkShelf2GuideIssue) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember(guide.id) { mutableStateOf<List<InkShelf2GuideIssue>>(emptyList()) }
    var nextPage by remember(guide.id) { mutableStateOf(1) }
    var hasMore by remember(guide.id) { mutableStateOf(true) }
    var loading by remember(guide.id) { mutableStateOf(false) }
    var essential by remember(guide.id) { mutableStateOf(true) }
    var missingOnly by remember(guide.id) { mutableStateOf(false) }
    var readIds by remember(guide.id) { mutableStateOf(InkShelf2GuidesData.readIds(context, guide.id)) }

    suspend fun loadPage() {
        if (loading || !hasMore) return
        loading = true
        try {
            val loaded = InkShelf2GuidesData.issues(context, guide.id, nextPage)
            if (loaded.issues.isNotEmpty()) {
                all = (all + loaded.issues).distinctBy { it.id }.sortedBy { it.position }
                nextPage += 1
            }
            hasMore = loaded.more && loaded.issues.isNotEmpty()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            hasMore = false
        } finally {
            loading = false
        }
    }
    LaunchedEffect(guide.id) { loadPage() }
    val filtered = all.filter { (!essential || it.isEssential) && (!missingOnly || it.id.toString() !in readIds) }
    val nextUnread = all.firstOrNull { (!essential || it.isEssential) && it.id.toString() !in readIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 5.dp,
            bottom = padding.calculateBottomPadding() + 25.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 7.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to guides", tint = InkShelf2Colors.Text)
                }
                Text("Reading Guide", color = InkShelf2Colors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(16.dp))
                    .border(1.dp, InkShelf2Colors.AccentLight.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(guide.name, color = InkShelf2Colors.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Order by " + (guide.author.ifBlank { "a Metron contributor" }) +
                        if (guide.attribution.isBlank()) "" else " · " + guide.attribution,
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
                Text(
                    readIds.size.toString() + " issues marked read • " + all.size + " issues loaded",
                    color = InkShelf2Colors.AccentLight, fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkShelf2ActionButton(
                        label = if (essential) "★ Essential" else "Essential",
                        outlined = !essential,
                        onClick = { essential = true },
                        modifier = Modifier.weight(1f),
                    )
                    InkShelf2ActionButton(
                        label = if (!essential) "✓ Complete" else "Complete",
                        outlined = essential,
                        onClick = { essential = false },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (nextUnread != null) {
                    InkShelf2ActionButton(
                        label = "Read Next: " + nextUnread.displayName,
                        onClick = { onReadIssue(nextUnread) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { missingOnly = !missingOnly }) {
                    Text(
                        if (missingOnly) "Show all issues"
                        else "Catch Me Up • Show unread links",
                        color = InkShelf2Colors.AccentLight,
                    )
                }
                Text(
                    "Catch Me Up lists missing issues without spoiling their plots. " +
                        "Only issues marked Core, Prologue or Epilogue appear in Essential; " +
                        "unlabelled issues are included to avoid skipping an important part.",
                    fontSize = 11.sp, color = InkShelf2Colors.Muted,
                )
            }
        }
        if (all.isEmpty()) {
            item {
                Text(
                    if (loading) "Loading ordered issues from Metron…" else "No issues available for this reading list.",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    color = InkShelf2Colors.Muted,
                )
            }
        } else {
            items(filtered, key = { it.id }) { issue ->
                val checked = issue.id.toString() in readIds
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(13.dp))
                        .border(
                            1.dp,
                            if (checked) InkShelf2Colors.AccentLight.copy(alpha = 0.45f)
                            else InkShelf2Colors.Border,
                            RoundedCornerShape(13.dp),
                        ).padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.CheckCircleOutline,
                        contentDescription = if (checked) "Mark unread" else "Mark read",
                        tint = if (checked) InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                        modifier = Modifier.size(25.dp).clickable {
                            readIds = InkShelf2GuidesData.setRead(context, guide.id, issue.id, !checked)
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(issue.displayName, fontSize = 13.sp, color = InkShelf2Colors.Text, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Step " + issue.position + "  •  " + issue.role.ifBlank { "Unlabelled issue" },
                            fontSize = 10.sp, color = InkShelf2Colors.Muted,
                        )
                    }
                    Text(
                        "READ ›", color = InkShelf2Colors.AccentLight,
                        modifier = Modifier.clickable { onReadIssue(issue) }.padding(5.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (filtered.isEmpty()) {
                item { Text("No unread issues in this selection.", modifier = Modifier.padding(17.dp), color = InkShelf2Colors.Muted) }
            }
        }
        if (hasMore && !loading) {
            item {
                InkShelf2ActionButton(
                    label = "Load More Issues",
                    onClick = { scope.launch { loadPage() } },
                    outlined = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        } else if (loading) {
            item { Text("Loading more issues…", modifier = Modifier.padding(17.dp), color = InkShelf2Colors.Muted) }
        }
    }
}
