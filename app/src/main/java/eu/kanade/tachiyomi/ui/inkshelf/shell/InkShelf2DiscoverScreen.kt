package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * New Discover destination built from the approved mock-up.
 * Source management stays under its menu. Search queries installed J2K
 * extensions directly and never treats cached recommendations as search.
 */
@Composable
internal fun InkShelf2DiscoverScreen(
    discover: InkShelf2Engine.Discover?,
    padding: PaddingValues,
    onOpen: (Manga) -> Unit,
    sources: List<CatalogueSource>,
    onOpenSearchResult: (Manga) -> Unit,
    onManageSources: () -> Unit,
    onManageExtensions: () -> Unit,
    onManageRepositories: () -> Unit,
    onSeeAll: (String) -> Unit,
    searchFocusToken: Int = 0,
    initialSearchQuery: String = "",
) {
    val engine = remember { InkShelf2SourcesEngine() }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var menuOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var activeQuery by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<InkShelf2SourcesEngine.GlobalSearchResults?>(null) }
    var selectedCollection by rememberSaveable { mutableStateOf<String?>(null) }

    // First Back leaves search or an open collection, not the entire app.
    BackHandler(enabled = query.isNotBlank() || selectedCollection != null) {
        when {
            query.isNotBlank() -> {
                query = ""
                activeQuery = ""
                found = null
                error = null
                keyboard?.hide()
            }
            selectedCollection != null -> selectedCollection = null
        }
    }

    val sourceIds = sources.map { it.id }
    LaunchedEffect(searchFocusToken) {
        if (searchFocusToken > 0) {
            if (initialSearchQuery.isNotBlank()) {
                query = initialSearchQuery
            }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // Debounced live search; the keyboard's Search action also submits instantly.
    LaunchedEffect(query) {
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            activeQuery = ""
            found = null
            error = null
        } else {
            delay(550L)
            activeQuery = cleaned
        }
    }
    LaunchedEffect(activeQuery, sourceIds) {
        if (activeQuery.isBlank()) {
            found = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        found = null
        error = null
        try {
            found = engine.searchAcross(sources, activeQuery)
        } catch (failure: Exception) {
            // Cancellation is managed by LaunchedEffect when a new query arrives.
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error = failure.message ?: "Source search could not be completed."
        } finally {
            searching = false
        }
    }

    val trending = discover?.trending.orEmpty().filter { !it.thumbnail_url.isNullOrBlank() }
    val heroTrending = trending.take(5)
    val newest = discover?.newest.orEmpty().filter { !it.thumbnail_url.isNullOrBlank() }
    val recommended = discover?.recommended.orEmpty().filter { !it.thumbnail_url.isNullOrBlank() }
    val picks = (recommended + trending + newest)
        .distinctBy { "${it.source}:${it.url}" }
    val curated: List<Pair<String, List<Manga>>> = remember(picks) {
        listOf(
            "X-Men Essentials" to picks.filter { comic ->
                listOf("x-men", "wolverine", "mutant", "deadpool", "uncanny")
                    .any { comic.title.contains(it, ignoreCase = true) }
            },
            "Cosmic Epics" to picks.filter { comic ->
                listOf("thanos", "silver surfer", "guardians", "cosmic", "nova", "galactus")
                    .any { comic.title.contains(it, ignoreCase = true) }
            },
            "Indie Spotlight" to picks.filter { comic ->
                listOf("spawn", "saga", "invincible", "walking dead", "radiant black", "monstress")
                    .any { comic.title.contains(it, ignoreCase = true) }
            },
        ).filter { it.second.isNotEmpty() }
    }
    val heroPager = rememberPagerState(pageCount = { heroTrending.size.coerceAtLeast(1) })

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 14.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(key = "discover-header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkShelf2PageHeading("Discover Comics", modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Sources and extension menu",
                                tint = InkShelf2Colors.Text,
                                modifier = Modifier.size(25.dp),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Browse Sources") },
                                leadingIcon = { Icon(Icons.Outlined.AutoStories, null) },
                                onClick = { menuOpen = false; onManageSources() },
                            )
                            DropdownMenuItem(
                                text = { Text("Manage Extensions") },
                                leadingIcon = { Icon(Icons.Outlined.Extension, null) },
                                onClick = { menuOpen = false; onManageExtensions() },
                            )
                            DropdownMenuItem(
                                text = { Text("Extension Repositories") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menuOpen = false; onManageRepositories() },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkShelf2SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholderText = "Search all comic sources",
                        onSearch = {
                            activeQuery = query.trim().takeIf { it.length >= 2 }.orEmpty()
                            keyboard?.hide()
                        },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = ""; activeQuery = ""; found = null }) {
                            Icon(Icons.Outlined.Close, "Clear search", tint = InkShelf2Colors.Text)
                        }
                    }
                }
            }
        }

        if (query.isNotBlank()) {
            item(key = "search-summary") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Search Installed Sources", color = InkShelf2Colors.Text,
                        fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    when {
                        sources.isEmpty() -> Text(
                            "No enabled sources. Open the ⋮ menu to install or enable comic extensions.",
                            color = InkShelf2Colors.Muted, fontSize = 13.sp,
                        )
                        query.trim().length < 2 -> Text(
                            "Enter at least 2 characters to search your extensions.",
                            color = InkShelf2Colors.Muted, fontSize = 13.sp,
                        )
                        searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                color = InkShelf2Colors.AccentLight,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("Searching ${sources.size} comic sources…",
                                color = InkShelf2Colors.Muted, fontSize = 12.sp)
                        }
                        error != null -> Text(error.orEmpty(), color = InkShelf2Colors.Muted, fontSize = 12.sp)
                        found != null -> {
                            val result = found!!
                            Text(
                                "${result.results.size} comics from ${result.searchedSources - result.failedSources}" +
                                    " of ${result.searchedSources} sources",
                                color = InkShelf2Colors.Muted,
                                fontSize = 12.sp,
                            )
                            if (result.failedSources > 0) {
                                Text(
                                    "${result.failedSources} source(s) did not respond; other results are shown.",
                                    color = InkShelf2Colors.AccentLight,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
            val comics = found?.results.orEmpty()
            if (!searching && error == null && comics.isEmpty() && query.length >= 2 && found != null) {
                item(key = "search-empty") {
                    InkShelf2EmptyShelf("No matches from your installed sources. Try a different title.")
                }
            }
            items(
                items = comics.chunked(3),
                key = { row -> row.first().manga.source.toString() + ":" + row.first().manga.url },
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    row.forEach { result ->
                        Column(
                            modifier = Modifier.weight(1f)
                                .clickable { onOpenSearchResult(result.manga) },
                        ) {
                            AsyncImage(
                                model = result.manga,
                                contentDescription = "Open ${result.manga.title}",
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.71f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(
                                        1.dp,
                                        InkShelf2Colors.Border.copy(alpha = 0.7f),
                                        RoundedCornerShape(9.dp),
                                    ),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                result.manga.title,
                                color = InkShelf2Colors.Text,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                result.sourceName,
                                color = InkShelf2Colors.AccentLight,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            // Fixed item indices keep mock-up category shortcuts functional.
            item(key = "trending-hero") {
                if (heroTrending.isNotEmpty()) {
                    HorizontalPager(
                        state = heroPager,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 10.dp,
                    ) { index ->
                        val comic = heroTrending[index]
                        Box(
                            modifier = Modifier.fillMaxWidth().height(194.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(InkShelf2Colors.SurfaceRaised)
                                .clickable { onOpen(comic) },
                        ) {
                            AsyncImage(
                                model = comic,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = 0.8f,
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xEE10081C),
                                            Color(0xB511061C),
                                            Color(0x221D0525),
                                        ),
                                    ),
                                ),
                            )
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart)
                                    .fillMaxWidth(0.88f)
                                    .padding(17.dp),
                            ) {
                                Text("TRENDING NOW", color = InkShelf2Colors.AccentLight,
                                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    comic.title,
                                    color = Color.White,
                                    fontSize = 23.sp,
                                    lineHeight = 25.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(5.dp))
                                Text("Explore comic", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(heroTrending.size) { index ->
                            Box(
                                modifier = Modifier.padding(horizontal = 3.dp)
                                    .width(if (index == heroPager.currentPage) 16.dp else 6.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (index == heroPager.currentPage) InkShelf2Colors.AccentLight
                                        else InkShelf2Colors.Border,
                                    ),
                            )
                        }
                    }
                } else {
                    InkShelf2EmptyShelf("Trending artwork will appear when recommendations refresh.")
                }
            }
            item(key = "shortcuts") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val shortcuts = listOf(
                        Triple("Trending", Icons.Outlined.LocalFireDepartment, 7),
                        Triple("New This Week", Icons.Outlined.NewReleases, 6),
                        Triple("Top Picks", Icons.Outlined.AutoAwesome, 5),
                        Triple("Collections", Icons.Outlined.AutoStories, 3),
                    )
                    shortcuts.forEach { (label, image, index) ->
                        Column(
                            modifier = Modifier.weight(1f).clickable {
                                scope.launch { scroll.animateScrollToItem(index) }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.size(45.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(InkShelf2Colors.SurfaceRaised)
                                    .border(
                                        1.dp,
                                        InkShelf2Colors.Border.copy(alpha = 0.75f),
                                        RoundedCornerShape(13.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(image, contentDescription = null,
                                    modifier = Modifier.size(23.dp), tint = InkShelf2Colors.AccentLight)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(label, color = InkShelf2Colors.Text, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item(key = "collections") {
                InkShelf2SectionHeading("Curated Collections", onClick = null)
                Spacer(Modifier.height(8.dp))
                if (curated.isEmpty()) {
                    InkShelf2EmptyShelf("Collections will appear when suitable comics are available.")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(curated, key = { it.first }) { (title, matches) ->
                            Box(
                                modifier = Modifier.width(160.dp).height(112.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .clickable { selectedCollection = title },
                            ) {
                                AsyncImage(
                                    model = matches.first(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x3306000A), Color(0xF0080613)),
                                        ),
                                    ),
                                )
                                Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                    Text(title, color = Color.White, fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text("${matches.size} comics", color = InkShelf2Colors.AccentLight,
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "selected-collection") {
                val selection = curated.firstOrNull { it.first == selectedCollection }
                if (selection != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selection.first, color = InkShelf2Colors.Text, fontSize = 17.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedCollection = null }) {
                            Text("Close", color = InkShelf2Colors.AccentLight)
                        }
                    }
                    DiscoverShelf(selection.second, onOpen)
                }
            }
            item(key = "recommended") {
                InkShelf2SectionHeading("Recommended for You", onClick = null)
                Spacer(Modifier.height(7.dp))
                DiscoverShelf(recommended.take(20), onOpen)
            }
            item(key = "new") {
                DiscoverSeeAllHeading("New This Week", onClick = { onSeeAll("New This Week") })
                Spacer(Modifier.height(7.dp))
                DiscoverShelf(newest.take(20), onOpen)
            }
            item(key = "trending-shelf") {
                DiscoverSeeAllHeading("Trending Right Now", onClick = { onSeeAll("Trending Right Now") })
                Spacer(Modifier.height(7.dp))
                DiscoverShelf(trending.take(20), onOpen)
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

/** Visible See all action for Discover shelves, without changing Home's approved arrows. */
@Composable
private fun DiscoverSeeAllHeading(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = InkShelf2Colors.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("See all", color = InkShelf2Colors.AccentLight, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(3.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "See all " + title,
                tint = InkShelf2Colors.AccentLight,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DiscoverShelf(comics: List<Manga>, onOpen: (Manga) -> Unit) {
    if (comics.isEmpty()) {
        InkShelf2EmptyShelf("No comics available in this shelf yet.")
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(comics, key = { "${it.source}:${it.url}" }) { comic ->
            Column(
                modifier = Modifier.width(105.dp).clickable { onOpen(comic) },
            ) {
                AsyncImage(
                    model = comic,
                    contentDescription = comic.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.71f)
                        .clip(RoundedCornerShape(9.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    comic.title,
                    color = InkShelf2Colors.Text,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
