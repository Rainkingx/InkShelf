package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.activity.compose.BackHandler
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.coroutines.delay
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    initialGuideId: Int? = null,
    onCloseInitialGuide: () -> Unit = {},
    savedGuideIds: Set<Int> = emptySet(),
    onSaveGuide: (Int, Boolean) -> Unit = { _, _ -> },
    enabledSourceIds: Set<Long> = emptySet(),
    onDownloadIssues: (Int, List<InkShelf2GuideIssue>) -> Unit = { _, _ -> },
    downloadingGuideId: Int? = null,
) {
    val context = LocalContext.current
    val installed = remember { InkShelf2BatCaveGuides.verified() }
    var eventCovers by remember {
        mutableStateOf(installed.mapNotNull { guide ->
            InkShelf2GuidesData.cachedEventCover(context, guide)?.let { guide.id to it }
        }.toMap())
    }
    val catalogue = installed.map { guide -> guide.copy(imageUrl = eventCovers[guide.id]) }
    val newlyAdded = remember(context, installed) { InkShelf2GuidesData.newlyAddedGuideIds(context, installed) }
    var timeline by rememberSaveable { mutableStateOf(InkShelf2GuidesData.timelineEnabled(context)) }
    var chosen by remember(initialGuideId) { mutableStateOf(installed.firstOrNull { it.id == initialGuideId }) }
    var relatedBackStack by remember(initialGuideId) { mutableStateOf<List<InkShelf2Guide>>(emptyList()) }
    fun closeGuide() {
        if (relatedBackStack.isNotEmpty()) {
            chosen = relatedBackStack.last()
            relatedBackStack = relatedBackStack.dropLast(1)
        } else if (initialGuideId != null) onCloseInitialGuide() else chosen = null
    }
    var started by remember { mutableStateOf(InkShelf2GuidesData.startedGuides(context)) }
    var guideRevision by remember { mutableStateOf(0) }
    var progressReady by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Map<Int, InkShelf2GuideProgress.EventState>>(emptyMap()) }
    LaunchedEffect(refreshEpoch, installed, guideRevision) {
        progressReady = false
        try {
            progress = InkShelf2GuideProgress.load(context, installed)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // A local database refresh failure does not stop the guide from opening.
        }
        started = InkShelf2GuidesData.startedGuides(context)
        progressReady = true
    }
    var guideSearch by rememberSaveable { mutableStateOf("") }
    var guideFilter by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All", "Marvel", "DC", "Started") + if (newlyAdded.isNotEmpty()) listOf("New") else emptyList()
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
    BackHandler(enabled = chosen != null) {
        guideRevision++
        closeGuide()
    }
    val selected = chosen?.let { selectedGuide ->
        catalogue.firstOrNull { it.id == selectedGuide.id } ?: selectedGuide
    }
    if (selected != null) {
        InkShelf2GuideDetails(
            guide = selected,
            padding = padding,
            onBack = {
                guideRevision++
                closeGuide()
                started = InkShelf2GuidesData.startedGuides(context)
            },
            progressReady = progressReady,
            onProgressChanged = { guideRevision++ },
            saved = selected.id in savedGuideIds,
            onSave = { onSaveGuide(selected.id, selected.id !in savedGuideIds) },
            enabledSourceIds = enabledSourceIds,
            onDownload = { issues -> onDownloadIssues(selected.id, issues) },
            preparingDownloads = downloadingGuideId != null,
            onReadIssue = { issue -> onReadIssue(selected.id, issue) },
            progress = progress[selected.id] ?: InkShelf2GuideProgress.EventState(),
            refreshEpoch = refreshEpoch,
            openingIssueId = openingIssueId,
            readingError = readingError,
            relatedEvents = InkShelf2EventTimeline.related(selected, catalogue),
            onRelatedEvent = { related ->
                relatedBackStack = (relatedBackStack + selected).takeLast(16)
                guideRevision++
                chosen = related
            },
            newEventIds = newlyAdded,
        )
        return
    }
    val openGuide: (InkShelf2Guide) -> Unit = { item -> chosen = item }
    val inProgress = catalogue.filter { guide ->
        if (guide.id !in verifiedStarted) false else {
            val issues = guide.batcaveSlug?.let { InkShelf2BundledEventOrders.get(it, 1)?.issues }.orEmpty()
            val read = InkShelf2GuideProgress.effectiveRead(context, guide.id, progress[guide.id] ?: InkShelf2GuideProgress.EventState())
            val path = InkShelf2GuideReadingSelection.issuesForMode(issues, InkShelf2GuidesData.essentialMode(context, guide.id))
            InkShelf2GuideReadingSelection.hasUnread(path, read)
        }
    }.sortedByDescending { InkShelf2GuidesData.lastOpenedAt(context, it.id) }
    val finding = guideSearch.trim()
    val matching = catalogue.filter { guide ->
        (finding.isBlank() || guide.name.contains(finding, ignoreCase = true) ||
            guide.publisher.contains(finding, ignoreCase = true)) &&
            when (activeFilter) {
                "Marvel", "DC" -> guide.publisher.equals(activeFilter, true)
                "Started" -> guide.id in verifiedStarted
                "New" -> guide.id in newlyAdded
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
                    started = inProgress.map { it.id }.toSet(),
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
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        InkShelf2PageHeading(if (timeline) "Event Timeline" else "All Events (${matching.size})")
                    }
                    IconButton(onClick = {
                        timeline = false
                        InkShelf2GuidesData.setTimelineEnabled(context, false)
                    }) {
                        Icon(Icons.Outlined.GridView, "Grid view",
                            tint = if (!timeline) InkShelf2Colors.AccentLight else InkShelf2Colors.Muted)
                    }
                    IconButton(onClick = {
                        timeline = true
                        InkShelf2GuidesData.setTimelineEnabled(context, true)
                    }) {
                        Icon(Icons.Outlined.Timeline, "Timeline view",
                            tint = if (timeline) InkShelf2Colors.AccentLight else InkShelf2Colors.Muted)
                    }
                }
                if (timeline) {
                    Text("Publication order · ${matching.size} installed events",
                        color = InkShelf2Colors.Muted, fontSize = 12.sp)
                }
            }
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
        if (timeline) {
            items(InkShelf2EventTimeline.ordered(matching), key = { "event-timeline-" + it.id }) { guide ->
                InkShelf2EventTimelineRow(
                    guide = guide,
                    progress = progress[guide.id],
                    isNew = guide.id in newlyAdded,
                    onOpen = { openGuide(guide) },
                )
            }
        } else {
            items(matching.chunked(3), key = { row -> "event-row-" + row.first().id }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    row.forEach { guide ->
                        Box(Modifier.weight(1f)) {
                            InkShelf2EventCoverCard(guide, width = null, progress = progress[guide.id],
                                onOpen = { openGuide(guide) }, isNew = guide.id in newlyAdded)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
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
    isNew: Boolean = false,
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
        badgeText = if (isNew) "NEW" else if (all.isEmpty()) null else all.size.toString(),
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
    progressReady: Boolean,
    onProgressChanged: () -> Unit,
    refreshEpoch: Int,
    openingIssueId: Int?,
    readingError: String?,
    saved: Boolean,
    onSave: () -> Unit,
    enabledSourceIds: Set<Long>,
    onDownload: (List<InkShelf2GuideIssue>) -> Unit,
    preparingDownloads: Boolean,
    relatedEvents: List<InkShelf2Guide>,
    onRelatedEvent: (InkShelf2Guide) -> Unit,
    newEventIds: Set<Int>,
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
    var optionsOpen by remember(guide.id) { mutableStateOf(false) }
    var relatedExpanded by remember(guide.id) { mutableStateOf(false) }
    var confirmReset by remember(guide.id) { mutableStateOf(false) }
    var resetting by remember(guide.id) { mutableStateOf(false) }
    var hideRead by remember(guide.id) { mutableStateOf(InkShelf2GuidesData.displayOption(context, guide.id, "hide_read")) }
    var spoilerSafe by remember(guide.id) { mutableStateOf(InkShelf2GuidesData.displayOption(context, guide.id, "spoilers")) }
    var localArtwork by remember(guide.id) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var artworkReady by remember(guide.id, all, refreshEpoch) { mutableStateOf(false) }
    LaunchedEffect(guide.id, all, refreshEpoch) {
        if (all.isNotEmpty()) {
            try {
                localArtwork = InkShelf2CachedGuideArtwork.load(all)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                localArtwork = emptyMap()
            } finally {
                artworkReady = true
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset event progress?") },
            text = { Text("Reset the complete event, not just the selected reading mode. Matched issues become unread and restart on page one. This also affects shared Library progress. Downloads and bookmarks are kept. Grouped multi-issue chapters are not reset.") },
            confirmButton = { TextButton(onClick = {
                confirmReset = false
                resetting = true
                scope.launch {
                    try {
                        val result = InkShelf2ReadingReset.event(context, guide)
                        manualRevision++
                        onProgressChanged()
                        Toast.makeText(context, "Reset ${result.reset} matched chapters" +
                            if (result.groupedSkipped > 0) "; ${result.groupedSkipped} grouped entries skipped" else "", Toast.LENGTH_LONG).show()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not reset event", Toast.LENGTH_LONG).show()
                    } finally {
                        resetting = false
                    }
                }
            }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
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
    val visibleIssues = if (hideRead) selected.filterNot { it.id.toString() in readIds } else selected
    // Only batch series with multiple needed covers. One-off tie-ins use one exact request.
    val multiIssueSeries = remember(selected) {
        selected.mapNotNull(InkShelf2GuidesData::issueArtworkKey)
            .groupBy { it.seriesKey }
            .filterValues { keys -> keys.map { it.number }.distinct().size > 1 }
            .keys
    }
    val lastOpened = InkShelf2GuidesData.lastOpenedIssue(context, guide.id)
    val nextIssue = InkShelf2GuideReadingSelection.next(selected, readIds, lastOpened, progress.partialIds)
    val completed = selected.count { it.id.toString() in readIds }
    val finished = InkShelf2GuideReadingSelection.finished(selected, readIds)
    val unavailableCount = selected.count { it.sourceUnavailable && it.id.toString() !in readIds }
    val hasProgress = completed > 0 || selected.any { it.id in progress.partialIds || it.id == lastOpened }
    val essentialMissing = essential && all.isNotEmpty() && all.none { it.isEssential }
    val currentlyOpening = openingIssueId != null
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val sourceManager: SourceManager = remember { Injekt.get() }
    var offline by remember(guide.id) {
        mutableStateOf<Map<Int, InkShelf2GuideDownloads.State>>(emptyMap())
    }
    LaunchedEffect(guide.id, progress, enabledSourceIds, refreshEpoch) {
        while (true) {
            try {
                offline = InkShelf2GuideDownloads.snapshot(progress, downloadManager, sourceManager, enabledSourceIds)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // A temporarily unavailable local download cache must not block the event.
            }
            delay(2_500)
        }
    }
    var downloadChoices by remember(guide.id) { mutableStateOf(false) }
    val essentialIssues = InkShelf2GuideReadingSelection.issuesForMode(all, true)
    val unreadIssues = selected.filter { it.id.toString() !in readIds }
    fun eligibleIssues(issues: List<InkShelf2GuideIssue>) =
        issues.filter { !it.sourceUnavailable && offline[it.id]?.canQueue == true }
    if (downloadChoices) {
        AlertDialog(
            onDismissRequest = { downloadChoices = false },
            title = { Text("Download Event Issues") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Only already matched, downloadable issues are queued. Open an unmatched issue first to link it to an available source. Existing offline or queued issues are skipped.")
                    listOf(
                        "Essential" to essentialIssues,
                        "Complete" to all,
                        "Unread in selected order" to unreadIssues,
                    ).forEach { (label, issues) ->
                        val eligible = eligibleIssues(issues)
                        TextButton(
                            enabled = eligible.isNotEmpty() && !preparingDownloads,
                            onClick = { downloadChoices = false; onDownload(eligible) },
                        ) { Text("$label (${eligible.size} of ${issues.size})") }
                    }
                    Text("Your existing download and cleanup settings still apply.", color = InkShelf2Colors.Muted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { downloadChoices = false }) { Text("Close") } },
        )
    }

    InkShelf2DetailPage(
        imageModel = guide.imageUrl,
        title = guide.name,
        subtitle = guide.publisher.uppercase() + " · EVENT" +
            (InkShelf2GuidesData.publicationYear(guide)?.let { " · $it" } ?: ""),
        attribution = if (all.isEmpty()) "Loading reading order…" else
            all.size.toString() + " issues · " + completed + " of " + selected.size + " read",
        padding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 25.dp,
        ),
        onBack = onBack,
        topActions = {
            IconButton(onClick = { optionsOpen = true }) {
                Icon(Icons.Outlined.MoreVert, "Event options", tint = Color.White)
            }
            DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (hideRead) "Show completed issues" else "Hide completed issues") },
                    onClick = {
                        hideRead = !hideRead
                        InkShelf2GuidesData.setDisplayOption(context, guide.id, "hide_read", hideRead)
                        optionsOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (spoilerSafe) "Turn off spoiler protection" else "Turn on spoiler protection") },
                    onClick = {
                        spoilerSafe = !spoilerSafe
                        InkShelf2GuidesData.setDisplayOption(context, guide.id, "spoilers", spoilerSafe)
                        optionsOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Reset event progress") },
                    enabled = progressReady && !resetting && !currentlyOpening && all.isNotEmpty(),
                    onClick = { optionsOpen = false; confirmReset = true },
                )
            }
        },
    ) {
        item(key = "event-details") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (spoilerSafe) "Spoiler protection is on. Unread issue artwork and the event synopsis are hidden."
                    else description ?: "Explore this event in its intended reading order. " +
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkShelf2DetailQuickAction(
                        label = when {
                            currentlyOpening -> "Opening Issue"
                            resetting -> "Resetting Progress"
                            !progressReady || all.isEmpty() -> "Loading Progress"
                            nextIssue == null -> "Unavailable"
                            finished -> "Read Again"
                            hasProgress -> "Continue Reading"
                            else -> "Start Reading"
                        },
                        icon = Icons.Filled.PlayArrow,
                        highlight = true,
                        onClick = { if (nextIssue != null && !nextIssue.sourceUnavailable) onReadIssue(nextIssue) },
                        enabled = progressReady && nextIssue != null && !currentlyOpening && !resetting,
                        modifier = Modifier.weight(1f),
                    )
                    InkShelf2DetailQuickAction(
                        label = if (preparingDownloads) "Preparing…" else "Download Issues",
                        icon = Icons.Outlined.FileDownload,
                        onClick = { downloadChoices = true },
                        enabled = all.isNotEmpty() && !preparingDownloads,
                        modifier = Modifier.weight(1f),
                    )
                    InkShelf2DetailQuickAction(
                        label = if (saved) "In Library" else "Add to Library",
                        icon = if (saved) Icons.Outlined.CheckCircleOutline else Icons.Outlined.BookmarkAdd,
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (selected.any { offline[it.id]?.status == InkShelf2GuideDownloads.Status.OFFLINE }) {
                    Text(
                        "${selected.count { offline[it.id]?.status == InkShelf2GuideDownloads.Status.OFFLINE }} of ${selected.size} issues offline",
                        color = InkShelf2Colors.AccentLight, fontSize = 11.sp,
                    )
                }
                if (progressReady && nextIssue != null) {
                    Text(
                        (if (finished) "Read again: " else if (nextIssue.id in progress.partialIds) "Resume: " else "Next: ") +
                            nextIssue.displayName,
                        color = InkShelf2Colors.Text, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
                if (unavailableCount > 0) {
                    Text("$unavailableCount listed issue(s) unavailable. They are skipped, not marked as read.",
                        color = InkShelf2Colors.Muted, fontSize = 11.sp)
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
        if (relatedEvents.isNotEmpty()) {
            item(key = "related-events") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { relatedExpanded = !relatedExpanded },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(if (relatedExpanded) "Hide other ${guide.publisher} events" else "Explore other ${guide.publisher} events",
                            color = InkShelf2Colors.AccentLight, fontSize = 13.sp)
                    }
                    if (relatedExpanded) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(relatedEvents, key = { it.id }) { related ->
                                InkShelf2CoverCard(
                                    imageModel = if (spoilerSafe) null else related.imageUrl,
                                    title = related.name,
                                    onClick = { onRelatedEvent(related) },
                                    badgeText = if (related.id in newEventIds) "NEW" else InkShelf2GuidesData.publicationYear(related)?.toString(),
                                    width = 105.dp,
                                    libraryShape = true,
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "reading-order-title") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(if (essential) "Essential Reading" else "Complete Reading Order",
                    color = InkShelf2Colors.Text,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text((if (hideRead) "${visibleIssues.size} of ${selected.size} shown · completed hidden"
                    else selected.size.toString() + (if (hasMore) "+ listed issues" else " listed issues")) +
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
        } else if (visibleIssues.isEmpty()) {
            item {
                Text("All listed issues are completed. Show completed issues from the event menu.",
                    modifier = Modifier.padding(horizontal = 16.dp), color = InkShelf2Colors.Muted, fontSize = 13.sp)
            }
        } else {
            items(visibleIssues, key = { it.id }) { issue ->
                val checked = issue.id.toString() in readIds
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(13.dp))
                        .border(1.dp,
                            if (checked) InkShelf2Colors.AccentLight.copy(alpha = 0.45f)
                            else InkShelf2Colors.Border,
                            RoundedCornerShape(13.dp))
                        .clickable(enabled = !resetting && !currentlyOpening && !issue.sourceUnavailable) { onReadIssue(issue) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InkShelf2GuideIssueArtwork(
                        issue, progress.issues[issue.id]?.coverUrl ?: localArtwork[issue.id], checked,
                        batch = InkShelf2GuidesData.issueArtworkKey(issue)?.seriesKey in multiIssueSeries,
                        allowArtwork = !spoilerSafe || checked,
                        allowLookup = artworkReady && progressReady,
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
                            " · " + (if (issue.isEssential) "ESSENTIAL" else "TIE-IN") +
                            (if (checked) " · READ" else if (issue.id in progress.partialIds) " · IN PROGRESS" else ""),
                            color = InkShelf2Colors.Muted, fontSize = 10.sp)
                    }
                    val state = offline[issue.id]
                    if (issue.sourceUnavailable) {
                        Text("UNAVAILABLE", color = InkShelf2Colors.Muted, fontSize = 10.sp)
                    } else {
                        IconButton(
                            onClick = { onDownload(listOf(issue)) },
                            enabled = state?.canQueue == true && !preparingDownloads,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                when (state?.status) {
                                    InkShelf2GuideDownloads.Status.OFFLINE -> Icons.Outlined.CheckCircleOutline
                                    InkShelf2GuideDownloads.Status.QUEUED, InkShelf2GuideDownloads.Status.DOWNLOADING -> Icons.Outlined.HourglassEmpty
                                    else -> Icons.Outlined.FileDownload
                                },
                                contentDescription = when (state?.status) {
                                    InkShelf2GuideDownloads.Status.OFFLINE -> "Offline ready"
                                    InkShelf2GuideDownloads.Status.QUEUED -> "Queued"
                                    InkShelf2GuideDownloads.Status.DOWNLOADING -> "Downloading ${state?.percent ?: 0}%"
                                    InkShelf2GuideDownloads.Status.FAILED -> "Retry download"
                                    InkShelf2GuideDownloads.Status.AVAILABLE -> "Download issue"
                                    else -> "Open issue first to link it"
                                },
                                tint = if (state?.status == InkShelf2GuideDownloads.Status.OFFLINE || state?.canQueue == true)
                                    InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                            )
                        }
                    }
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
    allowArtwork: Boolean,
    allowLookup: Boolean,
) {
    val context = LocalContext.current
    val key = remember(issue) { InkShelf2GuidesData.issueArtworkKey(issue) }
    var cover by remember(issue, sourceCover) {
        mutableStateOf(sourceCover ?: key?.let { InkShelf2GuidesData.cachedIssueCover(context, it) })
    }
    var batchFinished by remember(issue, sourceCover, batch) { mutableStateOf(!batch) }
    DisposableEffect(issue, sourceCover, batch, allowArtwork, allowLookup) {
        var active = true
        if (allowArtwork && allowLookup && cover == null && batch && key != null) {
            batchFinished = false
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
    LaunchedEffect(issue, sourceCover, batchFinished, allowArtwork, allowLookup) {
        if (allowArtwork && allowLookup && batchFinished && cover == null && key != null) {
            cover = InkShelf2GuidesData.issueCover(context, issue)
        }
    }
    InkShelf2IssueArtworkFrame(
        imageUrl = if (allowArtwork) cover else null,
        title = issue.displayName,
        fallbackLabel = if (issue.number.equals("Full", true)) "ISSUE" else "#" + issue.number,
        read = read,
        modifier = Modifier.size(52.dp, 76.dp),
    )
}


@Composable
private fun InkShelf2EventTimelineRow(
    guide: InkShelf2Guide,
    progress: InkShelf2GuideProgress.EventState?,
    isNew: Boolean,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val order = remember(guide.batcaveSlug) {
        guide.batcaveSlug?.let { InkShelf2BundledEventOrders.get(it, 1)?.issues }.orEmpty()
    }
    val read = InkShelf2GuideProgress.effectiveRead(context, guide.id,
        progress ?: InkShelf2GuideProgress.EventState())
    val path = InkShelf2GuideReadingSelection.issuesForMode(order, InkShelf2GuidesData.essentialMode(context, guide.id))
    val completed = path.count { it.id.toString() in read }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(InkShelf2GuidesData.publicationYear(guide)?.toString() ?: "—",
                color = InkShelf2Colors.AccentLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(2.dp, 65.dp).background(InkShelf2Colors.AccentLight.copy(alpha = 0.35f)))
        }
        Row(
            modifier = Modifier.weight(1f)
                .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(12.dp))
                .border(1.dp, InkShelf2Colors.Border, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InkShelf2IssueArtworkFrame(
                imageUrl = guide.imageUrl, title = guide.name,
                fallbackLabel = "EVENT", read = false, modifier = Modifier.size(58.dp, 83.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(guide.name, color = InkShelf2Colors.Text, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(guide.publisher + " · " + order.size + " issues" + if (isNew) " · NEW" else "",
                    color = InkShelf2Colors.Muted, fontSize = 11.sp)
                if (completed > 0 && path.isNotEmpty()) {
                    Text("$completed of ${path.size} read", color = InkShelf2Colors.AccentLight, fontSize = 11.sp)
                }
            }
            Icon(Icons.Outlined.ChevronRight, "Open event", tint = InkShelf2Colors.Muted)
        }
    }
}
