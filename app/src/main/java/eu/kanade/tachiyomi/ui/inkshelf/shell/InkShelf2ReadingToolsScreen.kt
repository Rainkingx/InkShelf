package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import java.text.DateFormat
import java.util.Date

/** Presentation-only snapshots of the original J2K services. */
internal data class InkShelf2QueueEntry(
    val manga: Manga,
    val chapter: Chapter,
    val status: Download.State,
    val percent: Int,
)
internal data class InkShelf2SavedSeries(val manga: Manga, val issues: Int)
internal data class InkShelf2HistoryEntry(val manga: Manga, val chapter: Chapter, val lastRead: Long)
internal data class InkShelf2RecentUpdate(val manga: Manga, val chapter: Chapter, val fetchedAt: Long)

@Composable
private fun ReadingToolHeading(title: String, back: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = back) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to Menu", tint = InkShelf2Colors.Text)
        }
        InkShelf2PageHeading(title)
    }
}

@Composable
internal fun InkShelf2DownloadsScreen(
    padding: PaddingValues,
    queued: List<InkShelf2QueueEntry>,
    saved: List<InkShelf2SavedSeries>,
    wifiRequired: Boolean,
    paused: Boolean,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: (InkShelf2QueueEntry) -> Unit,
    onOpen: (Manga) -> Unit,
) {
    var pendingCancel by remember { mutableStateOf<InkShelf2QueueEntry?>(null) }
    val toCancel = pendingCancel
    if (toCancel != null) {
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancel download?") },
            text = { Text(toCancel.manga.title + " — " + toCancel.chapter.name) },
            confirmButton = {
                TextButton(onClick = { pendingCancel = null; onCancel(toCancel) }) {
                    Text("Cancel download")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) { Text("Keep") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "heading") { ReadingToolHeading("Downloads", onBack) }
        item(key = "summary") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    queued.size.toString() + " in queue  •  " + saved.sumOf { it.issues } + " saved issues",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
                if (queued.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    if (wifiRequired) {
                        Text(
                            "Waiting for Wi-Fi. Downloads are set to Wi-Fi only in Advanced Settings.",
                            color = InkShelf2Colors.AccentLight, fontSize = 12.sp,
                        )
                    } else {
                        InkShelf2ActionButton(
                            label = if (paused) "Resume Downloads" else "Pause Downloads",
                            leadingIcon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            onClick = if (paused) onResume else onPause,
                            outlined = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item(key = "queue-label") {
            Text(
                "DOWNLOAD QUEUE",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = InkShelf2Colors.AccentLight, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            )
        }
        if (queued.isEmpty()) {
            item(key = "empty-queue") { InkShelf2EmptyShelf("Nothing queued. Open any comic and tap Download Issues.") }
        } else {
            items(queued, key = { it.manga.source.toString() + ":" + it.chapter.id + ":" + it.chapter.url }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = entry.manga,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp, 68.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onOpen(entry.manga) },
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            entry.manga.title, color = InkShelf2Colors.Text,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            entry.chapter.name, color = InkShelf2Colors.Muted,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val status = when {
                            entry.status == Download.State.DOWNLOADING -> "Downloading " + entry.percent + "%"
                            entry.status == Download.State.ERROR -> "Failed — retry from Comic Details"
                            wifiRequired -> "Waiting for Wi-Fi"
                            paused -> "Paused"
                            else -> "Queued"
                        }
                        Text(
                            status, color = if (entry.status == Download.State.ERROR)
                                Color(0xFFFFA39E) else InkShelf2Colors.AccentLight,
                            fontSize = 11.sp,
                        )
                        if (entry.status == Download.State.DOWNLOADING) {
                            LinearProgressIndicator(
                                progress = { entry.percent.coerceIn(0, 100) / 100f },
                                color = InkShelf2Colors.AccentLight,
                                trackColor = InkShelf2Colors.Border,
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                    IconButton(onClick = { pendingCancel = entry }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Cancel download for " + entry.chapter.name,
                            tint = InkShelf2Colors.Muted,
                        )
                    }
                }
                HorizontalDivider(
                    color = InkShelf2Colors.Border.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item(key = "offline-label") {
            Text(
                "AVAILABLE OFFLINE",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                color = InkShelf2Colors.AccentLight, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            )
        }
        if (saved.isEmpty()) {
            item(key = "empty-offline") { InkShelf2EmptyShelf("Completed downloads will appear here.") }
        } else {
            items(saved, key = { it.manga.source.toString() + ":" + it.manga.url }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onOpen(entry.manga) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = entry.manga, contentDescription = null,
                        modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(7.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            entry.manga.title, color = InkShelf2Colors.Text,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            entry.issues.toString() + " issues saved offline",
                            color = InkShelf2Colors.AccentLight, fontSize = 11.sp,
                        )
                    }
                    Icon(
                        Icons.Outlined.CheckCircleOutline,
                        contentDescription = "Available offline",
                        tint = InkShelf2Colors.AccentLight,
                    )
                }
            }
        }
    }
}

@Composable
internal fun InkShelf2HistoryScreen(
    padding: PaddingValues,
    history: List<InkShelf2HistoryEntry>,
    onBack: () -> Unit,
    onOpen: (Manga) -> Unit,
    onRead: (Manga, Chapter) -> Unit,
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "heading") { ReadingToolHeading("Reading History", onBack) }
        if (history.isEmpty()) {
            item(key = "empty") { InkShelf2EmptyShelf("Comics you've opened in the reader will appear here.") }
        } else {
            item(key = "intro") {
                Text(
                    "Your latest reading sessions",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
            }
            items(history, key = { it.manga.source.toString() + ":" + it.chapter.url + ":" + it.lastRead }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onRead(entry.manga, entry.chapter) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = entry.manga, contentDescription = null,
                        modifier = Modifier.size(57.dp, 80.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            entry.manga.title, color = InkShelf2Colors.Text,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(entry.chapter.name, color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
                        if (entry.lastRead > 0) {
                            Text(
                                formatter.format(Date(entry.lastRead)),
                                color = InkShelf2Colors.Muted, fontSize = 11.sp,
                            )
                        }
                        TextButton(onClick = { onOpen(entry.manga) }) { Text("Comic Details", fontSize = 11.sp) }
                    }
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Read " + entry.chapter.name,
                        tint = InkShelf2Colors.AccentLight,
                    )
                }
                HorizontalDivider(
                    color = InkShelf2Colors.Border.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** The same J2K recently-fetched chapter query used by the classic Updates tab. */
@Composable
internal fun InkShelf2RecentUpdatesScreen(
    padding: PaddingValues,
    updates: List<InkShelf2RecentUpdate>,
    onBack: () -> Unit,
    onOpen: (Manga) -> Unit,
    onRead: (Manga, Chapter) -> Unit,
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "heading") { ReadingToolHeading("Latest Updates", onBack) }
        item(key = "intro") {
            Text(
                "Recently discovered comic issues",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = InkShelf2Colors.Muted,
                fontSize = 12.sp,
            )
        }
        if (updates.isEmpty()) {
            item(key = "empty") {
                InkShelf2EmptyShelf("No recent issue updates. Refresh your library to check for new chapters.")
            }
        } else {
            items(updates, key = { it.manga.id.toString() + ":" + it.chapter.url + ":" + it.fetchedAt }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onRead(item.manga, item.chapter) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = item.manga, contentDescription = null,
                        modifier = Modifier.size(55.dp, 78.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            item.manga.title, color = InkShelf2Colors.Text,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.chapter.name,
                            color = InkShelf2Colors.AccentLight, fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatter.format(Date(item.fetchedAt)),
                            color = InkShelf2Colors.Muted, fontSize = 11.sp,
                        )
                        TextButton(onClick = { onOpen(item.manga) }) { Text("Comic Details") }
                    }
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Read " + item.chapter.name,
                        tint = InkShelf2Colors.AccentLight,
                    )
                }
                HorizontalDivider(
                    color = InkShelf2Colors.Border.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
