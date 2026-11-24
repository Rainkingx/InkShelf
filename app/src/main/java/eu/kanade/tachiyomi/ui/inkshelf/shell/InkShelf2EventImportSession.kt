package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.Charset
import java.util.Base64

/** Pure, bounded metadata import. No network, filesystem, cookies or reader access. */
internal class InkShelf2EventImportSession(val slug: String) {
    companion object {
        const val MAX_HTML = 2_000_000
        fun pageNumber(url: String, slug: String): Int? = runCatching {
            val uri = URI(url)
            if (uri.scheme != "https" || uri.host !in setOf("batcave.biz", "www.batcave.biz") ||
                uri.userInfo != null || uri.port !in setOf(-1, 443) || uri.query != null ||
                uri.fragment != null || !slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) return null
            val path = uri.path
            if (path == "/event/$slug/" || path == "/event/$slug") 1
            else Regex("^/event/$slug/list/([1-9][0-9]?)/?$").matchEntire(path)
                ?.groupValues?.get(1)?.toInt()?.takeIf { it <= 30 }
        }.getOrNull()
    }

    private val pages = linkedMapOf<Int, List<InkShelf2GuideIssue>>()
    private val requiredPages = sortedSetOf(1)
    private var shortPath = ""
    var essentialCount: Int = 0
        private set
    var declaredTotal: Int? = null
        private set
    val importedCount get() = pages.values.sumOf { it.size }
    val nextPage get() = requiredPages.firstOrNull { it !in pages }
    fun url(page: Int) = "https://batcave.biz/event/$slug/" + if (page == 1) "" else "list/$page/"

    fun add(raw: String, sourceUrl: String) {
        require(raw.length in 1..MAX_HTML) { "Page is empty or exceeds the import limit." }
        val page = pageNumber(sourceUrl, slug)
        require(page != null && page == nextPage) { "Import the next missing event page in order." }
        val doc = Jsoup.parse(raw, sourceUrl)
        val text = doc.text()
        require(!Regex("(?i)just a moment|verify (?:you are|you're) human|access denied|checking your browser").containsMatchIn(doc.title() + " " + text.take(500))) {
            "Verification or access restriction is still present. Complete it normally or use a permitted saved page."
        }
        doc.selectFirst("link[rel=canonical]")?.attr("href")?.takeIf { it.isNotBlank() }?.let {
            require(pageNumber(URI(sourceUrl).resolve(it).toString(), slug) == page) { "This page belongs to a different event or page." }
        }
        var fragment = shortPath
        var coreCount = essentialCount
        var total = declaredTotal
        if (page == 1) {
            val header = doc.select("h1,h2,h3").firstOrNull {
                it.text().contains("Start here: the short path", true)
            }
            require(header != null) { "No supported Essential reading list found. Use an authorized provider instead." }
            var sibling = header.nextElementSibling()
            var list: org.jsoup.nodes.Element? = null
            repeat(6) {
                if (list == null && sibling != null) {
                    if (sibling!!.tagName() in listOf("ol", "ul")) list = sibling
                    else sibling = sibling!!.nextElementSibling()
                }
            }
            require(list != null && list!!.select("li").isNotEmpty()) { "Essential list is missing." }
            coreCount = list!!.select("li").size
            // Keep only the short-path text; discard scripts, form values and raw HTML.
            val safeList = org.jsoup.nodes.Element("ol")
            list!!.select("li").forEach { safeList.appendElement("li").text(it.text()) }
            fragment = "<h2>Start here: the short path</h2>" + safeList.outerHtml()
            total = Regex("(?i)\\b([1-9][0-9]{0,3})\\s+issues\\b").find(text)?.groupValues?.get(1)?.toInt()
        }
        val parsed = InkShelf2BatCaveGuides.parseIssues(raw, slug, page, fragment)
        require(parsed.issues.isNotEmpty()) { "No supported reading-list entries found." }
        val positions = doc.select("li").mapNotNull { li ->
            li.select("a[href]").mapNotNull { a ->
                val uri = runCatching { URI(sourceUrl).resolve(a.attr("href")) }.getOrNull()
                if (uri?.host !in setOf("batcave.biz", "www.batcave.biz")) null
                else Regex("^/event/$slug/([0-9]+)/?$").matchEntire(uri?.path.orEmpty())?.groupValues?.get(1)?.toInt()
            }.firstOrNull()
        }
        require(positions.distinct().size == positions.size) { "Duplicate reading-order positions on page." }
        require(parsed.issues.all { it.number.isNotBlank() }) { "An issue number is missing." }
        require(importedCount + parsed.issues.size <= 10000) { "Event exceeds the metadata limit." }
        val prior = pages.values.flatten().map { it.position }.toSet()
        require(parsed.issues.none { it.position in prior }) { "Pages overlap or repeat. Nothing was saved." }
        val links = doc.select("a[href]").map { URI(sourceUrl).resolve(it.attr("href")).toString() }
        val discovered = links.mapNotNull { pageNumber(it, slug) }.toSet()
        require(links.none {
            val path = runCatching { URI(it).path }.getOrDefault("")
            path.startsWith("/event/$slug/list/") && pageNumber(it, slug) == null
        }) { "Unsupported pagination. Use an authorized provider." }
        pages[page] = parsed.issues
        requiredPages.addAll(1..(discovered.maxOrNull() ?: page))
        if (parsed.more) requiredPages.add(page + 1)
        shortPath = fragment
        essentialCount = coreCount
        declaredTotal = total
    }

