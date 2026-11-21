package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
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
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCovers
import java.util.Locale

/** Artwork belongs to Metron; tapping always opens the original source chapter. */
@Composable
private fun IssueArtwork(manga: Manga, chapter: Chapter, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val issue = remember(chapter.name, chapter.chapter_number) {
        val name = chapter.name.lowercase(Locale.ROOT)
        if (listOf("annual", "special", "one-shot", "one shot", "free comic book day").any { it in name }) {
            null
        } else {
            val n = chapter.chapter_number
            if (n >= 1f && n <= 999f && n % 1f == 0f) n.toInt()
            else Regex("""^\s*#?\s*(\d{1,3})(?:\b|$)""").find(chapter.name)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..999 }
        }
    }
    var cover by remember(manga.source, manga.url, chapter.name, issue) {
        mutableStateOf<String?>(null)
    }
    var failed by remember(cover) { mutableStateOf(false) }
    DisposableEffect(context, manga.source, manga.url, chapter.name, issue) {
        var active = true
        if (issue != null) {
            InkShelfMetronCovers.requestIssueCover(context, manga, issue) { url ->
                if (active) cover = url
            }
        }
        onDispose { active = false }
    }

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
                issue?.let { "ISSUE #$it" } ?: "ISSUE",
                color = InkShelf2Colors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (cover != null && !failed) {
            AsyncImage(
                model = cover,
                contentDescription = "Cover for ${chapter.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        }
        if (chapter.read) {
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
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        IssueArtwork(manga, chapter, Modifier.fillMaxWidth().aspectRatio(0.71f))
        Text(
            chapter.name,
            modifier = Modifier.padding(start = 3.dp, end = 3.dp, top = 7.dp),
            color = InkShelf2Colors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            issueStatus(chapter),
            modifier = Modifier.padding(start = 3.dp, top = 3.dp),
            color = if (chapter.read || chapter.last_page_read > 0)
                InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
internal fun InkShelf2IssueListRow(manga: Manga, chapter: Chapter, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IssueArtwork(manga, chapter, Modifier.width(56.dp).aspectRatio(0.71f))
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
                issueStatus(chapter),
                color = if (chapter.read || chapter.last_page_read > 0)
                    InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
                fontSize = 11.sp,
            )
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
