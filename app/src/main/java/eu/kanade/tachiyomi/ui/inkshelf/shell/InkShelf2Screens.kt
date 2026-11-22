package eu.kanade.tachiyomi.ui.inkshelf.shell

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewList
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
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
    personalPicks: List<InkShelf2Engine.PersonalPick>,
    profileName: String,
    padding: PaddingValues,
    onOpen: (Manga) -> Unit,
    onRead: (Manga) -> Unit,
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
    val unreadIssues = library.asSequence()
        .filter { it.state == InkShelf2ReadingState.READING }
        .sumOf { (it.totalChapters - it.readChapters).coerceAtLeast(0) }
    val savedKeys = remember(library) {
        library.map { it.manga.source to it.manga.url }.toSet()
    }
    // Discover owns recommendations; Home surfaces a short subset from the
    // same real cached source results. Never invent series or source URLs.
    val suggestions = remember(personalPicks, savedKeys) {
        personalPicks.asSequence()
            .filter { (it.manga.source to it.manga.url) !in savedKeys }
            .distinctBy { it.manga.source to it.manga.url }
            .take(9)
            .toList()
    }
    // The data arrives after the first composition; restore today's actual page.
    LaunchedEffect(slides.size, initial) {
        if (slides.isNotEmpty()) pager.scrollToPage(initial)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "continue-reading") {
            // A low-key, personal greeting rather than an oversized profile
            // panel. Reading controls stay front and centre; the profile and
            // its statistics remain available from Menu/Library.
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
            val name = profileName.takeIf { it.isNotBlank() }
            Text(
                text = if (name == null) greeting else "$greeting, $name",
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                color = InkShelf2Colors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            InkShelf2SectionHeading("Continue Reading", onClick = onLibrary)
            Spacer(Modifier.height(9.dp))
            val books = home?.history.orEmpty()
            if (books.isEmpty()) {
                InkShelf2EmptyShelf("Your reading progress will appear here.")
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // Exact three-column Library sizing: 14dp outer gutters,
                    // 9dp gaps, and a 0.71 comic-cover aspect ratio.
                    val libraryCoverWidth = (maxWidth - 28.dp - 18.dp) / 3
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(books) { item ->
                            ComicCoverCard(
                                item.manga,
                                percent = item.percent,
                                width = libraryCoverWidth,
                                showTitle = false,
                                libraryShape = true,
                                onClick = { onRead(item.manga) },
                            )
                        }
                    }
                }
                // Show the latest real history title and a one-tap resume,
                // while preserving the approved cover-only carousel above.
                val lastRead = books.first()
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .clickable { onRead(lastRead.manga) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        text = "LAST READ  •  ${lastRead.manga.title}",
                        modifier = Modifier.weight(1f),
                        color = InkShelf2Colors.Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "RESUME  ›",
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item {
            if (slides.isNotEmpty()) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth(),
                ) { index ->
                    val day = slides[index]
                    val pick = day.picks.firstOrNull()
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(295.dp)
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(InkShelf2Colors.Surface)
                            // Tapping the hero opens all daily picks; nested
                            // Read Now and Save actions retain their own clicks.
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
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.00f),
                                        InkShelf2Colors.SurfaceRaised.copy(alpha = 0.10f),
                                        InkShelf2Colors.Background.copy(alpha = 0.85f),
                                    ),
                                ),
                            ),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            InkShelf2ComicTitle(day.title)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (pick == null) "Explore comics for this theme"
                                else "${day.picks.size} PICKS  •  ${pick.title}",
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                InkShelf2ActionButton(
                                    // The hero promotes the complete daily selection.
                                    // Open all three verified picks first; each
                                    // then opens the normal InkShelf 2 details page.
                                    label = "Comic Details",
                                    leadingIcon = Icons.Outlined.AutoStories,
                                    enabled = true,
                                    onClick = { onDayPicks(day) },
                                    modifier = Modifier.weight(1f),
                                )
                                InkShelf2ActionButton(
                                    label = if (pick?.favorite == true) "In Library" else "Save",
                                    leadingIcon = if (pick?.favorite == true) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Add,
                                    enabled = pick != null,
                                    onClick = { pick?.let(onSave) },
                                    outlined = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
        item(key = "unread-library") {
            InkShelf2SectionHeading("Issues Waiting for You", onClick = onLibrary)
            if (unreadIssues > 0) {
                Text(
                    "$unreadIssues unread issues across comics you're reading",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(7.dp))
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
                    val cardWidth = (maxWidth - 28.dp - 18.dp) / 3
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
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
        if (suggestions.isNotEmpty()) {
            item(key = "home-personal-picks") {
                InkShelf2SectionHeading("Picked for You", onClick = onDiscover)
                Text(
                    "Based on your library and reading activity",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - 28.dp - 18.dp) / 3
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(suggestions, key = { "${it.manga.source}:${it.manga.url}" }) { pick ->
                            Column(modifier = Modifier.width(cardWidth)) {
                                ComicCoverCard(
                                    pick.manga,
                                    width = null,
                                    showTitle = true,
                                    libraryShape = true,
                                    onClick = { onOpen(pick.manga) },
                                )
                                if (pick.reason.isNotBlank()) {
                                    Text(
                                        pick.reason,
                                        color = InkShelf2Colors.Muted,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "recently-saved") {
            InkShelf2SectionHeading("Recently Added to Your Library", onClick = onLibrary)
            Spacer(Modifier.height(7.dp))
            val recent = library.sortedByDescending { it.manga.date_added }.take(12)
            if (recent.isEmpty()) {
                InkShelf2EmptyShelf("Save a comic to start your personal collection.")
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - 28.dp - 18.dp) / 3
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
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
        item { Spacer(Modifier.height(14.dp)) }
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
    issuesError: String?,
    onChapter: (Chapter) -> Unit,
    onSave: () -> Unit,
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
    // Keep initial artwork light: show just four rows of two covers.
    // Reveal subsequent issues only when requested, independently per comic.
    var visibleIssueCount by rememberSaveable(manga.source, manga.url) {
        mutableStateOf(8)
    }
    val visibleChapters = orderedChapters.take(visibleIssueCount)
    val moreIssuesRemaining = (chapters.size - visibleChapters.size).coerceAtLeast(0)
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(285.dp)) {
                AsyncImage(
                    model = manga,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.65f,
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, InkShelf2Colors.Background)),
                    ),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AsyncImage(
                        model = manga,
                        contentDescription = "Cover of ${manga.title}",
                        modifier = Modifier.size(100.dp, 140.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            manga.title,
                            color = Color.White,
                            fontSize = 25.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            manga.getGenres()?.take(3)?.joinToString(" • ").orEmpty(),
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = if (sourceName.isNullOrBlank()) "Source not installed"
                                else "Source: $sourceName",
                            color = InkShelf2Colors.AccentLight,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                InkShelf2ActionButton(
                    label = when {
                        isLoadingIssues && chapters.isEmpty() -> "Loading Issues…"
                        hasReadingProgress -> "Continue Reading"
                        else -> "Read Now"
                    },
                    leadingIcon = Icons.Filled.PlayArrow,
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth(),
                    // As in InkShelf 1, reading becomes available as soon as
                    // an actual issue exists. Never offer a dead Read Now tap.
                    enabled = !isOpeningReader && !isLoadingIssues &&
                        chapters.isNotEmpty() && sourceLinkError == null,
                )
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
                InkShelf2ActionButton(
                    label = if (manga.favorite) "In Library" else "Save to Library",
                    leadingIcon = if (manga.favorite) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Add,
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    outlined = true,
                    enabled = !manga.favorite,
                )
                Spacer(Modifier.height(15.dp))
                InkShelf2PageHeading("Synopsis")
                Spacer(Modifier.height(8.dp))
                Text(
                    manga.description?.takeIf { it.isNotBlank() }
                        ?: "No synopsis available from the selected source.",
                    color = InkShelf2Colors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
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
                            // Start the newly ordered list at the first eight
                            // instead of loading hundreds of covers at once.
                            visibleIssueCount = 8
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
                if (chapters.size > 8) {
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
                )
                HorizontalDivider(
                    color = InkShelf2Colors.Border.copy(alpha = 0.3f),
                )
            }
        }
        if (moreIssuesRemaining > 0) {
            item(key = "load-more-issues") {
                InkShelf2ActionButton(
                    label = "Show ${minOf(8, moreIssuesRemaining)} More Issues",
                    outlined = true,
                    onClick = {
                        visibleIssueCount = (visibleIssueCount + 8).coerceAtMost(chapters.size)
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
private fun ComicCoverCard(
    manga: Manga,
    onClick: () -> Unit,
    percent: Int? = null,
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
                model = manga,
                contentDescription = "Open ${manga.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (!badgeText.isNullOrBlank()) {
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
                        "${percent}%",
                        modifier = Modifier.align(Alignment.End),
                        fontSize = 10.sp,
                        color = Color.White,
                    )
                }
            }
        }
        if (showTitle) {
        Text(
            manga.title,
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

