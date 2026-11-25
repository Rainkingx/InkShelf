package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * New Discover destination built from the approved mock-up.
 * Source management stays under its menu. Search queries installed J2K
 * extensions directly and never treats cached recommendations as search.
 */
@Composable
internal fun InkShelf2DiscoverScreen(
    searchSession: Int = 0,
    discover: InkShelf2Engine.Discover?,
    curatedShelves: List<InkShelf2DiscoverCuration.Shelf>,
    padding: PaddingValues,
    onOpen: (Manga) -> Unit,
    sources: List<CatalogueSource>,
    onOpenSearchResult: (Manga) -> Unit,
    onManageSources: () -> Unit,
    onSeeAll: (String) -> Unit,
    onRecommendationsUpdated: suspend () -> Unit,
    searchFocusToken: Int = 0,
    initialSearchQuery: String = "",
    guideIssueTarget: InkShelf2GuideIssue? = null,
    onClearGuideTarget: () -> Unit = {},
    onAutoOpenGuideMatch: (Manga, InkShelf2GuideIssue) -> Unit,
) {
    val engine = remember { InkShelf2SourcesEngine() }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var activeQuery by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<InkShelf2SourcesEngine.GlobalSearchResults?>(null) }
    var refreshingPicks by remember { mutableStateOf(false) }
    var picksMessage by remember { mutableStateOf<String?>(null) }
    var autoAttempted by remember { mutableStateOf(false) }
    var activeSpotlight by remember { mutableStateOf<InkShelf2Spotlight.Event?>(null) }
    var spotlightEvents by remember { mutableStateOf(InkShelf2Spotlight.starter()) }
    var showOtherGuideEditions by remember(guideIssueTarget?.id) { mutableStateOf(false) }
    var attemptedAutoGuideMatch by remember(guideIssueTarget?.id) { mutableStateOf<String?>(null) }
    var foundQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        spotlightEvents = InkShelf2Spotlight.load(context)
    }

    BackHandler(enabled = query.isNotBlank() && activeSpotlight == null) {
        query = ""
        activeQuery = ""
        found = null
        error = null
        keyboard?.hide()
    }

    val sourceIds = sources.map { it.id }
    LaunchedEffect(searchSession, searchFocusToken, guideIssueTarget?.id) {
        // Both manual searches and guide searches start clean on a new
        // Discover visit. Never restore a prior search when coming back.
        query = if (searchFocusToken > 0) initialSearchQuery else ""
        activeQuery = ""
        found = null
        foundQuery = ""
        error = null
        searching = false
        showOtherGuideEditions = false
        attemptedAutoGuideMatch = null
        activeSpotlight = null
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        if (searchFocusToken > 0 && guideIssueTarget == null) {
            // Only an explicit header Search requests the keyboard.
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(found, guideIssueTarget?.id) {
        if (guideIssueTarget != null && found != null) {
            // Bring the matching-edition results into view once the
            // background extension search finishes.
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            scroll.animateScrollToItem(1)
        }
    }
    // Debounced live search; the keyboard's Search action also submits instantly.
    LaunchedEffect(query) {
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            activeQuery = ""
            found = null
            foundQuery = ""
            error = null
        } else {
            delay(550L)
            activeQuery = cleaned
        }
    }
    LaunchedEffect(activeQuery, sourceIds) {
        if (activeQuery.isBlank()) {
            found = null
            foundQuery = ""
            searching = false
            return@LaunchedEffect
        }
        searching = true
        found = null
        foundQuery = ""
        error = null
        try {
            val completedQuery = activeQuery
            val results = engine.searchAcross(sources, completedQuery)
            found = results
            foundQuery = completedQuery
        } catch (failure: Exception) {
            // Cancellation is managed by LaunchedEffect when a new query arrives.
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error = failure.message ?: "Source search could not be completed."
        } finally {
            searching = false
        }
    }

    // A single known-year source result can navigate without another tap.
    // Unknown years and multiple matches always retain the edition picker.
    LaunchedEffect(found, foundQuery, guideIssueTarget?.id, query) {
        val issue = guideIssueTarget ?: return@LaunchedEffect
        val results = found ?: return@LaunchedEffect
        if (foundQuery.isBlank() ||
            !foundQuery.equals(InkShelf2GuideEdition.sourceSearchQuery(issue), ignoreCase = true) ||
            !query.trim().equals(foundQuery, ignoreCase = true)
        ) return@LaunchedEffect
        val verified = results.results.filter {
            InkShelf2GuideEdition.classify(issue, it.manga.title) ==
                InkShelf2GuideEdition.Match.MATCHING
        }
        if (verified.size != 1) return@LaunchedEffect
        val candidate = verified.single().manga
        val key = issue.id.toString() + ":" + candidate.source + ":" + candidate.url
        if (attemptedAutoGuideMatch == key) return@LaunchedEffect
        attemptedAutoGuideMatch = key
        onAutoOpenGuideMatch(candidate, issue)
    }

    // Restrict every Discover card to enabled, installed source IDs.
    val enabledSourceIds = sources.map { it.id }.toSet()
    val trending = discover?.trending.orEmpty()
        .filter { it.source in enabledSourceIds && !it.thumbnail_url.isNullOrBlank() }
        .distinctBy { "${it.source}:${it.url}" }
    // The featured carousel has its own current, date-expiring publisher lineup.
    // Popular extension results remain below; they never define the hero.
    val heroSpotlight = spotlightEvents.take(4)
    val newest = discover?.newest.orEmpty()
        .filter { it.source in enabledSourceIds && !it.thumbnail_url.isNullOrBlank() }
        .distinctBy { "${it.source}:${it.url}" }
    val personalPicks = discover?.recommendationDetails.orEmpty()
        .filter { it.manga.source in enabledSourceIds && !it.manga.thumbnail_url.isNullOrBlank() }
        .distinctBy { "${it.manga.source}:${it.manga.url}" }
    val acclaimed = curatedShelves.firstOrNull { it.title == "Critically Acclaimed" }
    val hiddenGems = curatedShelves.firstOrNull { it.title == "Hidden Gems" }
    val curated = curatedShelves.filterNot {
        it.title in setOf("Critically Acclaimed", "Hidden Gems", "Browse All")
    }
    val heroPager = rememberPagerState(pageCount = { heroSpotlight.size.coerceAtLeast(1) })
    val highQualityHeroArt = remember { mutableStateMapOf<String, String>() }
    val heroKeys = heroSpotlight.map { it.id }
    LaunchedEffect(heroKeys) {
        heroSpotlight.forEach { feature ->
            val cover = InkShelf2HeroArtwork.findCover(context,
                InkShelf2Spotlight.metronTitle(feature.leadSeries))
            if (cover != null) highQualityHeroArt[feature.id] = cover
        }
    }
    // Swipe remains manual, but the editorial feature also advances gently.
    LaunchedEffect(heroSpotlight.size) {
        while (heroSpotlight.size > 1) {
            delay(8_000L)
            heroPager.animateScrollToPage((heroPager.currentPage + 1) % heroSpotlight.size)
        }
    }

    suspend fun loadPersonalPicks(force: Boolean) {
        if (refreshingPicks) return
        refreshingPicks = true
        picksMessage = null
        try {
            val success = BrowseController.refreshPickedForYouForInkShelf2(
                context = context,
                force = force,
                // Include the currently rendered cards, not just cached picks.
                // Source-recovery fallbacks should not be repeated immediately.
                visibleTitles = if (force) personalPicks.map { it.manga.title }.toSet()
                    else emptySet(),
            )
            if (success) {
                onRecommendationsUpdated()
            } else {
                picksMessage = if (force) {
                    "No different picks available from your enabled sources right now. Your current recommendations remain."
                } else {
                    "Personal picks are unavailable right now. Check your extensions or try Refresh Picks."
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            picksMessage = "Could not update your picks. Your saved recommendations are unchanged."
        } finally {
            refreshingPicks = false
        }
    }

    // No network work at launch. Only generate on Discover entry when the
    // persisted shelf is empty or older than a week, and only with real sources.
    val readyForPicks = discover != null && enabledSourceIds.isNotEmpty()
    LaunchedEffect(readyForPicks) {
        if (!readyForPicks || autoAttempted) return@LaunchedEffect
        autoAttempted = true
        val lastUpdated = InkShelfDiscoverCache.lastUpdatedAt(
            context, InkShelfDiscoverCache.Section.PICKED_FOR_YOU,
        )
        val stale = lastUpdated <= 0L ||
            System.currentTimeMillis() - lastUpdated > TimeUnit.DAYS.toMillis(7)
        if (stale || discover?.recommendationDetails.isNullOrEmpty()) {
            loadPersonalPicks(force = stale && lastUpdated > 0L)
        }
    }

    activeSpotlight?.let { event ->
        InkShelf2SpotlightEventScreen(
            event = event,
            sources = sources,
            padding = padding,
            onBack = { activeSpotlight = null },
            onOpen = onOpenSearchResult,
        )
        return
    }

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 14.dp,
        ),
        // A little more vertical rhythm between editorial sections makes
        // Discover easier to scan without shrinking art or removing shelves.
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "discover-header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkShelf2PageHeading(
                        if (guideIssueTarget == null) "Discover Comics" else "Find Guide Issue",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onManageSources) {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = "Browse comic sources in Menu",
                            tint = InkShelf2Colors.Text,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkShelf2SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholderText = "Search all comic sources",
                        onSearch = {
                            activeQuery = query.trim().takeIf { it.length >= 2 }.orEmpty()
                            keyboard?.hide()
                        },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = ""; activeQuery = ""; found = null }) {
                            Icon(Icons.Outlined.Close, "Clear search", tint = InkShelf2Colors.Text)
                        }
                    }
                }
            }
        }

        if (query.isNotBlank()) {
            item(key = "search-summary") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (guideIssueTarget != null) {
                        Text(
                            "FIND THIS GUIDE ISSUE",
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            guideIssueTarget.displayName,
                            color = InkShelf2Colors.Text,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (guideIssueTarget.yearBegan != null)
                                "Matching publication year first. Unlabelled editions need checking."
                            else "Metron has no year for this issue. Check the edition before reading.",
                            color = InkShelf2Colors.Muted,
                            fontSize = 12.sp,
                        )
                        TextButton(onClick = onClearGuideTarget) {
                            Text("Exit guide search", color = InkShelf2Colors.AccentLight)
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                    Text("Search Installed Sources", color = InkShelf2Colors.Text,
                        fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    when {
                        sources.isEmpty() -> Text(
                            "No enabled sources. Go to Menu → Manage Extensions to enable or install sources.",
                            color = InkShelf2Colors.Muted, fontSize = 13.sp,
                        )
                        query.trim().length < 2 -> Text(
                            "Enter at least 2 characters to search your extensions.",
                            color = InkShelf2Colors.Muted, fontSize = 13.sp,
                        )
                        searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                            InkShelf2LoadingIndicator(
                                modifier = Modifier.size(17.dp),
                                color = InkShelf2Colors.AccentLight,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("Searching ${sources.size} comic sources…",
                                color = InkShelf2Colors.Muted, fontSize = 12.sp)
                        }
                        error != null -> Text(error.orEmpty(), color = InkShelf2Colors.Muted, fontSize = 12.sp)
                        found != null -> {
                            val result = found!!
                            Text(
                                "${result.results.size} comics from ${result.searchedSources - result.failedSources}" +
                                    " of ${result.searchedSources} sources",
                                color = InkShelf2Colors.Muted,
                                fontSize = 12.sp,
                            )
                            if (result.failedSources > 0) {
                                Text(
                                    "${result.failedSources} source(s) did not respond; other results are shown.",
                                    color = InkShelf2Colors.AccentLight,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
            val comics = found?.results.orEmpty()
            val matchedEditions = if (guideIssueTarget == null) emptyList()
                else comics.filter {
                    InkShelf2GuideEdition.classify(guideIssueTarget, it.manga.title) ==
                        InkShelf2GuideEdition.Match.MATCHING
                }
            val unverifiedEditions = if (guideIssueTarget == null) emptyList()
                else comics.filter {
                    InkShelf2GuideEdition.classify(guideIssueTarget, it.manga.title) ==
                        InkShelf2GuideEdition.Match.UNVERIFIED
                }
            val otherEditions = if (guideIssueTarget == null) emptyList()
                else comics.filter {
                    InkShelf2GuideEdition.classify(guideIssueTarget, it.manga.title) in setOf(
                        InkShelf2GuideEdition.Match.OTHER_RUN,
                        InkShelf2GuideEdition.Match.OTHER_TITLE,
                    )
                }
            val visibleComics = if (guideIssueTarget == null) comics else
                matchedEditions + unverifiedEditions +
                    (if (showOtherGuideEditions) otherEditions else emptyList())
            if (guideIssueTarget != null && comics.isNotEmpty()) {
                item(key = "guide-edition-totals") {
                    Text(
                        matchedEditions.size.toString() + " matching • " +
                            unverifiedEditions.size + " unverified • " +
                            otherEditions.size + " other editions",
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 6.dp),
                    )
                }
            }
            if (!searching && error == null && comics.isEmpty() && query.length >= 2 && found != null) {
                item(key = "search-empty") {
                    InkShelf2EmptyShelf("No matches from your installed sources. Try a different title.")
                }
            }
            if (guideIssueTarget != null &&
                matchedEditions.isEmpty() && unverifiedEditions.isEmpty() &&
                otherEditions.isNotEmpty() && !showOtherGuideEditions
            ) {
                item(key = "guide-no-safe-match") {
                    Text(
                        "No matching or verifiable editions were found. " +
                            "Other years are hidden to avoid opening the wrong run.",
                        color = InkShelf2Colors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp),
                    )
                }
            }
            items(
                items = visibleComics.chunked(3),
                key = { row -> row.first().manga.source.toString() + ":" + row.first().manga.url },
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    row.forEach { result ->
                        Column(
                            modifier = Modifier.weight(1f)
                                .clickable { onOpenSearchResult(result.manga) },
                        ) {
                            AsyncImage(
                                model = result.manga,
                                contentDescription = "Open ${result.manga.title}",
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.71f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(
                                        1.dp,
                                        InkShelf2Colors.Border.copy(alpha = 0.7f),
                                        RoundedCornerShape(9.dp),
                                    ),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                result.manga.title,
                                color = InkShelf2Colors.Text,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                result.sourceName,
                                color = InkShelf2Colors.AccentLight,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (guideIssueTarget != null) {
                                val match = InkShelf2GuideEdition.classify(
                                    guideIssueTarget, result.manga.title,
                                )
                                val badge = when (match) {
                                    InkShelf2GuideEdition.Match.MATCHING -> "MATCHING RUN"
                                    InkShelf2GuideEdition.Match.UNVERIFIED -> "CHECK YEAR"
                                    InkShelf2GuideEdition.Match.OTHER_RUN -> "DIFFERENT YEAR"
                                    InkShelf2GuideEdition.Match.OTHER_TITLE -> "OTHER TITLE"
                                }
                                Text(
                                    badge + (InkShelf2GuideEdition.year(result.manga.title)
                                        ?.let { " · $it" } ?: ""),
                                    color = if (match == InkShelf2GuideEdition.Match.MATCHING)
                                        InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "VIEW SERIES ISSUES",
                                    color = InkShelf2Colors.AccentLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onOpen(result.manga) },
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (guideIssueTarget != null && otherEditions.isNotEmpty()) {
                item(key = "guide-other-editions-toggle") {
                    TextButton(
                        onClick = { showOtherGuideEditions = !showOtherGuideEditions },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            if (showOtherGuideEditions) "Hide other editions"
                            else "Show " + otherEditions.size + " other editions",
                            color = InkShelf2Colors.AccentLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        } else {
            // Fixed item indices keep mock-up category shortcuts functional.
            item(key = "trending-hero") {
                if (heroSpotlight.isNotEmpty()) {
                    HorizontalPager(
                        state = heroPager,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 10.dp,
                    ) { index ->
                        val feature = heroSpotlight[index]
                        val metronArt = highQualityHeroArt[feature.id]
                        var officialFailed by remember(feature.id, feature.artworkUrl) {
                            mutableStateOf(false)
                        }
                        var fallbackFailed by remember(feature.id, metronArt) {
                            mutableStateOf(false)
                        }
                        val heroImage = if (!officialFailed && feature.artworkUrl != null)
                            feature.artworkUrl else metronArt
                        Box(
                            modifier = Modifier.fillMaxWidth().height(194.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(InkShelf2Colors.SurfaceRaised)
                                .clickable {
                                    activeSpotlight = feature
                                    focusManager.clearFocus(force = true)
                                    keyboard?.hide()
                                },
                        ) {
                            // Show official publisher event art without cropping its characters
                            // or wordmarks. Metron remains a safe image fallback.
                            if (heroImage != null && !(officialFailed && fallbackFailed)) {
                                AsyncImage(
                                    model = heroImage,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .fillMaxWidth(if (feature.id == "dc-absolute") 0.87f else 0.61f),
                                    alignment = Alignment.CenterEnd,
                                    contentScale = ContentScale.Fit,
                                    onError = {
                                        if (!officialFailed && feature.artworkUrl != null)
                                            officialFailed = true
                                        else fallbackFailed = true
                                    },
                                )
                            }
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            // Keep the left-hand title readable while allowing
                                            // much more of the comic artwork to show through.
                                            InkShelf2Colors.Background.copy(alpha = 0.95f),
                                            InkShelf2Colors.Background.copy(alpha = 0.84f),
                                            InkShelf2Colors.Background.copy(alpha = 0.12f),
                                        ),
                                    ),
                                ),
                            )
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart)
                                    .fillMaxWidth(0.78f)
                                    .padding(16.dp),
                            ) {
                                Text(feature.publisher.uppercase() + " · " + feature.label,
                                    color = InkShelf2Colors.AccentLight,
                                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    feature.title,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(if (feature.id == "doom-essential")
                                    feature.series.size.toString() + " ESSENTIAL READS · START HERE"
                                    else feature.series.size.toString() + " SERIES · EXPLORE THE UNIVERSE",
                                    color = InkShelf2Colors.AccentLight, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(heroSpotlight.size) { index ->
                            Box(
                                modifier = Modifier.padding(horizontal = 3.dp)
                                    .width(if (index == heroPager.currentPage) 16.dp else 6.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (index == heroPager.currentPage) InkShelf2Colors.AccentLight
                                        else InkShelf2Colors.Border,
                                    ),
                            )
                        }
                    }
                } else {
                    InkShelf2EmptyShelf("No current editorial features. The Spotlight will return with the next verified launches.")
                }
            }
            item(key = "shortcuts") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val shortcuts = listOf(
                        Triple("Trending", Icons.Outlined.LocalFireDepartment, 6 + (if (acclaimed != null) 1 else 0) + (if (hiddenGems != null) 1 else 0)),
                        Triple("New This Week", Icons.Outlined.NewReleases, 5 + (if (acclaimed != null) 1 else 0) + (if (hiddenGems != null) 1 else 0)),
                        Triple("Top Picks", Icons.Outlined.AutoAwesome, 4 + (if (acclaimed != null) 1 else 0) + (if (hiddenGems != null) 1 else 0)),
                        Triple("Collections", Icons.Outlined.AutoStories, 3),
                    )
                    shortcuts.forEach { (label, image, index) ->
                        Column(
                            modifier = Modifier.weight(1f).clickable {
                                scope.launch { scroll.animateScrollToItem(index) }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.size(45.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(InkShelf2Colors.SurfaceRaised)
                                    .border(
                                        1.dp,
                                        InkShelf2Colors.Border.copy(alpha = 0.75f),
                                        RoundedCornerShape(13.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(image, contentDescription = null,
                                    modifier = Modifier.size(23.dp), tint = InkShelf2Colors.AccentLight)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(label, color = InkShelf2Colors.Text, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item(key = "collections") {
                InkShelf2SectionHeading("Curated Collections", onClick = { onSeeAll("Browse All") })
                Spacer(Modifier.height(8.dp))
                if (curated.isEmpty()) {
                    InkShelf2EmptyShelf("Collections will appear when suitable comics are available.")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(curated, key = { it.title }) { collection ->
                            Box(
                                modifier = Modifier.width(160.dp).height(112.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .clickable { onSeeAll(collection.title) },
                            ) {
                                AsyncImage(
                                    model = collection.comics.first(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(InkShelf2Colors.Background.copy(alpha = 0.2f), InkShelf2Colors.Background.copy(alpha = 0.94f)),
                                        ),
                                    ),
                                )
                                Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                    Text(collection.title, color = Color.White, fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text("${collection.comics.size} comics", color = InkShelf2Colors.AccentLight,
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (acclaimed != null) {
                item(key = "critically-acclaimed") {
                    DiscoverSeeAllHeading("Critically Acclaimed", onClick = { onSeeAll("Critically Acclaimed") })
                    Text(
                        acclaimed.description,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                        color = InkShelf2Colors.Muted,
                        fontSize = 11.sp,
                    )
                    DiscoverShelf(acclaimed.comics.take(12), onOpen)
                }
            }
            if (hiddenGems != null) {
                item(key = "hidden-gems") {
                    DiscoverSeeAllHeading("Hidden Gems", onClick = { onSeeAll("Hidden Gems") })
                    Text(
                        hiddenGems.description,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                        color = InkShelf2Colors.Muted,
                        fontSize = 11.sp,
                    )
                    DiscoverShelf(hiddenGems.comics.take(12), onOpen)
                }
            }
            item(key = "recommended") {
                DiscoverSeeAllHeading("Recommended for You", onClick = { onSeeAll("Recommended for You") })
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Based on your reading",
                        modifier = Modifier.weight(1f),
                        color = InkShelf2Colors.Muted,
                        fontSize = 11.sp,
                    )
                    TextButton(
                        enabled = !refreshingPicks && enabledSourceIds.isNotEmpty(),
                        onClick = { scope.launch { loadPersonalPicks(force = true) } },
                    ) {
                        Icon(Icons.Outlined.Refresh, null,
                            modifier = Modifier.size(16.dp),
                            tint = InkShelf2Colors.AccentLight)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (refreshingPicks) "Finding picks…" else "Refresh Picks",
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 11.sp,
                        )
                    }
                }
                picksMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = InkShelf2Colors.Muted,
                        fontSize = 12.sp,
                    )
                }
                if (personalPicks.isEmpty()) {
                    InkShelf2EmptyShelf(
                        if (refreshingPicks) "Finding comics based on your reading…"
                        else if (enabledSourceIds.isEmpty()) "Enable a comic extension to get personal recommendations."
                        else "No personal picks yet. Tap Refresh Picks to find some.",
                    )
                } else {
                    DiscoverPersonalShelf(personalPicks.take(9), onOpen)
                }
            }
            item(key = "new") {
                DiscoverSeeAllHeading("New This Week", onClick = { onSeeAll("New This Week") })
                Spacer(Modifier.height(7.dp))
                DiscoverShelf(newest.take(20), onOpen)
            }
            item(key = "trending-shelf") {
                DiscoverSeeAllHeading("Popular in Your Sources", onClick = { onSeeAll("Trending Right Now") })
                Spacer(Modifier.height(7.dp))
                DiscoverShelf(trending.take(20), onOpen)
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

/** Visible See all action for Discover shelves, without changing Home's approved arrows. */
@Composable
private fun DiscoverSeeAllHeading(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = InkShelf2Colors.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("See all", color = InkShelf2Colors.AccentLight, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(3.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "See all " + title,
                tint = InkShelf2Colors.AccentLight,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DiscoverShelf(comics: List<Manga>, onOpen: (Manga) -> Unit) {
    if (comics.isEmpty()) {
        InkShelf2EmptyShelf("No comics available in this shelf yet.")
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(comics, key = { "${it.source}:${it.url}" }) { comic ->
            InkShelf2CoverCard(
                imageModel = comic,
                title = comic.title,
                onClick = { onOpen(comic) },
                width = 105.dp,
                libraryShape = true,
            )
        }
    }
}


/** Personal picks preserve the existing horizontal shelf, with transparent reasons. */
@Composable
private fun DiscoverPersonalShelf(
    picks: List<InkShelf2Engine.PersonalPick>,
    onOpen: (Manga) -> Unit,
) {
    var explanation by remember { mutableStateOf<InkShelf2Engine.PersonalPick?>(null) }
    explanation?.let { pick ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { explanation = null },
            title = { Text("Why ${pick.manga.title}?", color = InkShelf2Colors.Text) },
            text = {
                Text(
                    pick.reason.substringAfter(" • ", pick.reason)
                        .ifBlank { "Suggested from your library and recent reading." },
                    color = InkShelf2Colors.Text,
                )
            },
            confirmButton = {
                TextButton(onClick = { explanation = null }) {
                    Text("Close", color = InkShelf2Colors.AccentLight)
                }
            },
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(picks, key = { "${it.manga.source}:${it.manga.url}" }) { pick ->
            Column(modifier = Modifier.width(118.dp)) {
                AsyncImage(
                    model = pick.manga,
                    contentDescription = "Open ${pick.manga.title}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.71f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onOpen(pick.manga) },
                    contentScale = ContentScale.Crop,
                )
                Text(
                    pick.manga.title,
                    modifier = Modifier.padding(top = 5.dp).clickable { onOpen(pick.manga) },
                    color = InkShelf2Colors.Text,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val kind = pick.reason.substringBefore(" • ").trim()
                    .ifBlank { "Personal pick" }
                Text(
                    kind,
                    modifier = Modifier.padding(top = 3.dp),
                    color = InkShelf2Colors.AccentLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Why this?",
                    modifier = Modifier.clip(RoundedCornerShape(5.dp))
                        .clickable { explanation = pick }
                        .padding(vertical = 5.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
