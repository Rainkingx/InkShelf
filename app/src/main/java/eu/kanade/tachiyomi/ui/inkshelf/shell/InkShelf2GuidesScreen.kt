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
import kotlinx.coroutines.launch

/** Only locally verified reading orders are visible; Metron supplies artwork. */
@Composable
internal fun InkShelf2GuidesScreen(
    padding: PaddingValues,
    onReadIssue: (InkShelf2GuideIssue) -> Unit,
    onFindCollection: (InkShelf2GuideCollections.Collection) -> Unit,
    onMetronSettings: () -> Unit,
    openingIssueId: Int? = null,
    readingError: String? = null,
) {
    val context = LocalContext.current
    val installed = remember { InkShelf2BatCaveGuides.verified() }
    var eventCovers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val catalogue = installed.map { guide -> guide.copy(imageUrl = eventCovers[guide.id]) }
    var chosen by remember { mutableStateOf<InkShelf2Guide?>(null) }
    var started by remember { mutableStateOf(InkShelf2GuidesData.startedGuides(context)) }
    var guideSearch by rememberSaveable { mutableStateOf("") }
    var guideFilter by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All", "Marvel", "DC", "Started")
    val activeFilter = guideFilter.takeIf { it in filters } ?: "All"
    val verifiedStarted = started.intersect(catalogue.map { it.id }.toSet())
    val connected = InkShelf2GuidesData.configured(context)
    // Keep artwork loading independent of lazy cards and hero rotation. A
    // single sequential coroutine completes all installed event covers.
    LaunchedEffect(connected, installed) {
        if (connected) {
            for (guide in installed) {
                val url = InkShelf2GuidesData.coverUrl(context, guide)
                if (url != null) eventCovers = eventCovers + (guide.id to url)
            }
        }
    }
    BackHandler(enabled = chosen != null) { chosen = null }
    val selected = chosen?.let { selectedGuide ->
        catalogue.firstOrNull { it.id == selectedGuide.id } ?: selectedGuide
    }
    if (selected != null) {
        InkShelf2GuideDetails(
            guide = selected,
            padding = padding,
            onBack = { chosen = null; started = InkShelf2GuidesData.startedGuides(context) },
            onReadIssue = onReadIssue,
            openingIssueId = openingIssueId,
            readingError = readingError,
        )
        return
    }
    val openGuide: (InkShelf2Guide) -> Unit = { item ->
        InkShelf2GuidesData.start(context, item.id)
        started = InkShelf2GuidesData.startedGuides(context)
        chosen = item
    }
    val continuation = catalogue.firstOrNull { it.id in verifiedStarted }
    val finding = guideSearch.trim()
    val matching = catalogue.filter { guide ->
        (finding.isBlank() || guide.name.contains(finding, ignoreCase = true) ||
            guide.publisher.contains(finding, ignoreCase = true)) &&
            when (activeFilter) {
                "Marvel", "DC" -> guide.publisher.equals(activeFilter, true)
                "Started" -> guide.id in verifiedStarted
                else -> true
            }
    }
    val browsing = finding.isNotBlank() || activeFilter != "All"

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
        if (!browsing && catalogue.isNotEmpty()) {
            item(key = "guides-hero") {
                InkShelf2FeaturedGuidesHero(
                    featured = catalogue.map { InkShelf2FeaturedGuide(it.name, it) },
                    started = verifiedStarted,
                    onOpenGuide = openGuide,
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
                        placeholderText = "Search verified guides…",
                        modifier = Modifier.weight(1f),
                    )
                    if (guideSearch.isNotBlank()) {
                        TextButton(onClick = { guideSearch = "" }) {
                            Text("Clear", color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters) { option ->
                        val active = activeFilter == option
                        val shape = RoundedCornerShape(22.dp)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) InkShelf2Colors.Accent else InkShelf2Colors.SurfaceRaised,
                                    shape,
                                )
                                .border(
                                    1.dp,
                                    if (active) InkShelf2Colors.AccentLight else InkShelf2Colors.Border,
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
        if (!browsing && continuation != null) {
            item(key = "guide-continue") {
                InkShelf2SectionHeading("Continue Your Guide", onClick = null)
                Spacer(Modifier.height(10.dp))
                InkShelf2GuideFeatureCard(
                    guide = continuation,
                    readCount = InkShelf2GuidesData.readIds(context, continuation.id).size,
                    onClick = { openGuide(continuation) },
                )
            }
        }
        item(key = "verified-guides-heading") {
            InkShelf2SectionHeading("Verified Event Guides (" + matching.size + ")", onClick = null)
        }
        if (matching.isEmpty()) {
            item(key = "guide-no-results") {
                Text(
                    "No installed guides match this search.",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 13.sp,
                )
            }
        }
        items(matching, key = { "verified-guide-" + it.id }) { guide ->
            InkShelf2GuideFeatureCard(
                guide = guide,
                readCount = InkShelf2GuidesData.readIds(context, guide.id).size,
                started = guide.id in verifiedStarted,
                onClick = { openGuide(guide) },
            )
        }
        if (!connected) {
            item(key = "guide-artwork-settings") {
                InkShelf2GuideNotice(
                    "Your installed reading orders work offline. Connect Metron for event artwork.",
                    onClick = onMetronSettings,
                    action = "Connect Metron",
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
        // Installed event art is owned by the parent screen, not a lazy row.
        if (guide.batcaveSlug == null && guide.imageUrl == null) {
            cover = try {
                InkShelf2GuidesData.coverUrl(context, guide)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                guide.imageUrl
            }
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

/**
 * Event details: artwork, introduction, two reading modes and one start action.
 * A list item also opens the exact issue directly. Source resolution is
 * handled by the Activity/auto-reader, never an intermediate search screen.
 */
@Composable
private fun InkShelf2GuideDetails(
    guide: InkShelf2Guide,
    padding: PaddingValues,
    onBack: () -> Unit,
    onReadIssue: (InkShelf2GuideIssue) -> Unit,
    openingIssueId: Int?,
    readingError: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember(guide.id) { mutableStateOf<List<InkShelf2GuideIssue>>(emptyList()) }
    var nextPage by remember(guide.id) { mutableStateOf(1) }
    var hasMore by remember(guide.id) { mutableStateOf(true) }
    var loading by remember(guide.id) { mutableStateOf(false) }
    var essential by remember(guide.id) { mutableStateOf(true) }
    var description by remember(guide.id) { mutableStateOf<String?>(null) }
    var readIds by remember(guide.id) {
        mutableStateOf(InkShelf2GuidesData.readIds(context, guide.id))
    }

    suspend fun loadPage() {
        if (loading || !hasMore) return
        loading = true
        try {
            val page = guide.batcaveSlug?.let {
                InkShelf2BatCaveGuides.issues(context, it, nextPage)
            } ?: InkShelf2GuidesData.issues(context, guide.id, nextPage)
            if (page.issues.isNotEmpty()) {
                all = (all + page.issues).distinctBy { it.id }.sortedBy { it.position }
                nextPage++
            }
            hasMore = page.more && page.issues.isNotEmpty()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            hasMore = false
        } finally {
            loading = false
        }
    }

    LaunchedEffect(guide.id) {
        repeat(8) {
            if (hasMore) loadPage()
        }
    }
    LaunchedEffect(guide.batcaveSlug) {
        val slug = guide.batcaveSlug
        if (slug != null) {
            try {
                description = InkShelf2BatCaveGuides.synopsis(context, slug)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                description = null
            }
        }
    }

    val selected = InkShelf2GuideReadingSelection.issuesForMode(all, essential)
    val nextIssue = InkShelf2GuideReadingSelection.next(selected, readIds)
    val completed = selected.count { it.id.toString() in readIds }
    val essentialMissing = essential && all.isNotEmpty() && all.none { it.isEssential }
    val currentlyOpening = openingIssueId != null

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 5.dp,
            bottom = padding.calculateBottomPadding() + 25.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(key = "event-back") {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to Reading Guides",
                        tint = InkShelf2Colors.Text)
                }
                Text("READING GUIDE", color = InkShelf2Colors.AccentLight,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        item(key = "event-details") {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(16.dp))
                    .border(1.dp, InkShelf2Colors.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(108.dp)) {
                        InkShelf2GuideArt(guide, 156)
                    }
                    Column(modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(guide.publisher.uppercase() + " · EVENT",
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(guide.name, color = InkShelf2Colors.Text,
                            fontSize = 21.sp, lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold)
                        Text(
                            (if (all.isEmpty()) "Loading issues…" else
                                all.size.toString() + if (hasMore) "+ issues" else " issues") +
                                " · " + completed + " of " + selected.size + " read",
                            color = InkShelf2Colors.Muted, fontSize = 11.sp,
                        )
                    }
                }
                Text(
                    description ?: "Explore this event in its intended reading order. " +
                        "Choose the main storyline or every available tie-in, then start reading.",
                    color = InkShelf2Colors.Text, fontSize = 13.sp, lineHeight = 19.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    InkShelf2ActionButton(
                        label = if (essential) "✓ Essential" else "Essential",
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
                if (essentialMissing) {
                    Text(
                        "No shorter essential path is provided for this event, so the complete order is shown.",
                        color = InkShelf2Colors.Muted, fontSize = 11.sp,
                    )
                }
                InkShelf2ActionButton(
                    label = when {
                        currentlyOpening -> "OPENING ISSUE…"
                        nextIssue == null -> "LOADING READING ORDER…"
                        nextIssue.sourceUnavailable -> "ISSUE UNAVAILABLE"
                        completed == selected.size -> "START AGAIN"
                        completed > 0 -> "CONTINUE READING"
                        else -> "START READING"
                    },
                    onClick = { if (nextIssue != null && !nextIssue.sourceUnavailable) onReadIssue(nextIssue) },
                    enabled = nextIssue != null && !nextIssue.sourceUnavailable && !currentlyOpening,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nextIssue?.sourceUnavailable == true) {
                    Text("The source lists this issue as unavailable. Choose another listed issue to read.",
                        color = InkShelf2Colors.Muted, fontSize = 12.sp)
                }
                if (currentlyOpening) {
                    Text("Finding the exact issue in your installed sources…",
                        color = InkShelf2Colors.Muted, fontSize = 12.sp)
                }
                if (!readingError.isNullOrBlank()) {
                    Text(readingError, color = InkShelf2Colors.AccentLight,
                        fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        item(key = "reading-order-title") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(if (essential) "Essential Reading" else "Complete Reading Order",
                    color = InkShelf2Colors.Text,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(selected.size.toString() + (if (hasMore) "+ listed issues" else " listed issues") +
                    " · Tap an available issue to read",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp)
            }
        }
        if (selected.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (loading) "Loading event issues…"
                        else "The reading order could not be loaded from the provider.",
                        color = InkShelf2Colors.Muted, fontSize = 13.sp)
                    if (!loading) {
                        TextButton(onClick = {
                            all = emptyList()
                            nextPage = 1
                            hasMore = true
                            scope.launch { loadPage() }
                        }) {
                            Text("Retry loading", color = InkShelf2Colors.AccentLight)
                        }
                    }
                }
            }
        } else {
            items(selected, key = { it.id }) { issue ->
                val checked = issue.id.toString() in readIds
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(13.dp))
                        .border(1.dp,
                            if (checked) InkShelf2Colors.AccentLight.copy(alpha = 0.45f)
                            else InkShelf2Colors.Border,
                            RoundedCornerShape(13.dp))
                        .clickable(enabled = !currentlyOpening && !issue.sourceUnavailable) { onReadIssue(issue) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.CheckCircleOutline,
                        contentDescription = if (checked) "Mark unread" else "Mark read",
                        tint = if (checked) InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                        modifier = Modifier.size(23.dp).clickable {
                            readIds = InkShelf2GuidesData.setRead(context, guide.id, issue.id, !checked)
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(issue.displayName, color = InkShelf2Colors.Text,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("STEP " + issue.position +
                            if (checked) " · READ" else " · " + issue.role.ifBlank { "ISSUE" },
                            color = InkShelf2Colors.Muted, fontSize = 10.sp)
                    }
                    Text(if (issue.sourceUnavailable) "UNAVAILABLE"
                        else if (openingIssueId == issue.id) "OPENING" else "READ", color = InkShelf2Colors.AccentLight,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        if (hasMore && !loading) {
            item {
                InkShelf2ActionButton(
                    label = "LOAD MORE ISSUES",
                    onClick = { scope.launch { loadPage() } },
                    outlined = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}
