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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga

/**
 * The actual three (or fewer) picks behind a weekday Home hero.
 * Displays the exact comics selected by InkShelf2Engine for that day;
 * no fabricated or unrelated filler comics are introduced here.
 */
@Composable
internal fun InkShelf2DailyPicksScreen(
    day: InkShelf2Engine.Day,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (Manga) -> Unit,
) {
    val comics = day.picks.distinctBy { "${it.source}:${it.url}" }
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 7.dp, end = 16.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = InkShelf2Colors.Text,
                    )
                }
                InkShelf2PageHeading("Daily Comic Picks")
            }
        }

        item(key = "hero") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .height(195.dp)
                    .clip(RoundedCornerShape(17.dp))
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
                            listOf(Color(0x120A0611), Color(0xE30A0611)),
                        ),
                    ),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(17.dp),
                ) {
                    InkShelf2ComicTitle(day.title)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "${comics.size} comic${if (comics.size == 1) "" else "s"} selected",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item(key = "intro") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "THE PICKS",
                    color = InkShelf2Colors.AccentLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Choose a comic to see its details and available issues.",
                    color = InkShelf2Colors.Muted,
                    fontSize = 12.sp,
                )
            }
        }

        if (comics.isEmpty()) {
            item(key = "empty") {
                InkShelf2EmptyShelf("No verified comics are available for this day's theme yet.")
            }
        }

        items(
            items = comics,
            key = { "${it.source}:${it.url}" },
        ) { comic ->
            val shape = RoundedCornerShape(13.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .clip(shape)
                    .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.72f))
                    .border(
                        1.dp,
                        InkShelf2Colors.Border.copy(alpha = 0.65f),
                        shape,
                    )
                    .clickable { onOpen(comic) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = comic,
                    contentDescription = comic.title,
                    modifier = Modifier.width(90.dp).aspectRatio(0.71f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        comic.title,
                        color = InkShelf2Colors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val genres = comic.getGenres().orEmpty().take(2)
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            genres.joinToString("  •  "),
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "View comic and issues",
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Open ${comic.title}",
                    modifier = Modifier.size(23.dp),
                    tint = InkShelf2Colors.AccentLight,
                )
            }
        }
    }
}
