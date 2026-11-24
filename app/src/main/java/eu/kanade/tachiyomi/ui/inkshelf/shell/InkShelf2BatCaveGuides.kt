package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * BatCave editorial event adapter. Metadata only; extension remains authoritative
 * for readable chapters. Never injects entries directly into the user's library.
 */
internal object InkShelf2BatCaveGuides {
    private const val BASE = "https://batcave.biz"
    private val validSlug = Regex("""[a-z0-9]+(?:-[a-z0-9]+)*""")
    private val issuePath = Regex("""^/event/([a-z0-9-]+)/(\d{1,5})/?$""")
    private val seriesPath = Regex("""^/\d{2,9}-[a-z0-9-]+\.html$""")
    private val issueNumber = Regex("""(?i)(?:issue\s*)?#\s*(\d{1,4}(?:\.\d+)?)\b""")
    private val yearPattern = Regex("""\(((?:19|20)\d{2})(?:[-–—].*?)?\)""")
    private val cataloguePath = Regex("""^/event/([a-z0-9-]+)/?$""")
    private val excluded = setOf("marvel", "dc", "epic", "evening", "weekend", "month", "runs", "relaunches")
    private fun localPath(value: String): String = runCatching {
        val url = java.net.URI("$BASE/").resolve(value)
        if (url.scheme == "https" && url.host in setOf("batcave.biz", "www.batcave.biz"))
            url.path else ""
    }.getOrDefault("")

    private fun guide(slug: String, name: String, publisher: String) = InkShelf2Guide(
        id = -(slug.hashCode().toLong().let { kotlin.math.abs(it) } % 2_000_000_000L).toInt() - 1,
        name = name, type = "EVENT", author = "BatCave event index",
        attribution = "BatCave reading order · Essential / Complete",
        rating = 0.0, publisher = publisher, batcaveSlug = slug,
    )
    // Blood Hunt is an on-demand pilot; availability is checked by the user.
    private val knownEvents = listOf(
        guide("blood-hunt", "Blood Hunt", "Marvel"),
        guide("house-of-m", "House of M", "Marvel"),
        guide("blackest-night", "Blackest Night", "DC"),
        guide("civil-war-2006", "Marvel Civil War", "Marvel"),
        guide("civil-war-ii", "Civil War II", "Marvel"),
        guide("secret-wars-2015", "Secret Wars (2015)", "Marvel"),
    )
    fun featured(): List<InkShelf2Guide> = knownEvents

