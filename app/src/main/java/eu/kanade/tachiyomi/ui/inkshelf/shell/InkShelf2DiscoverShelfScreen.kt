package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import eu.kanade.tachiyomi.data.database.models.Manga

/**
 * Full cached Discover shelf behind See All.
 * Unlike the on-page carousel this shows every currently available item
 * from the real Discover feed, never just the first five hero choices.
 */
@Composable
internal fun InkShelf2DiscoverShelfScreen(
    title: String,
    comics: List<Manga>,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (Manga) -> Unit,
) {
    val results = comics.filter { !it.thumbnail_url.isNullOrBlank() }
        .distinctBy { "${it.source}:${it.url}" }

    Column(modifier = Modifier.fillMaxSize().inkShelf2Atmosphere()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(top = padding.calculateTopPadding(), start = 6.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Back to Discover",
                    tint = InkShelf2Colors.Text,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                InkShelf2PageHeading(title)
                Text(
                    "${results.size} comics",
                    color = InkShelf2Colors.Muted,
                    fontSize = 12.sp,
                )
            }
        }

        if (results.isEmpty()) {
            InkShelf2EmptyShelf("This shelf has no cached recommendations yet. Check back after Discover refreshes.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 12.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items = results,
                    key = { "${it.source}:${it.url}" },
                ) { comic ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onOpen(comic) },
                    ) {
                        val shape = RoundedCornerShape(9.dp)
                        AsyncImage(
                            model = comic,
                            contentDescription = comic.title,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.71f)
                                .clip(shape)
                                .background(InkShelf2Colors.SurfaceRaised)
                                .border(
                                    width = 1.dp,
                                    color = InkShelf2Colors.Border.copy(alpha = 0.7f),
                                    shape = shape,
                                ),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.padding(top = 3.dp))
                        Text(
                            comic.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
