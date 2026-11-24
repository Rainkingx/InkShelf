package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal data class InkShelf2FeaturedGuide(val label: String, val guide: InkShelf2Guide)

/**
 * Match known crossovers to genuine Metron community reading lists.
 * No invented reading lists or image URLs are used. Ambiguous suffixes
 * (e.g. Civil War II) cannot masquerade as a requested featured event.
 */
internal object InkShelf2FeaturedGuides {
    private data class Event(val title: String, val publisher: String)
    private val favourites = listOf(
        Event("Civil War", "marvel"),
        Event("Secret Wars", "marvel"),
        Event("Absolute Power", "dc"),
        Event("Knightfall", "dc"),
        Event("Crisis on Infinite Earths", "dc"),
    )
    private fun find(event: Event, candidates: List<InkShelf2Guide>): InkShelf2Guide? =
        candidates.asSequence()
            .filter { it.publisher.isBlank() || it.publisher.equals(event.publisher, true) }
            // The catalogue knows Secret Wars (1984) and (2015) are
            // different events. Never feature an undated/ambiguous version.
            .filter { InkShelf2GuideFocus.identify(it)?.title == event.title }
            .sortedByDescending { it.rating }
            .firstOrNull()

    fun match(candidates: List<InkShelf2Guide>): List<InkShelf2FeaturedGuide> =
        favourites.mapNotNull { event ->
            find(event, candidates)?.let { InkShelf2FeaturedGuide(event.title, it) }
        }.distinctBy { it.guide.id }

    suspend fun load(
        context: Context,
        catalogue: List<InkShelf2Guide>,
        onUpdate: (List<InkShelf2FeaturedGuide>) -> Unit,
    ) {
        val found = catalogue.toMutableList()
        var featured = match(found)
        if (featured.isNotEmpty()) onUpdate(featured)

        // The initial catalogue is only one page from each publisher.
        // Search for missing events individually, using the existing
        // authenticated, rate-limited and cached Metron search function.
        for (event in favourites) {
            if (featured.any { it.label == event.title }) continue
            val fetched = try {
                InkShelf2GuidesData.search(context, event.title, event.publisher)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }
            if (fetched.isNotEmpty()) {
                found.addAll(fetched)
                featured = match(found)
                if (featured.isNotEmpty()) onUpdate(featured)
            }
        }

        // Fill remaining hero slots only with curated modern or landmark
        // events, never unrelated older lists from a generic API page.
        if (featured.size < favourites.size) {
            val exclude = featured.map { it.guide.id }.toSet()
            val extras = InkShelf2GuideFocus.choose(found)
                .filterNot { it.id in exclude }
                .take(favourites.size - featured.size)
                .map { InkShelf2FeaturedGuide(it.name, it) }
            featured = featured + extras
        }
        onUpdate(featured)
    }
}

/** Swipable, auto-advancing event cards with a manual pager indicator. */
@Composable
internal fun InkShelf2FeaturedGuidesHero(
    featured: List<InkShelf2FeaturedGuide>,
    started: Set<Int>,
    onOpenGuide: (InkShelf2Guide) -> Unit,
) {
    if (featured.isEmpty()) return
    val pager = rememberPagerState(pageCount = { featured.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(featured.map { it.guide.id }) {
        if (featured.size > 1) {
            while (true) {
                delay(6_500)
                if (!pager.isScrollInProgress) {
                    pager.animateScrollToPage((pager.currentPage + 1) % featured.size)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        HorizontalPager(
            state = pager,
            pageSpacing = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { index ->
            val item = featured[index]
            InkShelf2FeaturedGuideSlide(
                item = item,
                isStarted = item.guide.id in started,
                onOpen = { onOpenGuide(item.guide) },
            )
        }
        if (featured.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                featured.indices.forEach { index ->
                    val selected = pager.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(if (selected) 25.dp else 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) InkShelf2Colors.AccentLight
                                else InkShelf2Colors.Muted.copy(alpha = 0.40f),
                            )
                            .clickable {
                                scope.launch { pager.animateScrollToPage(index) }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun InkShelf2FeaturedGuideSlide(
    item: InkShelf2FeaturedGuide,
    isStarted: Boolean,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    var cover by remember(item.guide.id) { mutableStateOf(item.guide.imageUrl) }
    LaunchedEffect(item.guide.id, item.guide.imageUrl) {
        cover = try {
            InkShelf2GuidesData.coverUrl(context, item.guide)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            item.guide.imageUrl
        }
    }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(265.dp)
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF451469), Color(0xFF1D103A), Color(0xFF10101D)),
                ),
            )
            .border(1.dp, InkShelf2Colors.AccentLight.copy(alpha = 0.55f), shape)
            .clickable { onOpen() },
    ) {
        // Genuine Metron image, clipped to the right so the left headline
        // always remains legible, even for brightly coloured comic covers.
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = "Artwork for " + item.guide.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .fillMaxWidth(0.75f).fillMaxHeight(),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = InkShelf2Colors.AccentLight.copy(alpha = 0.20f),
                    radius = size.width * 0.42f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.89f, size.height * 0.52f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF11091E),
                        0.47f to Color(0xFF11091E).copy(alpha = 0.94f),
                        0.72f to Color(0xFF11091E).copy(alpha = 0.37f),
                        1f to Color(0xFF11091E).copy(alpha = 0.16f),
                    ),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xFF0C0718).copy(alpha = 0.65f)),
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(17.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "FEATURED READING GUIDE",
                    color = InkShelf2Colors.AccentLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Text(
                    item.label + (InkShelf2GuideFocus.identify(item.guide)
                        ?.takeUnless { item.label.contains(it.year.toString()) }
                        ?.let { " (" + it.year + ")" } ?: ""),
                    color = Color.White,
                    fontSize = 27.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.77f),
                )
                Text(
                    if (item.guide.publisher.equals("marvel", true)) "Marvel crossover reading order"
                    else if (item.guide.publisher.equals("dc", true)) "DC crossover reading order"
                    else "Crossovers and story arcs in the right order",
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(0.79f),
                )
            }
            InkShelf2ActionButton(
                label = if (isStarted) "Resume Guide" else "Open Guide",
                modifier = Modifier.width(153.dp),
                onClick = onOpen,
            )
        }
    }
}
