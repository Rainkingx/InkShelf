package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.OpenInNew
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An editorial link shelf, not a web scraper. Only an RSS-supplied headline,
 * optional feed thumbnail, source and date are displayed. Full stories remain
 * with the original publishers, opened using their HTTPS article links.
 */
internal data class InkShelf2PulseArticle(
    val title: String,
    val url: String,
    val publisher: String,
    val publishedAt: Long,
    val imageUrl: String?,
)

internal object InkShelf2PulseFeed {
    private const val PREFS = "inkshelf2_pulse"
    private const val TTL_MS = 45 * 60 * 1000L
    private const val CACHE_LIFETIME_MS = 48 * 60 * 60 * 1000L

    private data class Publisher(val name: String, val host: String, val feed: String)

    private val publishers = listOf(
        Publisher("AIPT Comics", "aiptcomics.com", "https://aiptcomics.com/category/comic-books/feed/"),
        Publisher("The Beat", "comicsbeat.com", "https://www.comicsbeat.com/feed/"),
    )

    private val skipTerms = listOf(
        "layoff", "bankruptcy", "lawsuit", "allegation", "scandal",
        "cutbacks", "controversy", "arrested", "misconduct",
    )

    suspend fun cached(context: Context): List<InkShelf2PulseArticle> = withContext(Dispatchers.IO) {
        readCached(context)
    }

    suspend fun refresh(context: Context): List<InkShelf2PulseArticle> = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val existing = readCached(context)
        val checkedAt = prefs.getLong("checked_at", 0L)
        if (now - checkedAt in 0L until TTL_MS) return@withContext existing

        // Cache network failures too, avoiding repeated requests on each
        // navigation to Home when a publisher is unreachable.
        prefs.edit().putLong("checked_at", now).apply()
        val entries = publishers.flatMap { publisher ->
            try {
                loadPublisher(publisher)
            } catch (_: Exception) {
                emptyList()
            }
        }.filterNot { article ->
            val title = article.title.lowercase(Locale.ROOT)
            skipTerms.any { it in title }
        }.distinctBy { it.url }.sortedByDescending { it.publishedAt }.take(24)

