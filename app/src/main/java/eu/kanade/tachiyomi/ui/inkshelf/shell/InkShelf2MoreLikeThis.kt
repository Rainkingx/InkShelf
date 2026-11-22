package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.Locale

internal data class InkShelf2SimilarPick(
    val manga: Manga,
    val reason: String,
    val score: Int,
)

/**
 * Reuses the same real, source-backed Discover and Library Manga objects as
 * personal recommendations. This is a local relevance ranker, not a source
 * search, a duplicate catalogue, or a made-up list of comics.
 *
 * Never create Manga objects from Metron metadata: it is artwork/metadata only,
 * not a readable J2K extension entry.
 */
internal object InkShelf2MoreLikeThis {
    private val yearTag = Regex("""\s*[\(\[](?:19|20)\d{2}(?:\s*[-–]\s*(?:19|20)\d{2})?\s*[\)\]]""")
    private val titleNoise = setOf(
        "the", "and", "with", "from", "of", "for", "issue", "vol",
        "volume", "series", "comics", "comic", "collection", "complete",
        "edition", "deluxe", "new", "all", "one", "part",
    )
    private val genericGenres = setOf(
        "comic", "comics", "english", "en", "gb", "manga", "webtoon",
        "action", "adventure", "full color", "colour", "colored", "completed",
        "ongoing", "superhero", "superheroes",
    )
    private data class Theme(val label: String, val terms: List<String>)
    private val themes = listOf(
        Theme("X-Men universe", listOf("x-men", "x men", "wolverine", "x-force", "x force", "mutant", "magneto")),
        Theme("Spider-Man universe", listOf("spider-man", "spiderman", "spider-verse", "spider verse", "venom", "carnage")),
        Theme("Batman universe", listOf("batman", "gotham", "nightwing", "batgirl", "robin", "red hood")),
        Theme("Superman universe", listOf("superman", "supergirl", "krypton", "superboy", "action comics")),
        Theme("Avengers universe", listOf("avengers", "captain america", "iron man", "thor", "hulk", "black widow")),
        Theme("Fantastic Four universe", listOf("fantastic four", "silver surfer", "galactus", "doctor doom")),
        Theme("Star Wars universe", listOf("star wars", "darth vader", "jedi", "sith")),
        Theme("Invincible universe", listOf("invincible", "atom eve", "guarding the globe", "tech jacket")),
    )

    private fun normalized(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(yearTag, " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    private fun key(manga: Manga): String = normalized(manga.title)

    private fun titleTokens(manga: Manga): Set<String> =
        key(manga).split(" ")
            .filter { it.length >= 4 && it !in titleNoise && it.toIntOrNull() == null }
            .toSet()

    private fun genres(manga: Manga): Map<String, String> =
        (manga.getOriginalGenres().orEmpty() + manga.getGenres().orEmpty())
            .distinctBy(::normalized)
            .associateBy(::normalized)
            .filterKeys { it.length >= 4 && it !in genericGenres }

    private fun creators(manga: Manga): List<String> =
        listOfNotNull(manga.originalAuthor, manga.originalArtist, manga.author, manga.artist)
            .flatMap { it.split(",", ";", "&") }
            .map(::normalized)
            .filter { it.length >= 5 && it !in listOf("unknown", "various", "multiple") }
            .distinct()

    private fun themesOf(manga: Manga): List<Theme> {
        val text = listOfNotNull(
            manga.title, manga.originalGenre, manga.genre,
        ).joinToString(" ").lowercase(Locale.ROOT)
        return themes.filter { theme -> theme.terms.any { it in text } }
    }

    fun select(
        focus: Manga,
        candidates: List<Manga>,
        enabledSources: Set<Long>,
        limit: Int = 8,
    ): List<InkShelf2SimilarPick> {
        val focusKey = key(focus)
        if (focusKey.isBlank()) return emptyList()
        val focusGenres = genres(focus)
        val focusCreators = creators(focus)
        val focusTokens = titleTokens(focus)
        val focusThemes = themesOf(focus)

        return candidates.asSequence()
            .filter { it.source in enabledSources && it.url.isNotBlank() }
            .filter { !it.thumbnail_url.isNullOrBlank() }
            .filter { it.source != focus.source || it.url != focus.url }
            .filter { key(it).isNotBlank() && key(it) != focusKey }
            .mapNotNull { comic ->
                val commonGenre = genres(comic).entries.firstOrNull { it.key in focusGenres }
                val commonCreator = creators(comic).firstOrNull { it in focusCreators }
                val sharedTerms = titleTokens(comic).intersect(focusTokens)
                val commonTheme = themesOf(comic).firstOrNull { it in focusThemes }

                val score = (if (commonCreator != null) 75 else 0) +
                    (if (commonGenre != null) 50 else 0) +
                    (sharedTerms.size.coerceAtMost(2) * 35) +
                    (if (commonTheme != null) 35 else 0)
                // Show a related comic only when there is an explainable,
                // meaningful similarity; don't fill the shelf with random picks.
                if (score < 35) return@mapNotNull null

                val reason = when {
                    commonCreator != null -> "Shared creator"
                    commonTheme != null -> commonTheme.label
                    sharedTerms.isNotEmpty() -> "Related series"
                    commonGenre != null -> "Shared genre: ${commonGenre.value.take(32)}"
                    else -> return@mapNotNull null
                }
                InkShelf2SimilarPick(comic, reason, score)
            }
            // Multiple extensions can provide the same title. Keep only the
            // strongest actual entry rather than showing duplicate covers.
            .groupBy { key(it.manga) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.score } }
            .sortedWith(compareByDescending<InkShelf2SimilarPick> { it.score }.thenBy { it.manga.title })
            .take(limit)
            .toList()
    }
}

@Composable
internal fun InkShelf2MoreLikeThisShelf(
    picks: List<InkShelf2SimilarPick>,
    onOpen: (Manga) -> Unit,
) {
    if (picks.isEmpty()) return
    // Keep extra bottom breathing room for gesture/navigation curves on
    // smaller phones; only the cover artwork itself should be rounded/clipped.
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 40.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            InkShelf2PageHeading("More Like This")
            Spacer(Modifier.height(4.dp))
            Text(
                "Related comics available from your installed sources",
                color = InkShelf2Colors.Muted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(picks, key = { "${it.manga.source}:${it.manga.url}" }) { pick ->
                Column(
                    modifier = Modifier.width(112.dp)
                        .clickable { onOpen(pick.manga) }
                        .padding(bottom = 8.dp),
                ) {
                    AsyncImage(
                        model = pick.manga,
                        contentDescription = "Open ${pick.manga.title}",
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(0.71f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(InkShelf2Colors.SurfaceRaised)
                            .border(
                                1.dp,
                                InkShelf2Colors.Border.copy(alpha = 0.55f),
                                RoundedCornerShape(10.dp),
                            ),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pick.manga.title,
                        color = InkShelf2Colors.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        lineHeight = 15.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        pick.reason,
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 10.sp,
                        maxLines = 2,
                        lineHeight = 12.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
