package eu.kanade.tachiyomi.ui.inkshelf.shell

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
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
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
    padding: PaddingValues,
    onOpen: (Manga) -> Unit,
    onRead: (Manga) -> Unit,
    onSave: (Manga) -> Unit,
    onDiscover: () -> Unit,
    onArtworkSource: (String) -> Unit,
) {
    val slides = home?.days.orEmpty()
    val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val initial = slides.indexOfFirst { it.calendarDay == currentDay }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial, pageCount = { slides.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    // The data arrives after the first composition; restore today's actual page.
    LaunchedEffect(slides.size, initial) {
        if (slides.isNotEmpty()) pager.scrollToPage(initial)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(InkShelf2Colors.Background),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                            .height(340.dp)
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(InkShelf2Colors.Surface),
                    ) {
                        AsyncImage(
                            model = day.artwork.imageUrl,
                            contentDescription = day.artwork.credit,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color(0xFF0C0714).copy(alpha = 0.40f),
                                        Color(0xFF08060D).copy(alpha = 0.96f),
                                    ),
                                ),
                            ),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                "DAILY COMICS  •  " + if (day.calendarDay == currentDay) "TODAY" else "WEEKDAY PREVIEW",
                                color = InkShelf2Colors.AccentLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                day.title,
                                color = Color.White,
                                fontSize = 35.sp,
                                lineHeight = 37.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (pick == null) "Explore comics for this theme"
                                else "${day.picks.size} PICKS  •  ${pick.title}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GradientAction(
                                    label = if (pick != null) "Read Now" else "Discover",
                                    leadingIcon = Icons.Filled.PlayArrow,
                                    enabled = true,
                                    onClick = { if (pick != null) onRead(pick) else onDiscover() },
                                    modifier = Modifier.weight(1f),
                                )
                                GradientAction(
                                    label = if (pick?.favorite == true) "In Library" else "Save",
                                    leadingIcon = if (pick?.favorite == true) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Add,
                                    enabled = pick != null,
                                    onClick = { pick?.let(onSave) },
                                    outlined = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                day.artwork.credit,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                modifier = Modifier.clickable { onArtworkSource(day.artwork.sourcePage) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
        item {
            SectionTitle("Continue Reading", onClick = null)
            Spacer(Modifier.height(9.dp))
            val books = home?.history.orEmpty()
            if (books.isEmpty()) {
                EmptyShelf("Your reading progress will appear here.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(books) { item ->
                        ComicCoverCard(item.manga, percent = item.percent, onClick = { onRead(item.manga) })
                    }
                }
            }
        }
        item {
            SectionTitle("Today's Picks", onClick = onDiscover)
            Spacer(Modifier.height(9.dp))
            val books = home?.todayPicks.orEmpty()
            if (books.isEmpty()) {
                EmptyShelf("No verified recommendations for today's theme yet.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(books) { manga -> ComicCoverCard(manga, onClick = { onOpen(manga) }) }
                }
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
internal fun InkShelf2DiscoverScreen(
    discover: InkShelf2Engine.Discover?,
    padding: PaddingValues,
    onOpen: (Manga) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shelves = listOf(
        "Trending Right Now" to discover?.trending.orEmpty(),
        "New This Week" to discover?.newest.orEmpty(),
        "Recommended for You" to discover?.recommended.orEmpty(),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(InkShelf2Colors.Background),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Heading("Discover Comics")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    label = { Text("Filter loaded recommendations") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                )
            }
        }
        shelves.forEach { (name, list) ->
            item {
                val filtered = if (query.isBlank()) list else list.filter {
                    it.title.contains(query, ignoreCase = true)
                }
                SectionTitle(name, onClick = null)
                Spacer(Modifier.height(9.dp))
                if (filtered.isEmpty()) {
                    EmptyShelf("No matching comics in the existing Discover cache.")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered.take(20)) { manga ->
                            ComicCoverCard(manga, onClick = { onOpen(manga) })
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun InkShelf2LibraryScreen(
    library: List<Manga>,
    padding: PaddingValues,
    onOpen: (Manga) -> Unit,
) {
    var grid by rememberSaveable { mutableStateOf(true) }
    val spacing = 10.dp
    Column(
        modifier = Modifier.fillMaxSize()
            .background(InkShelf2Colors.Background)
            .padding(padding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heading("My Library", modifier = Modifier.weight(1f))
            // The mock-up shows a compact two-segment control, not two
            // unrelated icon buttons. Keep both icons in the same capsule.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(InkShelf2Colors.SurfaceRaised)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(true to Icons.Outlined.GridView, false to Icons.Outlined.ViewList)
                    .forEach { (isGrid, image) ->
                        Box(
                            modifier = Modifier
                                .size(33.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (grid == isGrid) InkShelf2Colors.Accent else Color.Transparent)
                                .clickable { grid = isGrid },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = image,
                                contentDescription = if (isGrid) "Grid view" else "List view",
                                tint = if (grid == isGrid) Color.White else InkShelf2Colors.Muted,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
            }
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text("All (${library.size})") },
            )
        }
        if (library.isEmpty()) {
            EmptyShelf("Your existing J2K library will appear here.")
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                gridItems(library) { manga ->
                    ComicCoverCard(
                        manga = manga,
                        width = null,
                        onClick = { onOpen(manga) },
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 18.dp)) {
                items(library) { manga ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(manga) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = manga,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp, 76.dp).clip(RoundedCornerShape(7.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            manga.title,
                            color = InkShelf2Colors.Text,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    HorizontalDivider(color = InkShelf2Colors.Border.copy(alpha = 0.3f))
                }
            }
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
    onChapter: (Chapter) -> Unit,
    onSave: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(InkShelf2Colors.Background),
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
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                GradientAction(
                    label = "Continue Reading",
                    leadingIcon = Icons.Filled.PlayArrow,
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = chapters.isNotEmpty(),
                )
                Spacer(Modifier.height(8.dp))
                GradientAction(
                    label = if (manga.favorite) "In Library" else "Save to Library",
                    leadingIcon = if (manga.favorite) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Add,
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    outlined = true,
                    enabled = !manga.favorite,
                )
                Spacer(Modifier.height(15.dp))
                Heading("Synopsis")
                Spacer(Modifier.height(8.dp))
                Text(
                    manga.description?.takeIf { it.isNotBlank() }
                        ?: "No synopsis available from the selected source.",
                    color = InkShelf2Colors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(16.dp))
                Heading("Issues (${chapters.size})")
            }
        }
        if (chapters.isEmpty()) {
            item { EmptyShelf("No issues in the local cache. Refresh from the comic source in the current app.") }
        } else {
            items(chapters) { chapter ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onChapter(chapter) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (chapter.read) Icons.Outlined.CheckCircleOutline else Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = InkShelf2Colors.AccentLight,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            chapter.name,
                            color = InkShelf2Colors.Text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (chapter.read) "Read" else "Tap to read",
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(Icons.Outlined.PlayArrow, "Open reader", tint = InkShelf2Colors.AccentLight)
                }
                HorizontalDivider(color = InkShelf2Colors.Border.copy(alpha = 0.3f))
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
            .padding(20.dp),
    ) {
        Heading("Account")
        Spacer(Modifier.height(14.dp))
        Text(
            "This is the separate InkShelf 2.0 preview. Settings, backups, source management and downloads " +
                "remain available in the original InkShelf launcher until those tools have been rebuilt.",
            color = InkShelf2Colors.Muted,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun ComicCoverCard(
    manga: Manga,
    onClick: () -> Unit,
    percent: Int? = null,
    width: androidx.compose.ui.unit.Dp? = 106.dp,
) {
    Column(
        modifier = (if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(if (width == null) 148.dp else 150.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(InkShelf2Colors.SurfaceRaised),
        ) {
            AsyncImage(
                model = manga,
                contentDescription = "Open ${manga.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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

/**
 * Reusable mock-up buttons: bright violet gradient primary with dark play
 * glyph/text; transparent dark outlined secondary with a plus glyph.
 */
@Composable
private fun GradientAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(50)
    val background = when {
        !enabled -> Brush.horizontalGradient(listOf(InkShelf2Colors.Surface, InkShelf2Colors.Surface))
        outlined -> Brush.horizontalGradient(listOf(Color(0xD010081A), Color(0xD0140B20)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFB543EE), Color(0xFFE46EF6)))
    }
    Box(
        modifier = modifier
            .height(41.dp)
            .clip(shape)
            .background(background)
            .then(
                if (outlined) Modifier.border(
                    width = 1.dp,
                    color = if (enabled) InkShelf2Colors.AccentLight else InkShelf2Colors.Border,
                    shape = shape,
                ) else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            val fg = when {
                !enabled -> InkShelf2Colors.Muted
                outlined -> Color.White
                else -> Color(0xFF130A1A)
            }
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = fg,
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = InkShelf2Colors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SectionTitle(text: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = InkShelf2Colors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (onClick != null) Text("See all ›", color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyShelf(message: String) {
    Text(
        message,
        color = InkShelf2Colors.Muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
