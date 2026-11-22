package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Locale

/** Four functional filters that reflect actual local J2K status, not mock values. */
private enum class ComicAction(val title: String, val explanation: String) {
    REMOVE("Remove from Library", "The comic leaves your Library. Reading progress and downloaded issues are kept."),
    DELETE_DOWNLOADS("Delete offline downloads", "The comic stays in your Library, but its downloaded issues and queued downloads will be removed."),
    REMOVE_AND_DELETE("Remove comic and downloads", "The comic leaves your Library and its downloaded issues are deleted. Reading history is kept."),
}

private enum class ShelfFilter(val title: String) {
    ALL("All"),
    READING("Reading"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
}

@Composable
internal fun InkShelf2LibraryScreen(
    library: List<InkShelf2LibraryItem>,
    readingHistory: List<InkShelf2Engine.Reading>,
    padding: PaddingValues,
    onRead: (Manga) -> Unit,
    onOpen: (Manga) -> Unit,
    onComicAction: (Manga, Boolean, Boolean) -> Unit,
) {
    var grid by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(ShelfFilter.ALL.name) }
    var menuComic by androidx.compose.runtime.remember { mutableStateOf<Manga?>(null) }
    var confirmAction by androidx.compose.runtime.remember { mutableStateOf<Pair<Manga, ComicAction>?>(null) }
    val active = ShelfFilter.valueOf(selected)
    val selectedComic = menuComic
    if (selectedComic != null) {
        AlertDialog(
            onDismissRequest = { menuComic = null },
            title = { Text("Comic options") },
            text = {
                Column {
                    Text(selectedComic.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(9.dp))
                    ComicAction.entries.forEach { action ->
                        TextButton(onClick = {
                            menuComic = null
                            confirmAction = selectedComic to action
                        }) { Text(action.title) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuComic = null }) { Text("Close") }
            },
        )
    }
    val pending = confirmAction
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(pending.second.title + "?") },
            text = { Text(pending.second.explanation) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    onComicAction(
                        pending.first,
                        pending.second != ComicAction.DELETE_DOWNLOADS,
                        pending.second != ComicAction.REMOVE,
                    )
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
            },
        )
    }

    val readingCount = library.count { it.state == InkShelf2ReadingState.READING }
    val completedCount = library.count { it.state == InkShelf2ReadingState.COMPLETED }
    val holdCount = library.count { it.state == InkShelf2ReadingState.ON_HOLD }

    val visible = when (active) {
        ShelfFilter.ALL -> library
        ShelfFilter.READING -> library.filter { it.state == InkShelf2ReadingState.READING }
        ShelfFilter.COMPLETED -> library.filter { it.state == InkShelf2ReadingState.COMPLETED }
        ShelfFilter.ON_HOLD -> library.filter { it.state == InkShelf2ReadingState.ON_HOLD }
    }
    Column(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere().padding(padding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkShelf2PageHeading("My Library", modifier = Modifier.weight(1f))
            // Compact single segmented switch, as in the approved four-screen mock-up.
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
                                .background(
                                    if (grid == isGrid) InkShelf2Colors.Accent
                                    else Color.Transparent,
                                )
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

        // Distribute all four filters across the screen instead of hiding
        // On Hold off the right edge. Counts and labels stay live.
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ShelfFilter.entries.forEach { filter ->
                val count = when (filter) {
                    ShelfFilter.ALL -> library.size
                    ShelfFilter.READING -> readingCount
                    ShelfFilter.COMPLETED -> completedCount
                    ShelfFilter.ON_HOLD -> holdCount
                }
                ShelfFilterPill(
                    text = "${filter.title} ($count)",
                    active = active == filter,
                    modifier = Modifier.weight(1f),
                    onClick = { selected = filter.name },
                )
            }
        }

        if (visible.isEmpty()) {
            val message = when (active) {
                ShelfFilter.ALL -> "Your collection will appear here when you add comics to the library."
                ShelfFilter.READING -> "No comics have recorded reading progress yet."
                ShelfFilter.COMPLETED -> "No comics have been fully read yet."
                ShelfFilter.ON_HOLD -> "No comics are On Hold. You can assign them to an On Hold category in your existing library."
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
            ) {
                if (readingHistory.isNotEmpty()) {
                    item(key = "library-continue-reading") {
                        InkShelf2LibraryContinueReading(
                            history = readingHistory,
                            gutter = 16.dp,
                            onRead = onRead,
                            onSeeAll = { selected = ShelfFilter.READING.name },
                        )
                    }
                }
                item { InkShelf2EmptyShelf(message) }
            }
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (readingHistory.isNotEmpty()) {
                    item(
                        key = "library-continue-reading",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        InkShelf2LibraryContinueReading(
                            history = readingHistory,
                            gutter = 2.dp,
                            onRead = onRead,
                            onSeeAll = { selected = ShelfFilter.READING.name },
                        )
                    }
                }
                gridItems(
                    items = visible,
                    key = { it.manga.id ?: it.manga.url },
                ) { item ->
                    ShelfCover(
                        item,
                        onClick = { onOpen(item.manga) },
                        onLongClick = { menuComic = item.manga },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
            ) {
                if (readingHistory.isNotEmpty()) {
                    item(key = "library-continue-reading") {
                        InkShelf2LibraryContinueReading(
                            history = readingHistory,
                            gutter = 16.dp,
                            onRead = onRead,
                            onSeeAll = { selected = ShelfFilter.READING.name },
                        )
                    }
                }
                items(visible, key = { it.manga.id ?: it.manga.url }) { item ->
                    ShelfListRow(
                        item,
                        onClick = { onOpen(item.manga) },
                        onLongClick = { menuComic = item.manga },
                    )
                    HorizontalDivider(
                        color = InkShelf2Colors.Border.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * The former Home Continue Reading shelf, now placed directly below the
 * Library counters inside the scroll content in both grid and list modes.
 * Uses the same recent-reading history and exact reader callback as before.
 */
@Composable
private fun InkShelf2LibraryContinueReading(
    history: List<InkShelf2Engine.Reading>,
    gutter: Dp,
    onRead: (Manga) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (history.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = gutter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Continue Reading",
                color = InkShelf2Colors.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "See all  ›",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                color = InkShelf2Colors.AccentLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val coverWidth = (maxWidth - gutter * 2 - 20.dp) / 3
            LazyRow(
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = history,
                    key = { "${it.manga.source}:${it.manga.url}" },
                ) { reading ->
                    ComicCoverCard(
                        manga = reading.manga,
                        percent = reading.percent,
                        width = coverWidth,
                        showTitle = false,
                        libraryShape = true,
                        onClick = { onRead(reading.manga) },
                    )
                }
            }
        }
        val lastRead = history.first()
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = gutter, vertical = 10.dp)
                .clip(RoundedCornerShape(8.dp))
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
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShelfFilterPill(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(31.dp)
            .clip(shape)
            .background(
                if (active) InkShelf2Colors.AccentGradient
                else Brush.horizontalGradient(
                    listOf(InkShelf2Colors.SurfaceRaised, InkShelf2Colors.Background),
                ),
            )
            .border(
                1.dp,
                if (active) InkShelf2Colors.AccentLight
                else InkShelf2Colors.Border,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ShelfCover(item: InkShelf2LibraryItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.71f)
            .clip(shape)
            .background(InkShelf2Colors.SurfaceRaised)
            .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.7f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = item.manga,
            contentDescription = "Open ${item.manga.title}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (item.state != InkShelf2ReadingState.UNSTARTED) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, InkShelf2Colors.Background.copy(alpha = 0.89f)),
                        ),
                    ),
            ) {
                ShelfStatus(
                    item = item,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun ShelfStatus(item: InkShelf2LibraryItem, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        val progress = item.progress
        if (item.state == InkShelf2ReadingState.READING && progress != null) {
            // Keep the actual progress percentage. The small violet leading
            // marker remains visible even at 1–5%, without inflating the bar.
            Box(
                modifier = Modifier.fillMaxWidth().height(7.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = InkShelf2Colors.AccentLight,
                    trackColor = InkShelf2Colors.SurfaceRaised,
                    modifier = Modifier.fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                if (progress > 0) {
                    Box(
                        modifier = Modifier.size(7.dp)
                            .clip(CircleShape)
                            .background(InkShelf2Colors.AccentLight),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "${progress}%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(InkShelf2Colors.Background.copy(alpha = 0.64f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val glyph = when (item.state) {
                    InkShelf2ReadingState.COMPLETED -> Icons.Outlined.CheckCircleOutline
                    InkShelf2ReadingState.ON_HOLD -> Icons.Filled.Pause
                    else -> Icons.Outlined.AutoStories
                }
                Icon(glyph, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    when (item.state) {
                        InkShelf2ReadingState.COMPLETED -> "Completed"
                        InkShelf2ReadingState.ON_HOLD -> "On Hold"
                        InkShelf2ReadingState.READING -> "Reading"
                        else -> ""
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ShelfListRow(item: InkShelf2LibraryItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.manga,
            contentDescription = null,
            modifier = Modifier.size(61.dp, 86.dp).clip(RoundedCornerShape(7.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                item.manga.title,
                color = InkShelf2Colors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                when (item.state) {
                    InkShelf2ReadingState.COMPLETED -> "Completed"
                    InkShelf2ReadingState.READING -> "Reading"
                    InkShelf2ReadingState.ON_HOLD -> "On Hold"
                    InkShelf2ReadingState.UNSTARTED -> "Not started"
                },
                fontSize = 12.sp,
                color = InkShelf2Colors.AccentLight,
            )
            if (item.totalChapters > 0) {
                Text(
                    "${item.readChapters} of ${item.totalChapters} cached issues read",
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { (item.progress ?: 0) / 100f },
                    color = InkShelf2Colors.AccentLight,
                    trackColor = InkShelf2Colors.Border,
                    modifier = Modifier.fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}
