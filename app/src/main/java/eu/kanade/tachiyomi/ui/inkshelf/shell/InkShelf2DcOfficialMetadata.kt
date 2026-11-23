package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Optional official DC issue #1 metadata preview.
 *
 * No bulk scraping, web search, source replacement or auto-fetch: a user must
 * press "Check DC". Only two possible URLs based on a year-qualified series
 * title are requested, and returned pages are strictly title-verified.
 * Public issue details are displayed separately and never written to J2K or
 * Metron. For other publishers, Metron and extension metadata stay primary.
 */
internal object InkShelf2DcOfficialMetadata {
    private const val PREFS = "inkshelf2_dc_issue_preview"
    private const val TTL_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_HTML_CHARS = 512 * 1024
    private val seriesPattern = Regex("""^(.+?)\s*\(((?:19|20)\d{2})(?:\s*[-–]\s*(?:(?:19|20)\d{2})?)?\)$""")
    private val dcPrefixes = listOf(
        // Don't classify Marvel's "Absolute Carnage" or other generic
        // Absolute titles as DC's distinct Absolute Universe.
        "absolute batman", "absolute superman", "absolute wonder woman",
        "absolute flash", "absolute green lantern",
        "absolute martian manhunter", "absolute catwoman",
        "batman", "detective comics", "superman", "action comics",
        "wonder woman", "nightwing", "catwoman", "batgirl", "robin", "green lantern",
        "justice league", "the flash", "flash ", "aquaman", "harley quinn",
        "teen titans", "titans", "swamp thing", "doom patrol", "shazam",
        "green arrow", "birds of prey", "dc comics presents",
    )

    internal data class Target(val series: String, val year: Int, val candidates: List<String>)
    internal data class OfficialIssue(
        val title: String,
        val url: String,
        val writers: String?,
        val artists: String?,
        val onSaleDate: String?,
        val pages: Int?,
    )

