package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCovers
import eu.kanade.tachiyomi.ui.manga.InkShelfIssueMetadata

/** Real J2K queue progress; no second downloader or file cache. */
internal data class InkShelf2IssueDownloadInfo(
    val status: Download.State,
    val progress: Int,
)

private fun downloadStatusText(
    chapter: Chapter,
    downloaded: Boolean,
    info: InkShelf2IssueDownloadInfo?,
    waitingForWifi: Boolean,
    downloadsPaused: Boolean,
): String = when {
    downloaded -> "Downloaded • offline ready"
    info?.status == Download.State.DOWNLOADING -> "Downloading ${info.progress}%"
    info?.status == Download.State.ERROR -> "Download failed • retry"
    info?.status == Download.State.QUEUE && waitingForWifi -> "Waiting for Wi-Fi"
    info?.status == Download.State.QUEUE && downloadsPaused -> "Paused • resume downloads"
    info?.status == Download.State.QUEUE -> "Queued"
    info?.status == Download.State.DOWNLOADED -> "Finishing download…"
    else -> issueStatus(chapter)
}

/** Artwork belongs to Metron; tapping always opens the original source chapter. */
@Composable
private fun IssueArtwork(
    manga: Manga,
    chapter: Chapter,
    anchorIssueNumber: Int?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val issue = remember(chapter.name, chapter.chapter_number) {
        InkShelf2IssueNumbers.fromChapter(chapter)
    }
    var cover by remember(manga.source, manga.url, chapter.name, issue) {
        mutableStateOf<String?>(null)
    }
    // InkShelf 1 already supports exact source-supplied per-issue artwork.
    // Use that first; only ask Metron to fill genuine cover gaps.
    val sourceCover = InkShelfIssueMetadata.from(chapter).coverUrl
    val imageUrl = sourceCover ?: cover
    DisposableEffect(context, manga.source, manga.url, chapter.name, issue, anchorIssueNumber) {
        var active = true
        if (sourceCover == null && issue != null) {
            InkShelfMetronCovers.requestIssueCover(
                context, manga, issue,
                onFound = { url -> if (active) cover = url },
                anchorIssueNumber = anchorIssueNumber,
            )
        }
        onDispose { active = false }
    }

    InkShelf2IssueArtworkFrame(
        imageUrl = imageUrl,
        title = chapter.name,
        fallbackLabel = issue?.let { "ISSUE #$it" } ?: "ISSUE",
        read = chapter.read,
        modifier = modifier,
    )
}

/** Common issue artwork, including the honest missing-cover placeholder. */
@Composable
internal fun InkShelf2IssueArtworkFrame(
    imageUrl: String?,
    title: String,
    fallbackLabel: String,
    read: Boolean,
    modifier: Modifier = Modifier,
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier.clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        InkShelf2Colors.SurfaceRaised,
                        InkShelf2Colors.Surface,
                        InkShelf2Colors.Background,
                    ),
                ),
            )
            .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.55f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = InkShelf2Colors.AccentLight,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                fallbackLabel,
                color = InkShelf2Colors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (imageUrl != null && !failed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Cover for $title",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        }
        if (read) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(InkShelf2Colors.Background.copy(alpha = 0.84f))
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Read",
                    tint = InkShelf2Colors.AccentLight,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun InkShelf2IssueGridCard(
    manga: Manga,
    chapter: Chapter,
    modifier: Modifier = Modifier,
    anchorIssueNumber: Int?,
    onClick: () -> Unit,
    canDownload: Boolean = false,
    downloaded: Boolean = false,
    queued: Boolean = false,
    onDownload: () -> Unit = {},
    downloadInfo: InkShelf2IssueDownloadInfo? = null,
    waitingForWifi: Boolean = false,
    downloadsPaused: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 4.dp),
    ) {
        IssueArtwork(manga, chapter, anchorIssueNumber, Modifier.fillMaxWidth().aspectRatio(0.71f))
        Text(
            chapter.name,
            modifier = Modifier.padding(start = 3.dp, end = 3.dp, top = 7.dp),
            color = InkShelf2Colors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                downloadStatusText(chapter, downloaded, downloadInfo, waitingForWifi, downloadsPaused),
                modifier = Modifier.weight(1f).padding(start = 3.dp, top = 3.dp),
                color = if (downloaded || queued || downloadInfo != null || chapter.read || chapter.last_page_read > 0)
                    InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                fontSize = 11.sp,
            )
            if (canDownload) {
                IconButton(
                    onClick = onDownload,
                    enabled = !downloaded && !queued,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        if (downloaded) Icons.Outlined.CheckCircleOutline else if (queued) Icons.Outlined.HourglassEmpty else Icons.Outlined.FileDownload,
                        contentDescription = when {
                            downloaded -> "Downloaded ${chapter.name}"
                            queued -> "Queued ${chapter.name}"
                            else -> "Download ${chapter.name}"
                        },
                        modifier = Modifier.size(19.dp),
                        tint = if (queued) InkShelf2Colors.Muted else InkShelf2Colors.AccentLight,
                    )
                }
            }
        }
    }
}

@Composable
internal fun InkShelf2IssueListRow(
    manga: Manga,
    chapter: Chapter,
    anchorIssueNumber: Int?,
    onClick: () -> Unit,
    canDownload: Boolean = false,
    downloaded: Boolean = false,
    queued: Boolean = false,
    onDownload: () -> Unit = {},
    downloadInfo: InkShelf2IssueDownloadInfo? = null,
    waitingForWifi: Boolean = false,
    downloadsPaused: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IssueArtwork(manga, chapter, anchorIssueNumber, Modifier.width(56.dp).aspectRatio(0.71f))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                chapter.name,
                color = InkShelf2Colors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                downloadStatusText(chapter, downloaded, downloadInfo, waitingForWifi, downloadsPaused),
                color = if (downloaded || queued || downloadInfo != null || chapter.read || chapter.last_page_read > 0)
                    InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                fontSize = 11.sp,
            )
            if (downloadInfo?.status == Download.State.DOWNLOADING) {
                Spacer(Modifier.height(5.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { downloadInfo.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = InkShelf2Colors.AccentLight,
                    trackColor = InkShelf2Colors.Border,
                )
            }
        }
        if (canDownload) {
            IconButton(
                onClick = onDownload,
                enabled = !downloaded && !queued,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (downloaded) Icons.Outlined.CheckCircleOutline else if (queued) Icons.Outlined.HourglassEmpty else Icons.Outlined.FileDownload,
                    contentDescription = when {
                        downloaded -> "Downloaded ${chapter.name}"
                        queued -> "Queued ${chapter.name}"
                        else -> "Download ${chapter.name}"
                    },
                    tint = if (queued) InkShelf2Colors.Muted else InkShelf2Colors.AccentLight,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Read ${chapter.name}",
            tint = InkShelf2Colors.AccentLight,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun issueStatus(chapter: Chapter): String = when {
    chapter.read -> "Read"
    chapter.last_page_read > 0 -> "Continue reading"
    else -> "Tap to read"
}
