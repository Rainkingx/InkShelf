package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Entirely NEW Compose destinations. No Conductor View hierarchy, XML toolbar,
 * Activity app bar or legacy nav views are mounted anywhere in these screens.
 */
@Composable
internal fun InkShelf2HomeScreen(
    home: InkShelf2Engine.Home?,
    library: List<InkShelf2LibraryItem>,
    profileName: String,
    padding: PaddingValues,
    pulseExpanded: Boolean,
    onPulseExpandedChange: (Boolean) -> Unit,
    onPulseArticlesUpdated: (List<InkShelf2PulseArticle>) -> Unit,
    onOpen: (Manga) -> Unit,
    onSave: (Manga) -> Unit,
    onDiscover: () -> Unit,
    onLibrary: () -> Unit,
    onDayPicks: (InkShelf2Engine.Day) -> Unit,
    onTrendingSeeAll: () -> Unit,
) {
    val slides = home?.days.orEmpty()
    val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val initial = slides.indexOfFirst { it.calendarDay == currentDay }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial, pageCount = { slides.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    // A single restrained entrance sequence per Home visit. The animation is
    // tied to the Home composition, never to pager.currentPage or a swipe.
    val heroEntrance = remember { Animatable(0f) }
    val heroShimmer = remember { Animatable(-0.8f) }
    val heroDots = remember { Animatable(0f) }
    LaunchedEffect(slides.isNotEmpty()) {
        if (slides.isNotEmpty()) {
            launch {
                heroEntrance.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
            }
            launch {
                delay(320)
                heroDots.animateTo(1f, tween(420))
            }
            delay(440)
            heroShimmer.animateTo(1.8f, tween(1050, easing = LinearEasing))
        }
    }
    val unreadIssues = library.asSequence()
        .filter { it.state == InkShelf2ReadingState.READING }
        .sumOf { (it.totalChapters - it.readChapters).coerceAtLeast(0) }
    // Pulse replaces the Home recommendation shelf; Discover still owns the
    // complete personalised recommendation feed and remains unchanged.
    val context = LocalContext.current
    var pulseArticles by remember { mutableStateOf<List<InkShelf2PulseArticle>>(emptyList()) }
    var pulseLoading by remember { mutableStateOf(true) }
    var pulseShowVideos by rememberSaveable { mutableStateOf(false) }
    var pulseTopicFilter by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = pulseExpanded) { onPulseExpandedChange(false) }
    LaunchedEffect(Unit) {
        // Pulse is optional: stale or unavailable feeds must never crash Home
        // or leave the news section stuck with a loading indicator.
        try {
            pulseArticles = InkShelf2PulseFeed.cached(context)
            onPulseArticlesUpdated(pulseArticles)
            pulseArticles = InkShelf2PulseFeed.refresh(context)
            onPulseArticlesUpdated(pulseArticles)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Preserve whatever cached articles were already displayed.
        } finally {
            pulseLoading = false
        }
    }
    if (pulseExpanded) {
        InkShelf2PulseScreen(
            padding = padding,
            articles = pulseArticles,
            loading = pulseLoading,
            showVideos = pulseShowVideos,
            onShowVideos = { pulseShowVideos = it },
            topicFilter = InkShelf2PulseTopic.entries.firstOrNull { it.name == pulseTopicFilter },
            onTopicFilterChange = { pulseTopicFilter = it?.name.orEmpty() },
            onBack = { onPulseExpandedChange(false) },
            onOpenArticle = { url -> InkShelf2PulseFeed.openArticle(context, url) },
        )
        return
    }
    // The data arrives after the first composition; restore today's actual page.
    LaunchedEffect(slides.size, initial) {
        if (slides.isNotEmpty()) pager.scrollToPage(initial)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 22.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "home-greeting") {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
            val name = profileName.takeIf { it.isNotBlank() }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            ) {
                // Match the same heading treatment used on Library and Discover.
                InkShelf2PageHeading("Home")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (InkShelf2Themes.eventSkin != null) "Doom welcomes you" else if (name == null) greeting else "$greeting, $name",
                    color = InkShelf2Colors.AccentLight,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            if (InkShelf2Themes.eventSkin != null) {
                val shape = RoundedCornerShape(10.dp)
                val target = Calendar.getInstance().apply {
                    clear()
                    set(2026, Calendar.DECEMBER, 18)
                }.timeInMillis
                val days = kotlin.math.ceil((target - System.currentTimeMillis()) / 86_400_000.0).toInt()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(shape).background(Brush.horizontalGradient(listOf(
                        InkShelf2Colors.SurfaceRaised, InkShelf2Colors.Background)))
                    .border(1.dp, InkShelf2Colors.Trim, shape).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("REIGN OF LATVERIA", color = InkShelf2Colors.Trim,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (days > 0) "$days days until Doomsday" else "Doom December · Limited Event",
                            color = InkShelf2Colors.Muted, fontSize = 12.sp)
                    }
                    Text("◆", color = InkShelf2Colors.AccentLight, fontSize = 22.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (slides.isNotEmpty()) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer {
                            alpha = heroEntrance.value
                            translationY = (1f - heroEntrance.value) * 18.dp.toPx()
                        },
                    pageSpacing = 10.dp,
                ) { index ->
                    val day = slides[index]
                    val pick = day.picks.firstOrNull()
                    val heroShape = RoundedCornerShape(if (InkShelf2Themes.eventSkin != null) 10.dp else 20.dp)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(316.dp)
                            .padding(horizontal = 16.dp)
                            // Soft, stationary purple halo; the single shimmer
                            // below supplies the motion rather than a loop.
                            .shadow(
                                elevation = 11.dp,
                                shape = heroShape,
                                ambientColor = InkShelf2Colors.Accent.copy(alpha = 0.32f),
                                spotColor = InkShelf2Colors.AccentLight.copy(alpha = 0.38f),
                            )
                            .clip(heroShape)
                            .background(InkShelf2Colors.Surface)
                            .border(
                                1.dp,
                                if (InkShelf2Themes.eventSkin != null) InkShelf2Colors.Trim.copy(alpha = 0.85f)
                                else InkShelf2Colors.AccentLight.copy(alpha = 0.30f),
                                heroShape,
                            )
                            // Tapping the hero opens all daily picks; nested
                            // button clicks retain their own actions.
                            .clickable { onDayPicks(day) },
                    ) {
                        AsyncImage(
                            model = day.artwork.imageUrl,
                            contentDescription = day.artwork.credit,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            // A gentle lift to contrast, saturation and brightness
                            // keeps the genuine publisher artwork punchy without
                            // modifying the image file or overwhelming white text.
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix(
                                    floatArrayOf(
                                        1.075f, -0.020f, -0.007f, 0f, 8f,
                                        -0.014f, 1.080f, -0.010f, 0f, 8f,
                                        -0.012f, -0.020f, 1.110f, 0f, 8f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            ),
                        )
                        // Reserve contrast for the lower text/buttons; allow
                        // bright authentic cover artwork to remain vivid above.
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Black.copy(alpha = 0.04f),
                                        0.35f to Color.Black.copy(alpha = 0.03f),
                                        0.65f to InkShelf2Colors.Background.copy(alpha = 0.56f),
                                        1f to InkShelf2Colors.Background.copy(alpha = 0.94f),
                                    ),
                                ),
                            ),
                        )
                        // A gentle angled light glides across the artwork once
                        // when Home appears; never on hero swipes or in a loop.
                        if (index == pager.currentPage &&
                            heroShimmer.value > -0.5f && heroShimmer.value < 1.6f
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sweepX = size.width * heroShimmer.value
                                drawRect(
                                    brush = Brush.linearGradient(
                                        0f to Color.Transparent,
                                        0.42f to Color.Transparent,
                                        0.50f to Color.White.copy(alpha = 0.20f),
                                        0.58f to InkShelf2Colors.AccentLight.copy(alpha = 0.10f),
                                        1f to Color.Transparent,
                                        start = Offset(sweepX - size.width * 0.42f, 0f),
                                        end = Offset(sweepX + size.width * 0.42f, size.height),
                                    ),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 19.dp),
                        ) {
                            Text(
                                text = if (InkShelf2Themes.eventSkin != null) "DOOM’S DAILY DECREE" else "TODAY'S FEATURE",
                                color = InkShelf2Colors.Trim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(9.dp))
                            // Keep the distinctive day-of-week theme, but do
                            // not print a randomly chosen comic series title.
                            InkShelf2ComicTitle(day.title)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (day.picks.isEmpty()) "Explore today's selection"
                                else "${day.picks.size} ${if (day.picks.size == 1) "pick" else "picks"} selected for you",
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                InkShelf2ActionButton(
                                    // The hero promotes the complete daily selection.
                                    // Open all three verified picks first; each
                                    // then opens the normal InkShelf 2 details page.
                                    label = "View All Picks",
                                    leadingIcon = Icons.Outlined.AutoStories,
                                    enabled = true,
                                    onClick = { onDayPicks(day) },
                                    modifier = Modifier.weight(1f),
                                )
                                InkShelf2ActionButton(
                                    // Saves only the featured first comic, not
                                    // all three daily selections.
                                    label = if (pick?.favorite == true) "First Saved" else "Save First",
                                    leadingIcon = if (pick?.favorite == true) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Add,
                                    enabled = pick != null && pick.favorite != true,
                                    onClick = {
                                        pick?.takeIf { !it.favorite }?.let(onSave)
                                    },
                                    outlined = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp, bottom = 5.dp)
                        .graphicsLayer { alpha = heroDots.value },
                    horizontalArrangement = Arrangement.Center,
                ) {
                    slides.indices.forEach { index ->
                        val active = pager.currentPage == index
                        Box(
                            modifier = Modifier.padding(horizontal = 3.dp)
                                .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) InkShelf2Colors.AccentLight else InkShelf2Colors.Border)
                                .clickable { scope.launch { pager.animateScrollToPage(index) } },
                        )
                    }
                }
            } else {
                Text(
                    "Loading today's comics…",
                    color = InkShelf2Colors.Muted,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        item(key = "inkshelf-pulse") {
            InkShelf2PulsePreview(
                articles = pulseArticles,
                loading = pulseLoading,
                onSeeAll = {
                    // Open the complete Pulse on its default Articles tab.
                    pulseShowVideos = false
                    onPulseExpandedChange(true)
                },
                onOpenArticle = { url -> InkShelf2PulseFeed.openArticle(context, url) },
            )
        }
        item(key = "unread-library") {
            InkShelf2SectionHeading("Issues Waiting for You", onClick = onLibrary, showSeeAll = true)
            if (unreadIssues > 0) {
                Text(
                    "$unreadIssues unread issues across comics you're reading",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            // These are ACTUAL unread chapters from the original J2K
            // Library database. Do not label them newly published.
            val waiting = library
                .filter {
                    it.state == InkShelf2ReadingState.READING &&
                        it.totalChapters > it.readChapters
                }
                .sortedByDescending { it.manga.last_update }
                .take(12)
            if (waiting.isEmpty()) {
                InkShelf2EmptyShelf("Unread issues from comics you're reading will appear here.")
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - 32.dp - 20.dp) / 3
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(waiting, key = { "${it.manga.source}:${it.manga.url}" }) { item ->
                            ComicCoverCard(
                                item.manga,
                                percent = item.progress,
                                badgeText = "${(item.totalChapters - item.readChapters).coerceAtLeast(0)} unread",
                                width = cardWidth,
                                showTitle = false,
                                libraryShape = true,
                                onClick = { onOpen(item.manga) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "recently-saved") {
            InkShelf2SectionHeading("Recently Added", onClick = onLibrary, showSeeAll = true)
            Spacer(Modifier.height(12.dp))
            val recent = library.sortedByDescending { it.manga.date_added }.take(12)
            if (recent.isEmpty()) {
                InkShelf2EmptyShelf("Save a comic to start your personal collection.")
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - 32.dp - 20.dp) / 3
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recent, key = { "${it.manga.source}:${it.manga.url}" }) { item ->
                            ComicCoverCard(
                                item.manga,
                                width = cardWidth,
                                showTitle = false,
                                libraryShape = true,
                                onClick = { onOpen(item.manga) },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

/** Mock-up inspired comic masthead: angled, heavily inked, with a golden weekday. */
@Composable
internal fun InkShelf2ComicTitle(title: String) {
    val words = title.trim().split(" ")
    val first = words.dropLast(1).joinToString(" ")
    val second = words.lastOrNull().orEmpty()
    val size = if (first.length > 14 || second.length > 11) 28.sp else 34.sp
    Column(verticalArrangement = Arrangement.spacedBy((-6).dp)) {
        ComicInkLine(first, Color.White, size)
        ComicInkLine(second, InkShelf2Colors.AccentLight, size)
    }
}

@Composable
private fun ComicInkLine(value: String, ink: Color, fontSize: androidx.compose.ui.unit.TextUnit) {
    val style = TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize * 1.15f,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        letterSpacing = (-1.5).sp,
    )
    Box(modifier = Modifier.graphicsLayer { rotationZ = -4f }) {
        // The offset magenta ink gives the title an illustrated comic-cover
        // shadow rather than just a generic Android text shadow.
        Text(
            text = value,
            modifier = Modifier.offset(x = 2.dp, y = 3.dp),
            style = style.copy(
                color = InkShelf2Colors.Accent,
                drawStyle = Stroke(width = 12f, join = StrokeJoin.Round),
            ),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = value,
            style = style.copy(
                color = InkShelf2Colors.Background,
                drawStyle = Stroke(width = 8f, join = StrokeJoin.Round),
            ),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = value,
            style = style.copy(
                color = ink,
                shadow = Shadow(
                    color = InkShelf2Colors.Background.copy(alpha = 0.9f),
                    offset = Offset(2f, 3f),
                    blurRadius = 2f,
                ),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Compact details shortcut: one of the three equally sized actions under
 * the comic artwork. No full-width pill buttons or nested navigation changes.
 */
@Composable
internal fun InkShelf2DetailQuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: Boolean = false,
) {
    val tint = when {
        !enabled -> InkShelf2Colors.Muted.copy(alpha = 0.72f)
        highlight -> InkShelf2Colors.AccentLight
        else -> InkShelf2Colors.Text
    }
    val iconShape = RoundedCornerShape(17.dp)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier.size(57.dp)
                .clip(iconShape)
                .background(
                    if (highlight && enabled) InkShelf2Colors.AccentLight.copy(alpha = 0.17f)
                    else InkShelf2Colors.SurfaceRaised.copy(alpha = 0.70f),
                )
                .border(
                    1.dp,
                    if (highlight && enabled) InkShelf2Colors.AccentLight.copy(alpha = 0.65f)
                    else InkShelf2Colors.Border.copy(alpha = 0.85f),
                    iconShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(29.dp),
            )
        }
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shared comic/event detail page. Content providers supply their own issue rows. */
@Composable
internal fun InkShelf2DetailPage(
    imageModel: Any?,
    title: String,
    subtitle: String,
    attribution: String,
    padding: PaddingValues,
    onBack: () -> Unit,
    topActions: @Composable () -> Unit = {},
    compactComicHero: Boolean = false,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val refreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = { onRefresh?.invoke() },
    )
    Box(
        modifier = Modifier.fillMaxSize().pullRefresh(refreshState, enabled = onRefresh != null),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val backdrop = InkShelf2Colors.Background
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (compactComicHero) 0.72f else 0.65f,
                )
                Box(
                    modifier = Modifier.matchParentSize().background(
                        if (compactComicHero) Brush.verticalGradient(
                            0f to backdrop,
                            0.18f to backdrop.copy(alpha = 0.25f),
                            0.38f to Color.Transparent,
                            0.62f to backdrop.copy(alpha = 0.48f),
                            1f to backdrop,
                        ) else Brush.verticalGradient(listOf(Color.Transparent, backdrop)),
                    ),
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = if (compactComicHero) 212.dp else 285.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = if (compactComicHero) 10.dp else 12.dp,
                                vertical = if (compactComicHero) 0.dp else 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
                        }
                        Box { topActions() }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp,
                                top = if (compactComicHero) 4.dp else 12.dp,
                                bottom = if (compactComicHero) 12.dp else 16.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Cover of $title",
                            modifier = Modifier.size(
                                if (compactComicHero) 94.dp else 100.dp,
                                if (compactComicHero) 132.dp else 140.dp,
                            ).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compactComicHero) 4.dp else 0.dp),
                        ) {
                            Text(
                                title,
                                color = Color.White,
                                fontSize = if (compactComicHero) 22.sp else 25.sp,
                                lineHeight = if (compactComicHero) 26.sp else 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (subtitle.isNotBlank()) Text(
                                subtitle,
                                color = InkShelf2Colors.Muted,
                                fontSize = 11.sp,
                            )
                            if (attribution.isNotBlank()) Text(
                                text = attribution,
                                color = InkShelf2Colors.AccentLight,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
        content()
    }
        if (onRefresh != null) {
            PullRefreshIndicator(
                refreshing = refreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding()),
                backgroundColor = InkShelf2Colors.SurfaceRaised,
                contentColor = InkShelf2Colors.AccentLight,
            )
        }
    }
}

@Composable
internal fun InkShelf2DetailsScreen(
    manga: Manga,
    chapters: List<Chapter>,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRead: () -> Unit,
    sourceName: String?,
    sourceLinkError: String?,
    onFindSource: () -> Unit,
    isOpeningReader: Boolean,
    isLoadingIssues: Boolean,
    onRefresh: () -> Unit,
    isRefreshingDetails: Boolean,
    issuesError: String?,
    onChapter: (Chapter) -> Unit,
    onDownload: (List<Chapter>) -> Unit,
    canDownload: Boolean,
    downloadedChapterIds: Set<Long>,
    queuedChapterIds: Set<Long>,
    downloadStates: Map<Long, InkShelf2IssueDownloadInfo>,
    waitingForWifi: Boolean,
    downloadsPaused: Boolean,
    onResumeDownloads: () -> Unit,
    onDeleteIssueDownload: (Chapter) -> Unit,
    onSave: () -> Unit,
    onResetReading: (Long?) -> Unit = {},
    onEventChanged: () -> Unit = {},
    similarComics: List<InkShelf2SimilarPick>,
    onSimilarComic: (Manga) -> Unit,
) {
    // Chapter history, not simply a non-empty chapter list, determines
    // whether the reader has actually been started. J2K records completed
    // issues via read and in-progress issues via last_page_read.
    val hasReadingProgress = chapters.any { it.read || it.last_page_read > 0 }
    // Use the highest actual issue number shown by this source to identify
    // the correct Metron volume when multiple series share the same title.
    val seriesAnchorIssue = remember(manga.source, manga.url, chapters) {
        InkShelf2IssueNumbers.anchor(chapters)
    }
    val context = LocalContext.current
    val viewPrefs = remember(context) {
        context.applicationContext.getSharedPreferences(
            "inkshelf_2_issues_layout_v1",
            android.content.Context.MODE_PRIVATE,
        )
    }
    var showIssueGrid by rememberSaveable {
        mutableStateOf(viewPrefs.getBoolean("grid", true))
    }
    var newestIssuesFirst by rememberSaveable {
        mutableStateOf(viewPrefs.getBoolean("newest_first", true))
    }
    // Sort by the issue number actually displayed by the extension, not
    // J2K's internal chapter_order. Annuals/specials without a reliable number
    // remain in source order after regular issues in either direction.
    // Keep original Chapter instances: reading and progress still use the
    // exact source chapter/URL regardless of the visual sort.
    val orderedChapters = remember(chapters, newestIssuesFirst) {
        if (newestIssuesFirst) {
            chapters.sortedWith(
                compareByDescending<Chapter> {
                    InkShelf2IssueNumbers.fromChapter(it) ?: Int.MIN_VALUE
                },
            )
        } else {
            chapters.sortedWith(
                compareBy<Chapter> {
                    InkShelf2IssueNumbers.fromChapter(it) ?: Int.MAX_VALUE
                },
            )
        }
    }
    // Show just three issues at first to keep long comic details compact.
    // More issues expand in groups of three without changing source order.
    var visibleIssueCount by rememberSaveable(manga.source, manga.url) {
        mutableStateOf(3)
    }
    val visibleChapters = orderedChapters.take(visibleIssueCount)
    val moreIssuesRemaining = (chapters.size - visibleChapters.size).coerceAtLeast(0)
    val synopsisText = manga.description?.takeIf { it.isNotBlank() }
        ?: "No synopsis available from the selected source."
    var synopsisExpanded by rememberSaveable(manga.source, manga.url) {
        mutableStateOf(false)
    }
    var synopsisOverflows by remember(manga.source, manga.url, synopsisText) {
        mutableStateOf(false)
    }
    val pendingDownloads = remember(chapters, downloadedChapterIds, queuedChapterIds) {
        chapters.filter { chapter ->
            val id = chapter.id ?: return@filter false
            id !in downloadedChapterIds && id !in queuedChapterIds
        }
    }
    val unreadDownloads = remember(pendingDownloads) { pendingDownloads.filter { !it.read } }
    var downloadChoicesOpen by rememberSaveable(manga.source, manga.url) { mutableStateOf(false) }
    var issueActions by remember(manga.source, manga.url) { mutableStateOf<Chapter?>(null) }
    var issueToRemove by remember(manga.source, manga.url) { mutableStateOf<Chapter?>(null) }
    var comicMenu by remember(manga.source, manga.url) { mutableStateOf(false) }
    var resetSeries by remember(manga.source, manga.url) { mutableStateOf(false) }
    var resetIssue by remember(manga.source, manga.url) { mutableStateOf<Chapter?>(null) }
    if (resetSeries || resetIssue != null) {
        val single = resetIssue
        AlertDialog(
            onDismissRequest = { resetSeries = false; resetIssue = null },
            title = { Text(if (single == null) "Reset comic progress?" else "Reset issue progress?") },
            text = { Text(
                (if (single == null) "All loaded issues in this source edition will become unread and start at page one."
                else "${single.name} will become unread and start at page one.") +
                    " Downloads, bookmarks and Library entries are kept. Shared event progress may also change.",
            ) },
            confirmButton = { TextButton(onClick = {
                resetSeries = false
                resetIssue = null
                onResetReading(single?.id)
            }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { resetSeries = false; resetIssue = null }) { Text("Cancel") } },
        )
    }
    var addToEvent by remember(manga.source, manga.url) { mutableStateOf<Chapter?>(null) }
    addToEvent?.let { chapter ->
        InkShelf2AddToEventDialog(manga, chapter, onDismiss = { addToEvent = null }, onAdded = {
            addToEvent = null
            onEventChanged()
        })
    }
    val chosenIssue = issueActions
    if (chosenIssue != null) {
        val hasOffline = chosenIssue.id in downloadedChapterIds
        val isQueued = chosenIssue.id in queuedChapterIds
        AlertDialog(
            onDismissRequest = { issueActions = null },
            title = { Text(chosenIssue.name) },
            text = {
                Column {
                    Text("Issue options")
                    Spacer(Modifier.height(8.dp))
                    if (hasOffline || isQueued) {
                        TextButton(onClick = {
                            issueActions = null
                            issueToRemove = chosenIssue
                        }) {
                            Text(if (hasOffline) "Delete downloaded issue" else "Cancel queued download")
                        }
                    } else if (canDownload) {
                        TextButton(onClick = {
                            issueActions = null
                            onDownload(listOf(chosenIssue))
                        }) { Text("Download issue") }
                    }
                    TextButton(onClick = {
                        issueActions = null
                        onChapter(chosenIssue)
                    }) { Text("Read issue") }
                    TextButton(enabled = chosenIssue.id != null && manga.id != null, onClick = {
                        issueActions = null
                        addToEvent = chosenIssue
                    }) { Text("Add to Event") }
                    TextButton(enabled = chosenIssue.id != null, onClick = {
                        issueActions = null
                        resetIssue = chosenIssue
                    }) { Text("Reset issue progress") }
                }
            },
            confirmButton = {
                TextButton(onClick = { issueActions = null }) { Text("Close") }
            },
        )
    }
    val removingIssue = issueToRemove
    if (removingIssue != null) {
        AlertDialog(
            onDismissRequest = { issueToRemove = null },
            title = { Text("Remove offline issue?") },
            text = { Text("This removes the local download or pending queue entry for ${removingIssue.name}. Your reading progress is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    issueToRemove = null
                    onDeleteIssueDownload(removingIssue)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { issueToRemove = null }) { Text("Cancel") }
            },
        )
    }
    if (downloadChoicesOpen) {
        AlertDialog(
            onDismissRequest = { downloadChoicesOpen = false },
            title = { Text("Download Issues") },
            text = {
                Column {
                    Text("Choose what to save for offline reading. The comic will also be added to your Library.")
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        enabled = pendingDownloads.isNotEmpty(),
                        onClick = {
                            downloadChoicesOpen = false
                            onDownload(pendingDownloads)
                        },
                    ) { Text("All remaining issues (${pendingDownloads.size})") }
                    TextButton(
                        enabled = unreadDownloads.isNotEmpty(),
                        onClick = {
                            downloadChoicesOpen = false
                            onDownload(unreadDownloads)
                        },
                    ) { Text("Unread issues only (${unreadDownloads.size})") }
                    TextButton(
                        enabled = unreadDownloads.isNotEmpty(),
                        onClick = {
                            downloadChoicesOpen = false
                            val next = unreadDownloads.minByOrNull {
                                if (it.chapter_number >= 0f) it.chapter_number else Float.MAX_VALUE
                            }
                            if (next != null) onDownload(listOf(next))
                        },
                    ) { Text("Next unread issue") }
                }
            },
            confirmButton = {
                TextButton(onClick = { downloadChoicesOpen = false }) {
                    Text("Cancel")
                }
            },
            containerColor = InkShelf2Colors.SurfaceRaised,
            titleContentColor = InkShelf2Colors.Text,
            textContentColor = InkShelf2Colors.Muted,
        )
    }
    InkShelf2DetailPage(
        imageModel = manga,
        title = manga.title,
        subtitle = manga.getGenres()?.take(3)?.joinToString(" • ").orEmpty(),
        attribution = if (sourceName.isNullOrBlank()) "Source not installed" else "Source: $sourceName",
        compactComicHero = true,
        padding = padding,
        onBack = onBack,
        refreshing = isRefreshingDetails,
        onRefresh = { if (!isLoadingIssues) onRefresh() },
        topActions = {
            IconButton(onClick = { comicMenu = true }) {
                Icon(Icons.Outlined.MoreVert, "Comic options", tint = Color.White)
            }
            DropdownMenu(expanded = comicMenu, onDismissRequest = { comicMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Refresh comic and issues") },
                    enabled = !isLoadingIssues,
                    onClick = { comicMenu = false; onRefresh() },
                )
                DropdownMenuItem(
                    text = { Text("Reset reading progress") },
                    enabled = chapters.isNotEmpty() && !isLoadingIssues,
                    onClick = { comicMenu = false; resetSeries = true },
                )
            }
        },
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Three compact icon actions replace the former stacked pill
                // buttons, while retaining the original read/download/save
                // enablement and callbacks.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    InkShelf2DetailQuickAction(
                        label = when {
                            isLoadingIssues && chapters.isEmpty() -> "Loading Issues"
                            hasReadingProgress -> "Continue Reading"
                            else -> "Read Now"
                        },
                        icon = Icons.Filled.PlayArrow,
                        onClick = onRead,
                        modifier = Modifier.weight(1f),
                        highlight = true,
                        enabled = !isOpeningReader && !isLoadingIssues &&
                            chapters.isNotEmpty() && sourceLinkError == null,
                    )
                    InkShelf2DetailQuickAction(
                        label = when {
                            !canDownload -> "Unavailable"
                            pendingDownloads.isEmpty() -> "Downloaded"
                            else -> "Download Issues"
                        },
                        icon = Icons.Outlined.FileDownload,
                        onClick = { downloadChoicesOpen = true },
                        modifier = Modifier.weight(1f),
                        enabled = canDownload && !isLoadingIssues &&
                            pendingDownloads.isNotEmpty(),
                    )
                    InkShelf2DetailQuickAction(
                        label = if (manga.favorite) "In Library" else "Add to Library",
                        icon = if (manga.favorite) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Add,
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !manga.favorite,
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (queuedChapterIds.isNotEmpty()) {
                    if (waitingForWifi) {
                        Text(
                            "Downloads waiting for Wi-Fi. To use mobile data, turn off Wi-Fi-only in Menu → Settings → Downloads.",
                            modifier = Modifier.padding(top = 8.dp),
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 12.sp,
                        )
                    } else if (downloadsPaused) {
                        Spacer(Modifier.height(8.dp))
                        InkShelf2ActionButton(
                            label = "Resume Downloads",
                            onClick = onResumeDownloads,
                            outlined = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (sourceLinkError != null) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        sourceLinkError,
                        color = InkShelf2Colors.Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    InkShelf2ActionButton(
                        label = "Find on Installed Sources",
                        outlined = true,
                        onClick = onFindSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isLoadingIssues && chapters.isEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Fetching available issues from the comic source…",
                        color = InkShelf2Colors.Muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                InkShelf2PageHeading("Synopsis")
                Spacer(Modifier.height(8.dp))
                Text(
                    synopsisText,
                    color = InkShelf2Colors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!synopsisExpanded) {
                            synopsisOverflows = result.hasVisualOverflow
                        }
                    },
                )
                if (synopsisOverflows || synopsisExpanded) {
                    TextButton(
                        onClick = { synopsisExpanded = !synopsisExpanded },
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            if (synopsisExpanded) "Read less" else "Read more",
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                // Cached news and videos load independently of the reader and
                // only appear for strict, real full-series headline matches.
                InkShelf2RelatedPulse(comicTitle = manga.title)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        InkShelf2PageHeading("Issues (${chapters.size})")
                    }
                    IconButton(
                        onClick = {
                            showIssueGrid = true
                            viewPrefs.edit().putBoolean("grid", true).apply()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.GridView,
                            contentDescription = "Grid view of issue covers",
                            tint = if (showIssueGrid) InkShelf2Colors.AccentLight
                                else InkShelf2Colors.Muted,
                        )
                    }
                    IconButton(
                        onClick = {
                            showIssueGrid = false
                            viewPrefs.edit().putBoolean("grid", false).apply()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.ViewList,
                            contentDescription = "Compact list of issues",
                            tint = if (!showIssueGrid) InkShelf2Colors.AccentLight
                                else InkShelf2Colors.Muted,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showIssueGrid) "Tap a cover to read that issue"
                        else "Tap an issue to read",
                        modifier = Modifier.weight(1f),
                        color = InkShelf2Colors.Muted,
                        fontSize = 11.sp,
                    )
                    TextButton(
                        onClick = {
                            newestIssuesFirst = !newestIssuesFirst
                            viewPrefs.edit().putBoolean("newest_first", newestIssuesFirst).apply()
                            // Restart with three issues in the new sort order.
                            visibleIssueCount = 3
                        },
                    ) {
                        Icon(
                            Icons.Outlined.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = InkShelf2Colors.AccentLight,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (newestIssuesFirst) "Newest first" else "Oldest first",
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (chapters.size > 3) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Showing ${visibleChapters.size} of ${chapters.size} issues",
                        color = InkShelf2Colors.Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (chapters.isEmpty()) {
            item {
                InkShelf2EmptyShelf(
                    when {
                        isLoadingIssues -> "Loading issues from the installed comic source…"
                        sourceLinkError != null -> "This recommendation is not connected to a readable extension entry."
                        issuesError != null -> issuesError
                        else -> "No issues loaded yet. Tap Read Now to fetch them from the source."
                    },
                )
            }

        } else if (showIssueGrid) {
            items(visibleChapters.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    pair.forEach { chapter ->
                        InkShelf2IssueGridCard(
                            manga = manga,
                            chapter = chapter,
                            anchorIssueNumber = seriesAnchorIssue,
                            modifier = Modifier.weight(1f),
                            onClick = { onChapter(chapter) },
                            canDownload = canDownload,
                            downloaded = chapter.id in downloadedChapterIds,
                            queued = chapter.id in queuedChapterIds,
                            onDownload = { onDownload(listOf(chapter)) },
                            downloadInfo = chapter.id?.let(downloadStates::get),
                            waitingForWifi = waitingForWifi,
                            downloadsPaused = downloadsPaused,
                            onLongClick = { issueActions = chapter },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(visibleChapters) { chapter ->
                InkShelf2IssueListRow(
                    manga = manga,
                    chapter = chapter,
                    anchorIssueNumber = seriesAnchorIssue,
                    onClick = { onChapter(chapter) },
                    canDownload = canDownload,
                    downloaded = chapter.id in downloadedChapterIds,
                    queued = chapter.id in queuedChapterIds,
                    onDownload = { onDownload(listOf(chapter)) },
                    downloadInfo = chapter.id?.let(downloadStates::get),
                    waitingForWifi = waitingForWifi,
                    downloadsPaused = downloadsPaused,
                    onLongClick = { issueActions = chapter },
                )
                HorizontalDivider(
                    color = InkShelf2Colors.Border.copy(alpha = 0.3f),
                )
            }
        }
        if (moreIssuesRemaining > 0) {
            item(key = "load-more-issues") {
                InkShelf2ActionButton(
                    label = "See More Issues",
                    outlined = true,
                    onClick = {
                        visibleIssueCount = (visibleIssueCount + 3).coerceAtMost(chapters.size)
                    },
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = 16.dp, vertical = 12.dp,
                    ),
                )
            }
        }
        if (similarComics.isNotEmpty()) {
            item(key = "more-like-this") {
                InkShelf2MoreLikeThisShelf(
                    picks = similarComics,
                    onOpen = onSimilarComic,
                )
            }
        }
    }
}

@Composable
internal fun InkShelf2AccountScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(InkShelf2Colors.Background)
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        InkShelf2PageHeading("Account")
        Spacer(Modifier.height(14.dp))
        InkShelf2MetronSettings()
        Spacer(Modifier.height(22.dp))
        Text(
            "Other settings, backups and downloads remain available in the original InkShelf launcher " +
                "while the 2.0 Account screen is being rebuilt.",
            color = InkShelf2Colors.Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
internal fun ComicCoverCard(
    manga: Manga,
    onClick: () -> Unit,
    percent: Int? = null,
    percentLabel: String = "",
    badgeText: String? = null,
    width: androidx.compose.ui.unit.Dp? = 106.dp,
    showTitle: Boolean = true,
    libraryShape: Boolean = false,
) {
    InkShelf2CoverCard(
        imageModel = manga,
        title = manga.title,
        onClick = onClick,
        percent = percent,
        percentLabel = percentLabel,
        badgeText = badgeText,
        width = width,
        showTitle = showTitle,
        libraryShape = libraryShape,
    )
}

@Composable
internal fun InkShelf2CoverCard(
    imageModel: Any?,
    title: String,
    onClick: () -> Unit,
    percent: Int? = null,
    percentLabel: String = "",
    badgeText: String? = null,
    width: androidx.compose.ui.unit.Dp? = 106.dp,
    showTitle: Boolean = true,
    libraryShape: Boolean = false,
) {
    Column(
        modifier = (if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .clickable(onClick = onClick),
    ) {
        val shape = RoundedCornerShape(9.dp)
        Box(
            modifier = (if (libraryShape) {
                Modifier.fillMaxWidth().aspectRatio(0.71f)
            } else {
                Modifier.fillMaxWidth().height(if (width == null) 148.dp else 134.dp)
            })
                .clip(shape)
                .background(InkShelf2Colors.SurfaceRaised)
                .then(
                    if (libraryShape) {
                        Modifier.border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.7f), shape)
                    } else Modifier,
                ),
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Open $title",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (badgeText == "NEW") {
                Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(8.dp)
                    .clip(RoundedCornerShape(50)).background(InkShelf2Colors.Accent))
            } else if (!badgeText.isNullOrBlank()) {
                Text(
                    text = badgeText,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(InkShelf2Colors.Background.copy(alpha = 0.90f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (percent != null) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.74f))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { percent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = InkShelf2Colors.AccentLight,
                        trackColor = InkShelf2Colors.Border,
                    )
                    Text(
                        if (percentLabel.isBlank()) "${percent}%" else "$percentLabel ${percent}%",
                        modifier = Modifier.align(Alignment.End),
                        fontSize = 10.sp,
                        color = Color.White,
                    )
                }
            }
        }
        if (showTitle) {
        Text(
            title,
            color = InkShelf2Colors.Text,
            fontSize = 11.sp,
            maxLines = 2,
            lineHeight = 13.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        }
    }
}