    private fun slug(text: String): String = text.lowercase(Locale.ROOT)
        .replace("&", "and").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun normalized(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")

    fun targetFor(seriesTitle: String): Target? {
        val match = seriesPattern.matchEntire(seriesTitle.trim()) ?: return null
        val series = match.groupValues[1].trim()
        val year = match.groupValues[2].toIntOrNull() ?: return null
        if (year !in 1935..2100 || !dcPrefixes.any { series.lowercase(Locale.ROOT).startsWith(it) }) return null
        val base = slug(series)
        if (base.isBlank()) return null
        return Target(
            series,
            year,
            listOf(
                "https://www.dc.com/comics/$base-$year/$base-1",
                "https://www.dc.com/comics/$base-$year/$base-$year-1",
            ).distinct(),
        )
    }

    /** Strictly match a year-qualified series and issue #1 before showing credits. */
    fun parseOfficialPage(
        html: String,
        actualUrl: String,
        target: Target,
    ): OfficialIssue? {
        val uri = runCatching { URI(actualUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "www.dc.com" ||
            !uri.path.orEmpty().startsWith("/comics/") ||
            actualUrl.substringBefore('#').trimEnd('/') !in target.candidates.map { it.trimEnd('/') }
        ) return null
        val doc = Jsoup.parse(html, actualUrl)
        val title = doc.select("h1").map { it.text() }.firstOrNull {
            Regex("""#\s*1\s*$""").containsMatchIn(it)
        } ?: return null
        val actualSeries = title.substringBeforeLast('#').trim()
            .replace(Regex("""\s*\((?:19|20)\d{2}\s*[-–]?\)\s*$"""), "")
        if (normalized(actualSeries) != normalized(target.series)) return null

        val text = doc.body()?.text().orEmpty()
        fun field(label: String, next: String): String? = Regex(
            Regex.escape(label) + """\s*(.{1,160}?)\s*(?=""" + next + ")",
            RegexOption.IGNORE_CASE,
        ).find(text)?.groupValues?.getOrNull(1)?.trim()?.take(130)?.takeIf { it.isNotEmpty() }

        val writers = field("Writer:", """Pencils:|Art by:|Cover:|Colorist:|SPECS""")
        val artists = field("Art by:", """Cover:|Colorist:|SPECS""")
            ?: field("Pencils:", """Cover:|Colorist:|SPECS""")
        val date = field("On Sale Date:", """Page Count:|Rated:|Starring""")
        val pages = Regex("""Page Count:\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (writers == null && artists == null && date == null && pages == null) return null
        return OfficialIssue(title, actualUrl, writers, artists, date, pages)
    }

    suspend fun fetchOnDemand(context: Context, target: Target): OfficialIssue? =
        withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "issue_" + slug(target.series) + "_" + target.year
            val cached = prefs.getString(key, null)
            if (cached != null) {
                val stored = runCatching { JSONObject(cached) }.getOrNull()
                if (stored != null && System.currentTimeMillis() -
                    stored.optLong("saved_at") in 0L until TTL_MS
                ) {
                    return@withContext OfficialIssue(
                        stored.optString("title"),
                        stored.optString("url"),
                        stored.optString("writers").takeIf { it.isNotBlank() },
                        stored.optString("artists").takeIf { it.isNotBlank() },
                        stored.optString("date").takeIf { it.isNotBlank() },
                        stored.optInt("pages").takeIf { it > 0 },
                    )
                }
            }
            for (candidate in target.candidates) {
                val connection = try {
                    (URL(candidate).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3_000
                        readTimeout = 4_000
                        instanceFollowRedirects = false
                        setRequestProperty("Accept", "text/html")
                        setRequestProperty("User-Agent", "InkShelf/2.0 optional official issue lookup")
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    continue
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                        !connection.contentType.orEmpty().contains("text/html", ignoreCase = true)
                    ) continue
                    val page = StringBuilder()
                    connection.inputStream.reader(StandardCharsets.UTF_8).use { reader ->
                        val buffer = CharArray(4_096)
                        while (page.length < MAX_HTML_CHARS) {
                            val n = reader.read(buffer, 0, minOf(buffer.size, MAX_HTML_CHARS - page.length))
                            if (n < 0) break
                            page.append(buffer, 0, n)
                        }
                    }
                    val result = parseOfficialPage(page.toString(), candidate, target) ?: continue
                    prefs.edit().putString(key, JSONObject().apply {
                        put("saved_at", System.currentTimeMillis())
                        put("title", result.title)
                        put("url", result.url)
                        put("writers", result.writers.orEmpty())
                        put("artists", result.artists.orEmpty())
                        put("date", result.onSaleDate.orEmpty())
                        put("pages", result.pages ?: 0)
                    }.toString()).apply()
                    return@withContext result
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // A blocked publisher page should never affect details or reading.
                } finally {
                    connection.disconnect()
                }
            }
            null
        }
}

@Composable
internal fun InkShelf2OfficialDcPanel(manga: Manga) {
    val target = remember(manga.title) { InkShelf2DcOfficialMetadata.targetFor(manga.title) } ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(manga.source, manga.url) {
        mutableStateOf<InkShelf2DcOfficialMetadata.OfficialIssue?>(null)
    }
    var busy by remember(manga.source, manga.url) { mutableStateOf(false) }
    var checked by remember(manga.source, manga.url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, InkShelf2Colors.Border, shape)
            .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.68f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Official DC details", color = InkShelf2Colors.Text,
            fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("Issue #1 · Additional publisher information, not your reading source",
            color = InkShelf2Colors.Muted, fontSize = 11.sp)
        if (info != null) {
            val issue = info!!
            issue.writers?.let { Text("Writer: $it", color = InkShelf2Colors.Text, fontSize = 12.sp) }
            issue.artists?.let { Text("Art: $it", color = InkShelf2Colors.Text, fontSize = 12.sp) }
            issue.onSaleDate?.let { Text("On sale: $it", color = InkShelf2Colors.Text, fontSize = 12.sp) }
            issue.pages?.let { Text("Pages: $it", color = InkShelf2Colors.Text, fontSize = 12.sp) }
        } else if (checked) {
            Text("No verified official issue details found for this edition.",
                color = InkShelf2Colors.Muted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (info == null) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                info = InkShelf2DcOfficialMetadata.fetchOnDemand(context, target)
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                info = null
                            } finally {
                                checked = true
                                busy = false
                            }
                        }
                    },
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp), strokeWidth = 2.dp,
                            color = InkShelf2Colors.AccentLight,
                        )
                    } else {
                        Text("Check DC", color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
                    }
                }
            }
            TextButton(onClick = {
                val official = info?.url ?: "https://www.dc.com/comics"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(official)))
                } catch (_: Exception) {
                    // No browser available: do not interfere with comic reading.
                }
            }) {
                Text(if (info != null) "View on DC.com" else "DC comic catalogue",
                    color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
            }
        }
    }
}