    suspend fun catalogue(context: Context): List<InkShelf2Guide> {
        // Retire the old raw-page cache. No background website requests.
        context.getSharedPreferences("inkshelf2_batcave_events_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
        return knownEvents
    }

    internal fun parseCatalogue(raw: String): List<InkShelf2Guide> =
        Jsoup.parse(raw, BASE).select("a[href]").mapNotNull { link ->
            val slug = cataloguePath.matchEntire(localPath(link.attr("href")))
                ?.groupValues?.get(1) ?: return@mapNotNull null
            if (slug in excluded) return@mapNotNull null
            val text = link.text().trim().replace(Regex("""\s+"""), " ")
            val match = Regex("""(Marvel|DC)\s*\d+\s*issues\b""").find(text)
                ?: return@mapNotNull null
            val title = text.substring(0, match.range.first).trim()
            if (title.length !in 3..100) return@mapNotNull null
            guide(slug, title, match.groupValues[1])
        }.distinctBy { it.batcaveSlug }

    suspend fun issues(context: Context, slug: String, page: Int): InkShelf2GuidePage {
        InkShelf2BundledEventOrders.get(slug, page)?.let { return it }
        return InkShelf2GuidePage(
            if (page == 1) InkShelf2EventMetadataStore.read(context, slug).orEmpty() else emptyList(),
            false,
        )
    }

    suspend fun synopsis(context: Context, slug: String): String? =
        if (slug == "house-of-m") "In House of M (2005), the Scarlet Witch reshapes reality into a world where mutants hold power. Follow the eight-issue main story or explore the crossover's tie-ins." else null

    suspend fun coverUrl(context: Context, slug: String): String? = null

    internal fun parseIssues(
        raw: String, slug: String, page: Int, firstPageHtml: String? = null,
    ): InkShelf2GuidePage {
        if (!validSlug.matches(slug)) return InkShelf2GuidePage(emptyList(), false)
        val doc = Jsoup.parse(raw, BASE)
        val essentialDoc = if (firstPageHtml != null) Jsoup.parse(firstPageHtml, BASE) else doc
        val essential = shortPathKeys(
            essentialDoc.select("h1,h2,h3").firstOrNull {
                it.text().contains("Start here: the short path", true)
            },
        )
        val entries = doc.select("li").mapNotNull { li ->
            val target = li.select("a[href]").firstOrNull {
                issuePath.matchEntire(localPath(it.attr("href")))?.groupValues?.get(1) == slug
            }
            // BatCave explicitly labels missing items "not on the site yet".
            // Keep their reading-order positions without fabricating links.
            if (target == null) {
                val missing = li.text().trim()
                if (!missing.contains("not on the site yet", ignoreCase = true))
                    return@mapNotNull null
                val positionMatch = Regex("""^(\d{1,5})\s+(.+)$""").find(missing)
                    ?: return@mapNotNull null
                val pos = positionMatch.groupValues[1].toIntOrNull()
                    ?.takeIf { it in 1..10_000 } ?: return@mapNotNull null
                val clean = positionMatch.groupValues[2]
                    .replace(Regex("""(?i)\s+not on the site yet\s*$"""), "")
                val num = issueNumber.findAll(clean).lastOrNull()?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val title = clean.substringBeforeLast('#').trim()
                if (title.length !in 3..140) return@mapNotNull null
                return@mapNotNull InkShelf2GuideIssue(
                    id = pos, position = pos, series = title, volume = null,
                    number = num,
                    role = if (normalizedKey(title, num) in essential)
                        "Core Issue" else "Optional tie-in",
                    batcaveSeriesPath = null, batcaveIssuePath = null,
                )
            }
            val match = issuePath.matchEntire(localPath(target.attr("href"))) ?: return@mapNotNull null
            val pos = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..10_000 }
                ?: return@mapNotNull null
            val seriesLink = li.select("a[href]").firstOrNull {
                seriesPath.matches(localPath(it.attr("href")))
            } ?: return@mapNotNull null
            // The adjacent series hyperlink is often only a ↗ glyph.
            // The issue's primary anchor carries the *series name* and #number.
            val rawLabel = target.text().trim()
                .replace(Regex("^\\s*" + pos + "\\s*"), "").trim()
            val originalTitle = seriesLink.text().trim()
            val withoutIssue = rawLabel
                .replace(Regex("""(?i)\s*(?:Issue\s*)?#\s*\d{1,4}(?:\.\d+)?\s*$"""), "")
                .replace(Regex("""(?i)\s*Full\s*$"""), "").trim()
            val namedEdition = Regex("""^.+?\((?:19|20)\d{2}[^)]*\)""")
                .find(withoutIssue)?.value
            val seriesName = if (originalTitle.length in 3..140 &&
                originalTitle != "Open series" && originalTitle != "View series")
                originalTitle else namedEdition ?: withoutIssue
            if (seriesName.length !in 3..140) return@mapNotNull null
            val number = issueNumber.findAll(rawLabel).lastOrNull()?.groupValues?.get(1)
                ?: if (rawLabel.contains("Full", true)) "Full" else ""
            InkShelf2GuideIssue(
                id = pos, position = pos, series = seriesName, volume = null,
                number = number, role = if (normalizedKey(seriesName, number) in essential)
                    "Core Issue" else "Optional tie-in",
                yearBegan = yearPattern.find(seriesName)?.groupValues?.get(1)?.toIntOrNull(),
                batcaveSeriesPath = localPath(seriesLink.attr("href")),
                batcaveIssuePath = localPath(target.attr("href")),
            )
        }.distinctBy { it.position }.sortedBy { it.position }
        val next = page + 1
        val morePath = Regex("^/event/" + slug + "/list/" + next + "/?$")
        val more = doc.select("a[href]").any { morePath.matches(localPath(it.attr("href"))) }
        return InkShelf2GuidePage(entries, more)
    }

    private fun shortPathKeys(header: Element?): Set<String> {
        if (header == null) return emptySet()
        var sibling = header.nextElementSibling()
        repeat(6) {
            if (sibling == null) return emptySet()
            if (sibling!!.tagName() in listOf("ol", "ul")) {
                return sibling!!.select("li").mapNotNull { item ->
                    val value = item.text().replace(Regex("""^\s*\d+[.)]\s*"""), "")
                    val match = issueNumber.findAll(value).lastOrNull()
                        ?: return@mapNotNull null
                    val title = value.substringBeforeLast('#').trim()
                    normalizedKey(title, match.groupValues[1])
                }.toSet()
            }
            sibling = sibling!!.nextElementSibling()
        }
        return emptySet()
    }

    private fun normalizedKey(series: String, issue: String) =
        InkShelf2GuideEdition.titleKey(series)
            .removePrefix("the ") + "#" + issue.trim().trimStart('0').ifBlank { "0" }
}
