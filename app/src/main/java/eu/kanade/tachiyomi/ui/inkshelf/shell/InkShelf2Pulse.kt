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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val isVideo: Boolean = false,
)


internal object InkShelf2PulseFeed {
    private const val PREFS = "inkshelf2_pulse"
    private const val TTL_MS = 45 * 60 * 1000L
    private const val CACHE_LIFETIME_MS = 48 * 60 * 60 * 1000L
    // A single in-flight refresh serves Home and Comic Details alike.
    private val refreshMutex = Mutex()

    private data class Publisher(val name: String, val host: String, val feed: String)
    private data class VideoChannel(val name: String, val id: String)

    private val publishers = listOf(
        Publisher("AIPT Comics", "aiptcomics.com", "https://aiptcomics.com/category/comic-books/feed/"),
        Publisher("The Beat", "comicsbeat.com", "https://www.comicsbeat.com/feed/"),
    )

    // These are the channels' publicly listed, stable YouTube channel IDs.
    // Official YouTube RSS feeds require no Google API key or account.
    private val videoChannels = listOf(
        VideoChannel("Near Mint Condition", "UCUX6kqTUFzQaKJKBaqzuNdg"),
        VideoChannel("ComicPop", "UCEqBMWd6aeCVLAUMEO5WqIw"),
        VideoChannel("Casually Comics", "UCjKC1V-BWoFRVLAmYeoPCWw"),
        VideoChannel("Variant Comics", "UC4kjDjhexSVuC8JWk4ZanFw"),
    )

    private val skipTerms = listOf(
        "layoff", "bankruptcy", "lawsuit", "allegation", "scandal",
        "cutbacks", "controversy", "arrested", "misconduct",
    )

    suspend fun cached(context: Context): List<InkShelf2PulseArticle> = withContext(Dispatchers.IO) {
        readCached(context)
    }

    suspend fun refresh(context: Context): List<InkShelf2PulseArticle> = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val existing = readCached(context)
        val checkedAt = prefs.getLong("checked_at", 0L)
        // A feed-version bump makes the first update fetch YouTube once, even
        // if the previous news-only version cached articles minutes ago.
        // Afterward, respect the TTL even when a channel is unavailable.
        if (now - checkedAt in 0L until TTL_MS && prefs.getInt("feed_version", 0) >= 2)
            return@withLock existing

        // Pull each independent feed concurrently so a slow/blocked source
        // does not delay every other publisher or video channel.
        val newsByPublisher = coroutineScope {
            publishers.map { publisher ->
                async(Dispatchers.IO) { safeLoad { loadPublisher(publisher) } }
            }.awaitAll()
        }
        val videosByChannel = coroutineScope {
            videoChannels.map { channel ->
                async(Dispatchers.IO) { safeLoad { loadVideos(channel) } }
            }.awaitAll()
        }

        // Round-robin by publisher: the two Home headlines normally show one
        // from each site, rather than letting a high-volume site dominate.
        val news = balance(newsByPublisher, 24)
        val videos = balance(videosByChannel, 12)
        val entries = (
            (if (news.isNotEmpty()) news else existing.filterNot { it.isVideo }) +
            (if (videos.isNotEmpty()) videos else existing.filter { it.isVideo })
        ).distinctBy { it.url }

