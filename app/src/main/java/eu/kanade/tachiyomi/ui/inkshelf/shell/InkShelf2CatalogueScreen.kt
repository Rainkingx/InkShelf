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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource

/**
 * Entirely new Compose reader catalogue for an installed J2K extension.
 * Uses that extension's actual Popular, Latest and Search implementations.
 */
@Composable
internal fun InkShelf2CatalogueScreen(
    source: CatalogueSource?,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenComic: (Manga) -> Unit,
) {
    if (source == null) {
        Column(
            modifier = Modifier.fillMaxSize().inkShelf2Atmosphere().padding(padding).padding(16.dp),
        ) {
            InkShelf2PageHeading("Source unavailable")
            InkShelf2EmptyShelf("The source is no longer installed or enabled.")
            InkShelf2ActionButton("Back to Sources", onClick = onBack,
                modifier = Modifier.fillMaxWidth())
        }
        return
    }

    val engine = remember { InkShelf2SourcesEngine() }
    var draft by rememberSaveable(source.id) { mutableStateOf("") }
    var search by rememberSaveable(source.id) { mutableStateOf("") }
    var latest by rememberSaveable(source.id) { mutableStateOf(false) }
    var page by rememberSaveable(source.id) { mutableIntStateOf(1) }
    var comics by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }

    fun submitSearch() {
        search = draft.trim()
        comics = emptyList()
        page = 1
    }
    LaunchedEffect(source.id, search, latest, page) {
        loading = true
        error = null
        try {
            val response = engine.page(source, page, search, latest)
            comics = if (page == 1) {
                response.comics
            } else {
                (comics + response.comics).distinctBy { it.source.toString() + ":" + it.url }
            }
            hasMore = response.hasMore && response.comics.isNotEmpty()
        } catch (failure: Exception) {
            error = failure.message ?: "This source is not responding."
            hasMore = false
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().inkShelf2Atmosphere().padding(padding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, "Back to Sources", tint = InkShelf2Colors.Text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(source.name, color = InkShelf2Colors.Text, fontSize = 21.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${source.lang.uppercase()} extension catalogue",
                    color = InkShelf2Colors.Muted, fontSize = 11.sp)
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search this comic source") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkShelf2ActionButton(
                if (search.isBlank() && !latest) "Popular ✓" else "Popular",
                onClick = { draft = ""; search = ""; latest = false; page = 1; comics = emptyList() },
                outlined = !(search.isBlank() && !latest),
                modifier = Modifier.weight(1f),
            )
            InkShelf2ActionButton(
                if (search.isBlank() && latest) "Latest ✓" else "Latest",
                onClick = { draft = ""; search = ""; latest = true; page = 1; comics = emptyList() },
                enabled = source.supportsLatest,
                outlined = !(search.isBlank() && latest),
                modifier = Modifier.weight(1f),
            )
            InkShelf2ActionButton(
                "Search", onClick = { submitSearch() }, outlined = search.isBlank(),
                modifier = Modifier.weight(1f),
            )
        }
        if (search.isNotBlank()) {
            Text("Search results for “$search”",
                color = InkShelf2Colors.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(comics, key = { it.source.toString() + ":" + it.url }) { manga ->
                Column(modifier = Modifier.fillMaxWidth().clickable { onOpenComic(manga) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.71f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(InkShelf2Colors.SurfaceRaised)
                            .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.7f),
                                RoundedCornerShape(9.dp)),
                    ) {
                        AsyncImage(
                            model = manga, contentDescription = "Open ${manga.title}",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        )
                    }
                    Text(manga.title, color = InkShelf2Colors.Text,
                        modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp,
                        maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis)
                }
            }
            if (loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = InkShelf2Colors.AccentLight,
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text("Loading comics…", color = InkShelf2Colors.Muted, fontSize = 12.sp)
                    }
                }
            }
            error?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InkShelf2EmptyShelf(message)
                }
            }
            if (comics.isEmpty() && !loading && error == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InkShelf2EmptyShelf("No comics found for this selection.")
                }
            }
            if (hasMore && !loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InkShelf2ActionButton(
                        "Load More Comics", onClick = { page++ },
                        outlined = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