        if (entries.isNotEmpty()) {
            val output = JSONArray()
            entries.forEach { article ->
                output.put(JSONObject().apply {
                    put("title", article.title)
                    put("url", article.url)
                    put("publisher", article.publisher)
                    put("publishedAt", article.publishedAt)
                    put("imageUrl", article.imageUrl)
                })
            }
            prefs.edit().putString("articles", output.toString()).putLong("saved_at", now).apply()
            entries
        } else existing
    }

    fun openArticle(context: Context, articleUrl: String) {
        if (!isApprovedArticleLink(articleUrl)) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(context, "No browser available to open the article", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readCached(context: Context): List<InkShelf2PulseArticle> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong("saved_at", 0L)
        val age = System.currentTimeMillis() - savedAt
        if (savedAt == 0L || age !in 0L until CACHE_LIFETIME_MS) return emptyList()
        return try {
            val rows = JSONArray(prefs.getString("articles", "[]") ?: "[]")
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val url = row.optString("url")
                val title = row.optString("title")
                if (title.isBlank() || !isApprovedArticleLink(url)) return@mapNotNull null
                InkShelf2PulseArticle(
                    title = title,
                    url = url,
                    publisher = row.optString("publisher"),
                    publishedAt = row.optLong("publishedAt"),
                    imageUrl = row.optString("imageUrl").takeIf { validHttps(it) },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadPublisher(publisher: Publisher): List<InkShelf2PulseArticle> {
        val connection = URL(publisher.feed).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6_500
            connection.readTimeout = 6_500
            connection.setRequestProperty("User-Agent", "InkShelf/2.0 comic RSS reader")
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            if (connection.responseCode !in 200..299) return emptyList()

            val result = mutableListOf<InkShelf2PulseArticle>()
            connection.inputStream.buffered().use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                var insideItem = false
                var title = ""
                var link = ""
                var date = ""
                var image: String? = null
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && result.size < 20) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val rawName = parser.name.orEmpty().lowercase(Locale.ROOT)
                            val tag = rawName.substringAfterLast(':')
                            if (tag == "item") {
                                insideItem = true
                                title = ""
                                link = ""
                                date = ""
                                image = null
                            } else if (insideItem) {
                                when (tag) {
                                    "title" -> title = parser.nextText().trim()
                                    "link" -> link = parser.nextText().trim()
                                    "pubdate", "published" -> date = parser.nextText().trim()
                                    "thumbnail", "enclosure", "content" -> {
                                        val imageCandidate = parser.getAttributeValue(null, "url")
                                        val type = parser.getAttributeValue(null, "type").orEmpty()
                                        if (image == null && validHttps(imageCandidate) &&
                                            (tag == "thumbnail" || type.startsWith("image/") ||
                                                imageCandidate.orEmpty().substringBefore('?')
                                                    .endsWith(".jpg", true) ||
                                                imageCandidate.orEmpty().substringBefore('?')
                                                    .endsWith(".png", true) ||
                                                imageCandidate.orEmpty().substringBefore('?')
                                                    .endsWith(".webp", true))
                                        ) {
                                            image = imageCandidate
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name.equals("item", ignoreCase = true) && insideItem) {
                                if (title.isNotBlank() && isApprovedArticleLink(link)) {
                                    result += InkShelf2PulseArticle(
                                        title = title,
                                        url = link,
                                        publisher = publisher.name,
                                        publishedAt = parseDate(date),
                                        imageUrl = image,
                                    )
                                }
                                insideItem = false
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDate(value: String): Long {
        if (value.isBlank()) return 0L
        for (pattern in listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                    .parse(value)?.time ?: 0L
            } catch (_: Exception) {
                // Try another RSS or Atom date format.
            }
        }
        return 0L
    }

    private fun isApprovedArticleLink(value: String): Boolean {
        if (!validHttps(value)) return false
        val host = Uri.parse(value).host.orEmpty().lowercase(Locale.ROOT)
        return publishers.any { host == it.host || host.endsWith("." + it.host) }
    }

    private fun validHttps(value: String?): Boolean {
        if (value == null || !value.startsWith("https://", ignoreCase = true)) return false
        return try { !Uri.parse(value).host.isNullOrBlank() } catch (_: Exception) { false }
    }
}

@Composable
internal fun InkShelf2PulsePreview(
    articles: List<InkShelf2PulseArticle>,
    loading: Boolean,
    onSeeAll: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    InkShelf2SectionHeading("InkShelf Pulse", onClick = onSeeAll, showSeeAll = true)
    Text(
        "Fresh stories from the comic world",
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        color = InkShelf2Colors.Muted,
        fontSize = 12.sp,
    )
    if (articles.isEmpty()) {
        Text(
            if (loading) "Finding the latest comic stories…" else "Comic news is unavailable right now. Tap See all to try again.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            color = InkShelf2Colors.Muted,
            fontSize = 12.sp,
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        articles.take(2).forEach { article ->
            Column(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.75f))
                    .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                    .clickable { onOpenArticle(article.url) }
                    .padding(9.dp),
            ) {
                InkShelf2PulseArtwork(article, height = 98)
                Spacer(Modifier.height(8.dp))
                Text(
                    article.publisher.uppercase(Locale.UK),
                    color = InkShelf2Colors.AccentLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    article.title,
                    color = InkShelf2Colors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 3,
                    minLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                )
                if (article.publishedAt > 0) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        SimpleDateFormat("d MMM", Locale.UK).format(Date(article.publishedAt)),
                        color = InkShelf2Colors.Muted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun InkShelf2PulseArtwork(article: InkShelf2PulseArticle, height: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(listOf(
                InkShelf2Colors.Surface,
                InkShelf2Colors.Accent.copy(alpha = 0.58f),
                InkShelf2Colors.SurfaceRaised,
            ))),
        contentAlignment = Alignment.Center,
    ) {
        if (article.imageUrl != null) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(
                        Color.Transparent,
                        InkShelf2Colors.Background.copy(alpha = 0.25f),
                    ))),
            )
        } else {
            Icon(
                Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = InkShelf2Colors.AccentLight,
                modifier = Modifier.size(34.dp),
            )
        }
        Icon(
            Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(7.dp).size(15.dp),
        )
    }
}

@Composable
internal fun InkShelf2PulseScreen(
    padding: PaddingValues,
    articles: List<InkShelf2PulseArticle>,
    loading: Boolean,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 6.dp,
            bottom = padding.calculateBottomPadding() + 22.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to Home", tint = InkShelf2Colors.Text)
                }
                Column {
                    Text("InkShelf Pulse", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = InkShelf2Colors.Text)
                    Text("Comic stories worth exploring", color = InkShelf2Colors.Muted, fontSize = 12.sp)
                }
            }
        }
        if (articles.isEmpty()) {
            item {
                Text(
                    if (loading) "Loading comic news…" else "News couldn't be loaded. Please check your connection and visit again.",
                    modifier = Modifier.padding(18.dp),
                    color = InkShelf2Colors.Muted,
                )
            }
        }
        items(articles, key = { it.url }) { article ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.72f))
                    .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.60f), RoundedCornerShape(14.dp))
                    .clickable { onOpenArticle(article.url) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(106.dp)) {
                    InkShelf2PulseArtwork(article, height = 95)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        article.publisher.uppercase(Locale.UK),
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        article.title,
                        color = InkShelf2Colors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                    if (article.publishedAt > 0) {
                        Text(
                            SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date(article.publishedAt)),
                            color = InkShelf2Colors.Muted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
        if (articles.isNotEmpty()) {
            item {
                Text(
                    "Headlines from AIPT Comics and The Beat. Tap an article to read it on the publisher's website.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