        prefs.edit().putLong("checked_at", now).putInt("feed_version", 2).apply()
        if (entries.isNotEmpty()) {
            val output = JSONArray()
            entries.forEach { article ->
                output.put(JSONObject().apply {
                    put("title", article.title)
                    put("url", article.url)
                    put("publisher", article.publisher)
                    put("publishedAt", article.publishedAt)
                    put("imageUrl", article.imageUrl)
                    put("video", article.isVideo)
                })
            }
            prefs.edit().putString("articles", output.toString()).putLong("saved_at", now).apply()
        }
        if (entries.isNotEmpty()) InkShelf2PulseArchive.record(context, entries)
        entries
        }
    }

    fun openArticle(context: Context, url: String) {
        if (!isApprovedArticleLink(url)) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(context, "No app available to open the link", Toast.LENGTH_SHORT).show()
        }
    }

    private inline fun safeLoad(block: () -> List<InkShelf2PulseArticle>): List<InkShelf2PulseArticle> {
        return try {
            block().filterNot { article ->
                val title = article.title.lowercase(Locale.ROOT)
                skipTerms.any { it in title }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun balance(groups: List<List<InkShelf2PulseArticle>>, maxItems: Int): List<InkShelf2PulseArticle> {
        val ordered = groups.map { it.distinctBy { row -> row.url }.sortedByDescending { row -> row.publishedAt } }
        val output = mutableListOf<InkShelf2PulseArticle>()
        for (index in 0 until (ordered.maxOfOrNull { it.size } ?: 0)) {
            for (group in ordered) {
                if (index < group.size) output += group[index]
                if (output.size >= maxItems) return output
            }
        }
        return output
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
                    isVideo = row.optBoolean("video", false),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadPublisher(publisher: Publisher): List<InkShelf2PulseArticle> {
        val connection = openFeed(publisher.feed)
        try {
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
                                when {
                                    tag == "title" -> title = parser.nextText().trim()
                                    tag == "link" -> link = parser.nextText().trim()
                                    tag == "pubdate" || tag == "published" -> date = parser.nextText().trim()
                                    tag == "description" || tag == "encoded" || tag == "summary" -> {
                                        val body = parser.nextText()
                                        if (image == null) image = firstFeedImage(body)
                                    }
                                    tag == "thumbnail" || tag == "enclosure" ||
                                        tag == "content" || tag == "image" -> {
                                        val candidate = parser.getAttributeValue(null, "url")
                                            ?: parser.getAttributeValue(null, "href")
                                        val type = parser.getAttributeValue(null, "type").orEmpty()
                                        val medium = parser.getAttributeValue(null, "medium").orEmpty()
                                        val looksLikeImage = tag == "thumbnail" || tag == "image" ||
                                            type.startsWith("image/") || medium == "image" ||
                                            listOf(".jpg", ".jpeg", ".png", ".webp", ".avif").any {
                                                candidate.orEmpty().substringBefore('?').endsWith(it, ignoreCase = true)
                                            }
                                        if (image == null && looksLikeImage && validHttps(candidate)) {
                                            image = normaliseImageUrl(candidate.orEmpty())
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name.equals("item", ignoreCase = true) && insideItem) {
                                if (title.isNotBlank() && isApprovedArticleLink(link)) {
                                    result += InkShelf2PulseArticle(
                                        title = cleanText(title),
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

    private fun loadVideos(channel: VideoChannel): List<InkShelf2PulseArticle> {
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channel.id
        val connection = openFeed(feedUrl)
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val videos = mutableListOf<InkShelf2PulseArticle>()
            connection.inputStream.buffered().use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                var insideEntry = false
                var title = ""
                var videoId = ""
                var published = ""
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && videos.size < 12) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name.orEmpty().substringAfterLast(':').lowercase(Locale.ROOT)
                            if (tag == "entry") {
                                insideEntry = true
                                title = ""
                                videoId = ""
                                published = ""
                            } else if (insideEntry) {
                                when (tag) {
                                    "title" -> title = parser.nextText().trim()
                                    "videoid" -> videoId = parser.nextText().trim()
                                    "published" -> published = parser.nextText().trim()
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name.equals("entry", ignoreCase = true) && insideEntry) {
                                if (title.isNotBlank() && validVideoId(videoId)) {
                                    videos += InkShelf2PulseArticle(
                                        title = cleanText(title),
                                        url = "https://www.youtube.com/watch?v=" + videoId,
                                        publisher = channel.name,
                                        publishedAt = parseDate(published),
                                        imageUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg",
                                        isVideo = true,
                                    )
                                }
                                insideEntry = false
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            return videos
        } finally {
            connection.disconnect()
        }
    }

    private fun openFeed(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 7_000
            setRequestProperty("User-Agent", "InkShelf/2.0 RSS reader")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
        }
    }

    // WordPress feeds often put a full <img> inside description or
    // content:encoded rather than providing a media:thumbnail element.
    // Read only the first publisher-supplied image; do not scrape web pages.
    private fun firstFeedImage(html: String): String? {
        val tags = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
        val sources = listOf("src", "data-src", "data-lazy-src", "data-original")
        for (tag in tags) {
            for (attr in sources) {
                val matcher = Regex("\\b" + attr + "\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                val candidate = matcher.find(tag.value)?.groupValues?.getOrNull(1) ?: continue
                val normalised = normaliseImageUrl(candidate)
                if (validHttps(normalised) &&
                    !normalised.contains("gravatar", ignoreCase = true) &&
                    !normalised.contains("pixel", ignoreCase = true) &&
                    !normalised.startsWith("data:", ignoreCase = true)
                ) return normalised
            }
        }
        return null
    }

    private fun normaliseImageUrl(value: String): String =
        value.trim().replace("&amp;", "&").replace("&#038;", "&")

    private fun cleanText(value: String): String =
        value.replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#8217;", "’").replace("&#8216;", "‘")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("<[^>]*>"), "").trim()

    private fun parseDate(value: String): Long {
        if (value.isBlank()) return 0L
        for (pattern in listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        )) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                if (pattern.endsWith("'Z'")) parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return parser.parse(value)?.time ?: 0L
            } catch (_: Exception) {
                // Try another RSS or Atom date format.
            }
        }
        return 0L
    }

    private fun validVideoId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{11}"))

    internal fun isApprovedArticleLink(value: String): Boolean {
        if (!validHttps(value)) return false
        val uri = Uri.parse(value)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        if (publishers.any { host == it.host || host.endsWith("." + it.host) }) return true
        if (host != "www.youtube.com" && host != "youtube.com") return false
        return uri.path == "/watch" && validVideoId(uri.getQueryParameter("v").orEmpty())
    }

    private fun validHttps(value: String?): Boolean {
        if (value == null || !value.startsWith("https://", ignoreCase = true)) return false
        return try { !Uri.parse(value).host.isNullOrBlank() } catch (_: Exception) { false }
    }
}

/**
 * Category selector on the full Pulse destination only.
 * Home remains a simpler news preview with a single large See all action.
 */
@Composable
private fun InkShelf2PulseTabs(
    showVideos: Boolean,
    onShowVideos: (Boolean) -> Unit,
) {
    val containerShape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(containerShape)
            .background(InkShelf2Colors.SurfaceRaised)
            .border(1.dp, InkShelf2Colors.Border, containerShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to "Articles", true to "Videos").forEach { (isVideo, label) ->
            val selected = showVideos == isVideo
            val pillShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier.weight(1f).height(38.dp)
                    .clip(pillShape)
                    .background(
                        if (selected) InkShelf2Colors.AccentLight.copy(alpha = 0.19f)
                        else Color.Transparent,
                    )
                    .border(
                        1.dp,
                        if (selected) InkShelf2Colors.AccentLight.copy(alpha = 0.76f)
                        else Color.Transparent,
                        pillShape,
                    )
                    .clickable { if (!selected) onShowVideos(isVideo) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) InkShelf2Colors.AccentLight else InkShelf2Colors.Text,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun InkShelf2PulsePreview(
    articles: List<InkShelf2PulseArticle>,
    loading: Boolean,
    onSeeAll: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    // Home stays a simple editorial teaser. The Articles and Videos selector
    // belongs only to the full Pulse destination behind the large CTA.
    val news = articles.filterNot { it.isVideo }.take(2)
    InkShelf2SectionHeading("The Pulse", onClick = null)
    Text(
        "Comic-book news, reviews & videos",
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        color = InkShelf2Colors.Muted,
        fontSize = 12.sp,
    )
    if (news.isEmpty()) {
        Text(
            if (loading) "Finding the latest comic-book news…"
            else "Comic-book news is unavailable right now. Open The Pulse to try again.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            color = InkShelf2Colors.Muted,
            fontSize = 12.sp,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            news.forEach { article ->
                Column(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.75f))
                        .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                        .clickable { onOpenArticle(article.url) }
                        .padding(9.dp),
                ) {
                    InkShelf2PulseArtwork(article, height = 105)
                    Spacer(Modifier.height(9.dp))
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
    Spacer(Modifier.height(15.dp))
    InkShelf2ActionButton(
        label = "See All News & Videos",
        onClick = onSeeAll,
        leadingIcon = Icons.Outlined.AutoStories,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun InkShelf2PulseArtwork(article: InkShelf2PulseArticle, height: Int) {
    var failed by remember(article.imageUrl) { mutableStateOf(false) }
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
        if (article.imageUrl != null && !failed) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(
                        Color.Transparent,
                        InkShelf2Colors.Background.copy(alpha = 0.30f),
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
        if (article.isVideo) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(InkShelf2Colors.Accent.copy(alpha = 0.92f))
                    .border(1.dp, InkShelf2Colors.AccentLight, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Watch on YouTube",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        } else {
            Icon(
                Icons.Outlined.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(7.dp).size(15.dp),
            )
        }
    }
}


/**
 * Original comic-newspaper front page: masthead, edition rule and a live
 * publisher headline. Editorial styling only; no Daily Bugle branding.
 */
@Composable
private fun InkShelf2PulseNewsHero(
    featured: InkShelf2PulseArticle?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val ink = Color(0xFF17111F)
    val paper = Color(0xFFF1E6D7)
    val accent = Color(0xFF5E276F)
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFF6E7), paper, Color(0xFFE2D1C1)),
                ),
            )
            .border(1.dp, InkShelf2Colors.AccentLight.copy(alpha = 0.6f), shape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                "THE INKSHELF EDITION",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text("VOL. 02", color = ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        androidx.compose.material3.HorizontalDivider(thickness = 2.dp, color = ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "THE PULSE",
            color = ink,
            fontSize = 41.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 43.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            letterSpacing = (-1.4).sp,
        )
        androidx.compose.material3.HorizontalDivider(thickness = 1.dp, color = ink)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.UK)
                    .format(Date()).uppercase(Locale.UK),
                color = ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("COMICS  •  REVIEWS  •  VIDEO", color = ink, fontSize = 9.sp)
        }
        androidx.compose.material3.HorizontalDivider(thickness = 3.dp, color = ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "LATEST HEADLINE",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            featured?.title ?: "STORIES FROM ACROSS THE COMIC UNIVERSE",
            color = ink,
            fontSize = 19.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = if (featured == null) Modifier else Modifier.clickable { onOpen(featured.url) },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (featured != null && featured.imageUrl != null) {
                Box(modifier = Modifier.width(99.dp)) {
                    InkShelf2PulseArtwork(featured, height = 76)
                }
            }
            Text(
                if (featured == null) "Latest verified publisher stories appear here when the news feed connects."
                else "From " + featured.publisher +
                    "  •  Tap the headline to read the full story at its original source.",
                color = ink.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.HorizontalDivider(
            thickness = 1.dp,
            color = ink.copy(alpha = 0.45f),
        )
        Text(
            "ALL THE STORIES BEHIND THE PANELS",
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
internal fun InkShelf2PulseScreen(
    padding: PaddingValues,
    articles: List<InkShelf2PulseArticle>,
    loading: Boolean,
    showVideos: Boolean,
    onShowVideos: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val selected = articles.filter { it.isVideo == showVideos }
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 6.dp,
            bottom = padding.calculateBottomPadding() + 22.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "pulse-front-page") {
            InkShelf2PulseNewsHero(
                featured = articles.filterNot { it.isVideo }
                    .maxByOrNull { it.publishedAt },
                onBack = onBack,
                onOpen = onOpenArticle,
            )
        }
        item(key = "pulse-tabs") {
            InkShelf2PulseTabs(showVideos, onShowVideos)
        }
        if (selected.isEmpty()) {
            item {
                Text(
                    when {
                        loading -> if (showVideos) "Loading comic-book videos…" else "Loading comic-book news…"
                        showVideos -> "No videos available right now. Check your connection and try again."
                        else -> "No articles available right now. Check your connection and try again."
                    },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    color = InkShelf2Colors.Muted,
                )
            }
        } else {
            items(selected, key = { it.url }) { article ->
                InkShelf2PulseListCard(article, onOpenArticle)
            }
            item {
                Text(
                    if (showVideos)
                        "YouTube news and reviews from selected comic channels. Videos open in YouTube."
                    else
                        "Comic news from AIPT Comics and The Beat. Articles open on the original publisher's website.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun InkShelf2PulseListCard(
    article: InkShelf2PulseArticle,
    onOpenArticle: (String) -> Unit,
) {
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


/**
 * Conservative related-content matching. Match the full comic series phrase
 * (after removing a trailing publishing year), not a shared character name.
 * This prevents e.g. Batman news being attached to Absolute Batman unless
 * the headline explicitly mentions "Absolute Batman".
 */
internal object InkShelf2PulseMatcher {
    private val publishingYearSuffix =
        Regex("""\s*\((?:19|20)\d{2}(?:\s*[-–—]\s*(?:(?:19|20)\d{2})?)?\)\s*$""")
    private val punctuation = Regex("""[^\p{L}\p{N}]+""")
    private val extraSpaces = Regex("""\s+""")
    private val volumeSuffix = Regex("""\s+(?:(?:vol(?:ume)?\.?)\s*\d+|#\s*\d+)$""", RegexOption.IGNORE_CASE)
    private val comicContexts = listOf(
        "comic", "comics", "issue", "review", "omnibus", "volume", "vol",
        "collected", "graphic novel", "edition", "writer", "artist",
        "cover", "covers", "series", "first look",
    )

    private fun normalise(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(punctuation, " ")
            .trim()
            .replace(extraSpaces, " ")

    fun searchTerm(comicTitle: String): String? {
        val title = comicTitle.replace(publishingYearSuffix, "")
            .replace(volumeSuffix, "").trim()
        val normalised = normalise(title)
        // Names shorter than four letters are much too ambiguous for a
        // historical search. Others still require strict headline checks.
        return title.takeIf { normalised.length >= 4 }
    }

    fun isMatch(comicTitle: String, article: InkShelf2PulseArticle): Boolean {
        val search = searchTerm(comicTitle) ?: return false
        val series = normalise(search)
        val headline = " " + normalise(article.title) + " "
        val phrase = " $series "
        if (!headline.contains(phrase)) return false

        // Longer full-series phrases reliably identify a specific comic
        // (e.g. Absolute Batman, Ultimate Spider-Man or X-Men). A short
        // single-name franchise (Spawn, Batman, Saga) must also reference
        // the comic medium; film, TV and gaming headlines are excluded.
        if (series.split(' ').size >= 2) return true
        val medium = comicContexts.any { context ->
            (" " + context + " ") in headline
        }
        val issueNumber = Regex("""#\s*\d+""").containsMatchIn(article.title)
        return medium || issueNumber
    }

    fun related(
        comicTitle: String,
        articles: List<InkShelf2PulseArticle>,
    ): List<InkShelf2PulseArticle> {
        val matches = articles.filter { isMatch(comicTitle, it) }.distinctBy { it.url }
        val news = matches.filterNot { it.isVideo }.sortedByDescending { it.publishedAt }.take(2)
        val videos = matches.filter { it.isVideo }.sortedByDescending { it.publishedAt }.take(2)
        return buildList {
            news.getOrNull(0)?.let(::add)
            videos.getOrNull(0)?.let(::add)
            news.getOrNull(1)?.let(::add)
            videos.getOrNull(1)?.let(::add)
        }
    }
}

/**
 * Related publisher headlines / video reviews shown only when a strict,
 * real match exists. The comic's chapter loading and reader never wait for
 * this optional, cached RSS content.
 */
@Composable
internal fun InkShelf2RelatedPulse(comicTitle: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var feed by remember(comicTitle) { mutableStateOf<List<InkShelf2PulseArticle>>(emptyList()) }
    LaunchedEffect(comicTitle) {
        // Render cached matches first. The comic/reader itself never waits.
        val recent = InkShelf2PulseFeed.cached(context)
        val historical = InkShelf2PulseArchive.cached(context)
        feed = (recent + historical).distinctBy { it.url }
        try {
            val latest = InkShelf2PulseFeed.refresh(context)
            feed = (latest + InkShelf2PulseArchive.cached(context)).distinctBy { it.url }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Keep cached cards when feeds are down.
        }
        try {
            // Public WordPress search can reach up to one year back when
            // supported by the publisher; results are cached per series.
            val matches = InkShelf2PulseArchive.searchForComic(context, comicTitle)
            feed = (feed + matches).distinctBy { it.url }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // An optional article lookup must never block Comic Details.
        }
    }
    val related = remember(comicTitle, feed) { InkShelf2PulseMatcher.related(comicTitle, feed) }
    if (related.isEmpty()) return

    Spacer(Modifier.height(20.dp))
    Text(
        "In The Pulse",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = InkShelf2Colors.Text,
    )
    Spacer(Modifier.height(5.dp))
    Text(
        "Related comic news & video reviews",
        color = InkShelf2Colors.Muted,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(12.dp))
    androidx.compose.foundation.lazy.LazyRow(
        // The surrounding Details Column already provides the page's 16dp
        // horizontal gutters. Keep cards aligned with the synopsis text.
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(related, key = { it.url }) { article ->
            Column(
                modifier = Modifier.width(202.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.70f))
                    .border(
                        1.dp, InkShelf2Colors.Border.copy(alpha = 0.75f),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { InkShelf2PulseFeed.openArticle(context, article.url) }
                    .padding(9.dp),
            ) {
                InkShelf2PulseArtwork(article, height = 103)
                Spacer(Modifier.height(7.dp))
                Text(
                    if (article.isVideo) "VIDEO REVIEW" else "RELATED NEWS",
                    color = InkShelf2Colors.AccentLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    article.title,
                    color = InkShelf2Colors.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    article.publisher,
                    color = InkShelf2Colors.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