    fun validated(expectedTotal: Int, expectedEssential: Int): List<InkShelf2GuideIssue> {
        require(nextPage == null) { "Import every linked page before saving." }
        val rows = pages.values.flatten().sortedBy { it.position }
        require(expectedTotal in 1..10000 && rows.size == expectedTotal &&
            (declaredTotal == null || declaredTotal == expectedTotal)) { "Complete count does not match the source. Nothing was saved." }
        require(rows.map { it.position } == (1..expectedTotal).toList()) { "Reading-order positions have gaps." }
        require(expectedEssential > 0 && expectedEssential == essentialCount &&
            rows.count { it.isEssential } == expectedEssential) { "Essential count does not match the short path. Nothing was saved." }
        return rows
    }

    fun clear() { pages.clear(); requiredPages.clear(); requiredPages.add(1); shortPath = ""; essentialCount = 0; declaredTotal = null }
}

/** Decode only the HTML MIME part. Attachments are never opened or written. */
internal object InkShelf2SavedEventPage {
    fun decode(bytes: ByteArray): String {
        require(bytes.size <= InkShelf2EventImportSession.MAX_HTML) { "Saved file exceeds 2 MB." }
        val raw = bytes.toString(Charsets.UTF_8)
        if (!raw.trimStart().startsWith("From:", true) && !raw.contains("MIME-Version:", true)) return raw
        val boundary = Regex("(?i)boundary=\"?([^\";\\r\\n]+)").find(raw)?.groupValues?.get(1)
            ?: error("Unsupported MHT format. Choose saved HTML instead.")
        val htmlParts = raw.split("--$boundary").mapNotNull { part ->
            val split = Regex("\\r?\\n\\r?\\n").find(part) ?: return@mapNotNull null
            val headers = part.substring(0, split.range.first)
            if (!Regex("(?i)Content-Type:\\s*text/html\\b").containsMatchIn(headers)) return@mapNotNull null
            val body = part.substring(split.range.last + 1).trimEnd('\r', '\n')
            val encoding = Regex("(?i)Content-Transfer-Encoding:\\s*([^\\s]+)").find(headers)?.groupValues?.get(1)?.lowercase()
            val charset = Regex("(?i)charset=\"?([^\";\\s]+)").find(headers)?.groupValues?.get(1) ?: "UTF-8"
            val decoded = when (encoding) {
                "base64" -> Base64.getMimeDecoder().decode(body)
                "quoted-printable" -> {
                    val text = body.replace(Regex("=\\r?\\n"), "")
                    val output = java.io.ByteArrayOutputStream()
                    var i = 0
                    while (i < text.length) {
                        if (text[i] == '=') {
                            require(i + 2 < text.length) { "Truncated MHT encoding." }
                            output.write(text.substring(i + 1, i + 3).toInt(16)); i += 3
                        } else { output.write(text[i].code); i++ }
                    }
                    output.toByteArray()
                }
                null, "8bit", "7bit", "binary" -> body.toByteArray(Charsets.UTF_8)
                else -> error("Unsupported MHT encoding. Choose saved HTML instead.")
            }
            decoded.toString(Charset.forName(charset))
        }
        require(htmlParts.size == 1) { "Choose an MHT with one HTML page, or saved HTML." }
        return htmlParts.single()
    }
}
