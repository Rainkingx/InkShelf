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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Locale

/**
 * Full cached Discover shelf behind See All.
 * Unlike the on-page carousel this shows every currently available item
 * from the real Discover feed, never just the first five hero choices.
 */
@Composable
internal fun InkShelf2DiscoverShelfScreen(
    title: String,
    comics: List<Manga>,
    recommendationReasons: Map<String, String> = emptyMap(),
    trendingRanks: Map<String, Int> = emptyMap(),
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (Manga) -> Unit,
) {
    val results = comics.filter { !it.thumbnail_url.isNullOrBlank() }
        .distinctBy { "${it.source}:${it.url}" }
    var reasonDialog by remember { mutableStateOf<String?>(null) }
    var genreFilter by rememberSaveable(title) { mutableStateOf("Any genre") }
    var yearFilter by rememberSaveable(title) { mutableStateOf("Any year") }
    var publisherFilter by rememberSaveable(title) { mutableStateOf("Any publisher") }
    var sortOrder by rememberSaveable(title) { mutableStateOf("Featured") }

    val availableGenres = remember(results) {
        shelfGenres.keys.filter { label -> results.any { matchesShelfGenre(it, label) } }
    }
    val availableYears = remember(results) {
        results.mapNotNull(::shelfYear).distinct().sortedDescending().map(Int::toString)
    }
    val availablePublishers = remember(results) {
        results.mapNotNull(::shelfPublisher).distinct().sorted()
    }
    val sortOptions = if (results.any {
        "${it.source}:${it.url}" in trendingRanks
    }) listOf("Featured", "Trending first", "Newest edition", "Title A–Z")
    else listOf("Featured", "Newest edition", "Title A–Z")

    val visible = remember(results, genreFilter, yearFilter, publisherFilter, sortOrder, trendingRanks) {
        val filtered = results.filter { comic ->
            (genreFilter == "Any genre" || matchesShelfGenre(comic, genreFilter)) &&
                (yearFilter == "Any year" || shelfYear(comic)?.toString() == yearFilter) &&
                (publisherFilter == "Any publisher" || shelfPublisher(comic) == publisherFilter)
        }
        when (sortOrder) {
            "Trending first" -> filtered.sortedWith(
                compareBy<Manga> { trendingRanks["${it.source}:${it.url}"] ?: Int.MAX_VALUE }
                    .thenBy { it.title },
            )
            "Newest edition" -> filtered.sortedWith(
                compareByDescending<Manga> { shelfYear(it) ?: -1 }
                    .thenBy { it.title },
            )
            "Title A–Z" -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
            else -> filtered
        }
    }

    reasonDialog?.let { reason ->
        AlertDialog(
            onDismissRequest = { reasonDialog = null },
            title = { Text("Why this comic?", color = InkShelf2Colors.Text) },
            text = { Text(reason.substringAfter(" • ", reason), color = InkShelf2Colors.Text) },
            confirmButton = {
                TextButton(onClick = { reasonDialog = null }) {
                    Text("Close", color = InkShelf2Colors.AccentLight)
                }
            },
        )
    }

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
                    if (visible.size == results.size) "${results.size} comics"
                    else "${visible.size} of ${results.size} comics",
                    color = InkShelf2Colors.Muted,
                    fontSize = 12.sp,
                )
            }
        }

        if (results.isEmpty()) {
            InkShelf2EmptyShelf("No source-backed comics available for this shelf yet.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ShelfFilterMenu("Genre", genreFilter,
                    listOf("Any genre") + availableGenres) { genreFilter = it }
                if (availableYears.isNotEmpty()) {
                    ShelfFilterMenu("Year", yearFilter,
                        listOf("Any year") + availableYears) { yearFilter = it }
                }
                if (availablePublishers.isNotEmpty()) {
                    ShelfFilterMenu("Publisher", publisherFilter,
                        listOf("Any publisher") + availablePublishers) { publisherFilter = it }
                }
                ShelfFilterMenu("Sort", sortOrder, sortOptions) { sortOrder = it }
            }
            if (visible.isEmpty()) {
                InkShelf2EmptyShelf("No matches with these filters. Try another genre, year or publisher.")
            } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (recommendationReasons.isNotEmpty()) 2 else 3),
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
                    items = visible,
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
                        recommendationReasons["${comic.source}:${comic.url}"]
                            ?.takeIf(String::isNotBlank)
                            ?.let { reason ->
                                Text(
                                    reason.substringBefore(" • ", reason),
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = InkShelf2Colors.AccentLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { reasonDialog = reason }) {
                                    Text("Why this?", color = InkShelf2Colors.Muted, fontSize = 11.sp)
                                }
                            }
                    }
                }
            }
            }
        }
    }
}

/** Only explicit tags count as publisher metadata. Source site names and
 * character titles do not prove a publication credit.
 */
private fun shelfPublisher(manga: Manga): String? {
    val tags = listOfNotNull(manga.genre, manga.originalGenre).joinToString(" ").lowercase(Locale.ROOT)
    return when {
        "marvel comics" in tags -> "Marvel"
        "dc comics" in tags -> "DC"
        "image comics" in tags -> "Image"
        "dark horse" in tags -> "Dark Horse"
        "boom studios" in tags || "boom! studios" in tags -> "BOOM!"
        "idw publishing" in tags || "idw" in tags -> "IDW"
        else -> null
    }
}

private val shelfYearPattern = Regex(
    """[\(\[]((?:19|20)\d{2})(?:\s*[-–—]\s*(?:(?:19|20)\d{2}|ongoing|present)?)?\s*[\)\]]""",
    RegexOption.IGNORE_CASE,
)
private fun shelfYear(manga: Manga): Int? =
    shelfYearPattern.find(manga.title)?.groupValues?.getOrNull(1)?.toIntOrNull()

private val shelfGenres = mapOf(
    "Horror" to listOf("horror"),
    "Sci-Fi" to listOf("sci-fi", "sci fi", "science fiction", "cyberpunk"),
    "Fantasy" to listOf("fantasy", "magic"),
    "Mystery" to listOf("mystery", "detective"),
    "Crime" to listOf("crime", "noir"),
    "Superhero" to listOf("superhero", "superheroes"),
    "Adventure" to listOf("adventure"),
    "Drama" to listOf("drama"),
    "Action" to listOf("action"),
)
private fun matchesShelfGenre(manga: Manga, label: String): Boolean {
    val terms = shelfGenres[label] ?: return false
    val tags = listOfNotNull(manga.genre, manga.originalGenre).joinToString(" ").lowercase(Locale.ROOT)
    return terms.any { it in tags }
}

@Composable
private fun ShelfFilterMenu(
    label: String,
    selection: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                if (selection.startsWith("Any ") || selection == "Featured") label
                    else "$label: $selection",
                color = InkShelf2Colors.AccentLight,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
