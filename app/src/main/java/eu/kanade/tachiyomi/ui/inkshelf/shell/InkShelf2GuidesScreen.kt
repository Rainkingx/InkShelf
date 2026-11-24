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
import androidx.compose.runtime.DisposableEffect
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
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Only locally verified reading orders are visible; Metron supplies artwork. */
@Composable
internal fun InkShelf2GuidesScreen(
    padding: PaddingValues,
    onReadIssue: (Int, InkShelf2GuideIssue) -> Unit,
    refreshEpoch: Int = 0,
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
    var progress by remember { mutableStateOf<Map<Int, InkShelf2GuideProgress.EventState>>(emptyMap()) }
    LaunchedEffect(refreshEpoch, installed) {
        try {
            progress = InkShelf2GuideProgress.load(context, installed)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // A local database refresh failure does not stop the guide from opening.
        }
        started = InkShelf2GuidesData.startedGuides(context)
    }
    var guideSearch by rememberSaveable { mutableStateOf("") }
    var guideFilter by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All", "Marvel", "DC", "Started")
    val activeFilter = guideFilter.takeIf { it in filters } ?: "All"
    val verifiedStarted = (started + progress.filterValues { it.readIds.isNotEmpty() || it.partialIds.isNotEmpty() }.keys)
        .intersect(catalogue.map { it.id }.toSet())
    val connected = InkShelf2GuidesData.configured(context)
    // Keep artwork loading independent of lazy cards and hero rotation. A
    // single sequential coroutine completes all installed event covers.
    LaunchedEffect(connected, installed, chosen?.id) {
        if (connected) {
            // Prioritise the opened event. Cancel unrelated hero lookups while reading.
            val wanted = chosen?.let { current -> installed.filter { it.id == current.id } } ?: installed
            for (guide in wanted) {
                if (eventCovers.containsKey(guide.id)) continue
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
            onReadIssue = { issue -> onReadIssue(selected.id, issue) },
            progress = progress[selected.id] ?: InkShelf2GuideProgress.EventState(),
            refreshEpoch = refreshEpoch,
            openingIssueId = openingIssueId,
            readingError = readingError,
        )
        return
    }
    val openGuide: (InkShelf2Guide) -> Unit = { item -> chosen = item }
    val inProgress = catalogue.filter { guide ->
        if (guide.id !in verifiedStarted) false else {
            val issues = guide.batcaveSlug?.let { InkShelf2BundledEventOrders.get(it, 1)?.issues }.orEmpty()
            val read = InkShelf2GuideProgress.effectiveRead(context, guide.id, progress[guide.id] ?: InkShelf2GuideProgress.EventState())
            val path = InkShelf2GuideReadingSelection.issuesForMode(issues, InkShelf2GuidesData.essentialMode(context, guide.id))
            path.isNotEmpty() && path.any { it.id.toString() !in read }
        }
    }
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
        if (!browsing && inProgress.isNotEmpty()) {
            item(key = "guide-continue") {
                InkShelf2SectionHeading("Continue Reading", onClick = null)
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(inProgress, key = { it.id }) { guide ->
                        InkShelf2EventCoverCard(guide, width = 105.dp, progress = progress[guide.id], onOpen = { openGuide(guide) })
                    }
                }
            }
        }
        item(key = "verified-guides-heading") {
            InkShelf2SectionHeading("All Events (" + matching.size + ")", onClick = null)
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
        items(matching.chunked(3), key = { row -> "event-row-" + row.first().id }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEach { guide ->
                    Box(Modifier.weight(1f)) {
                        InkShelf2EventCoverCard(guide, width = null, progress = progress[guide.id], onOpen = { openGuide(guide) })
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
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
private fun InkShelf2EventCoverCard(
    guide: InkShelf2Guide,
    width: androidx.compose.ui.unit.Dp?,
    progress: InkShelf2GuideProgress.EventState?,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val all = remember(guide.batcaveSlug) {
        guide.batcaveSlug?.let { InkShelf2BundledEventOrders.get(it, 1)?.issues }.orEmpty()
    }
    val read = InkShelf2GuideProgress.effectiveRead(context, guide.id, progress ?: InkShelf2GuideProgress.EventState())
    val path = InkShelf2GuideReadingSelection.issuesForMode(all, InkShelf2GuidesData.essentialMode(context, guide.id))
    val completed = path.count { it.id.toString() in read }
    val percentage = if (path.isNotEmpty() && completed > 0) completed * 100 / path.size else null
    InkShelf2CoverCard(
        imageModel = guide.imageUrl,
        title = guide.name,
        onClick = onOpen,
        percent = percentage,
        badgeText = if (all.isEmpty()) null else all.size.toString(),
        width = width,
        libraryShape = true,
    )
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
    progress: InkShelf2GuideProgress.EventState,
    refreshEpoch: Int,
    openingIssueId: Int?,
    readingError: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember(guide.id) { mutableStateOf<List<InkShelf2GuideIssue>>(emptyList()) }
    var nextPage by remember(guide.id) { mutableStateOf(1) }
    var hasMore by remember(guide.id) { mutableStateOf(true) }
    var loading by remember(guide.id) { mutableStateOf(false) }
    var essential by remember(guide.id) { mutableStateOf(InkShelf2GuidesData.essentialMode(context, guide.id)) }
    var description by remember(guide.id) { mutableStateOf<String?>(null) }
    var manualRevision by remember(guide.id) { mutableStateOf(0) }
    val readIds = remember(guide.id, progress, manualRevision, refreshEpoch) {
        InkShelf2GuideProgress.effectiveRead(context, guide.id, progress)
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
    // Only batch series with multiple needed covers. One-off tie-ins use one exact request.
    val multiIssueSeries = remember(selected) {
        selected.mapNotNull(InkShelf2GuidesData::issueArtworkKey)
            .groupBy { it.seriesKey }
            .filterValues { keys -> keys.map { it.number }.distinct().size > 1 }
            .keys
    }
    val lastOpened = InkShelf2GuidesData.lastOpenedIssue(context, guide.id)
    val nextIssue = selected.firstOrNull { it.id == lastOpened && it.id.toString() !in readIds && !it.sourceUnavailable }
        ?: selected.firstOrNull { it.id in progress.partialIds && it.id.toString() !in readIds && !it.sourceUnavailable }
        ?: InkShelf2GuideReadingSelection.next(selected, readIds)
    val completed = selected.count { it.id.toString() in readIds }
    val essentialMissing = essential && all.isNotEmpty() && all.none { it.isEssential }
    val currentlyOpening = openingIssueId != null

    InkShelf2DetailPage(
        imageModel = guide.imageUrl,
        title = guide.name,
        subtitle = guide.publisher.uppercase() + " · EVENT",
        attribution = if (all.isEmpty()) "Loading reading order…" else
            all.size.toString() + " issues · " + completed + " of " + selected.size + " read",
        padding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 25.dp,
        ),
        onBack = onBack,
    ) {
        item(key = "event-details") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    description ?: "Explore this event in its intended reading order. " +
                        "Choose the main storyline or every available tie-in, then start reading.",
                    color = InkShelf2Colors.Muted, fontSize = 13.sp, lineHeight = 19.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    InkShelf2ActionButton(
                        label = if (essential) "✓ Essential" else "Essential",
                        outlined = !essential,
                        onClick = { essential = true; InkShelf2GuidesData.setEssentialMode(context, guide.id, true) },
                        modifier = Modifier.weight(1f),
                    )
                    InkShelf2ActionButton(
                        label = if (!essential) "✓ Complete" else "Complete",
                        outlined = essential,
                        onClick = { essential = false; InkShelf2GuidesData.setEssentialMode(context, guide.id, false) },
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
                        completed == selected.size -> "READ AGAIN"
                        completed > 0 || lastOpened != null || selected.any { it.id in progress.partialIds } -> "CONTINUE READING"
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
                    InkShelf2GuideIssueArtwork(
                        issue, progress.issues[issue.id]?.coverUrl, checked,
                        batch = InkShelf2GuidesData.issueArtworkKey(issue)?.seriesKey in multiIssueSeries,
                    )
                    Icon(
                        Icons.Outlined.CheckCircleOutline,
                        contentDescription = if (checked) "Mark unread in guide" else "Mark read in guide",
                        tint = if (checked) InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                        modifier = Modifier.size(23.dp).clickable {
                            InkShelf2GuidesData.setGuideRead(context, guide.id, issue.id, !checked)
                            manualRevision++
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(issue.displayName, color = InkShelf2Colors.Text,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("STEP " + issue.position +
                            if (checked) " · READ" else if (issue.id in progress.partialIds) " · IN PROGRESS" else " · " + issue.role.ifBlank { "ISSUE" },
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


/** Visible/near-visible rows request one coalesced series lookup, with exact fallback. */
@Composable
private fun InkShelf2GuideIssueArtwork(
    issue: InkShelf2GuideIssue,
    sourceCover: String?,
    read: Boolean,
    batch: Boolean,
) {
    val context = LocalContext.current
    val key = remember(issue) { InkShelf2GuidesData.issueArtworkKey(issue) }
    var cover by remember(issue, sourceCover) {
        mutableStateOf(sourceCover ?: key?.let { InkShelf2GuidesData.cachedIssueCover(context, it) })
    }
    var batchFinished by remember(issue, sourceCover, batch) { mutableStateOf(!batch) }
    DisposableEffect(issue, sourceCover, batch) {
        var active = true
        if (cover == null && batch && key != null) {
            InkShelfMetronCovers.requestIssueCover(
                context = context,
                title = key.title + " (" + key.year + ")",
                issueNumber = key.number,
                onFinished = { if (active) batchFinished = true },
                onFound = { url ->
                    InkShelf2GuidesData.rememberIssueCover(context, key, url)
                    if (active) cover = url
                },
            )
        } else batchFinished = true
        onDispose { active = false }
    }
    LaunchedEffect(issue, sourceCover, batchFinished) {
        if (batchFinished && cover == null && key != null) {
            cover = InkShelf2GuidesData.issueCover(context, issue)
        }
    }
    InkShelf2IssueArtworkFrame(
        imageUrl = cover,
        title = issue.displayName,
        fallbackLabel = if (issue.number.equals("Full", true)) "ISSUE" else "#" + issue.number,
        read = read,
        modifier = Modifier.size(52.dp, 76.dp),
    )
}
