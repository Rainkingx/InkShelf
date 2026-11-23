package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import java.util.Locale

/**
 * Article artwork URLs provided by the original publisher.
 * We extract only image metadata; no article text or image bytes are copied.
 */
internal object InkShelf2PulseImages {
    fun fromFeedHtml(markup: String): String? {
        if (markup.isBlank()) return null
        val document = Jsoup.parse(Parser.unescapeEntities(markup, false))
        for (element in document.select("img")) {
            for (attribute in listOf("data-src", "data-lazy-src", "data-original", "src")) {
                val candidate = safeHttps(element.attr(attribute))
                if (candidate != null) return candidate
            }
            val srcset = element.attr("srcset")
            val candidate = srcset.split(',').firstNotNullOfOrNull {
                safeHttps(it.trim().substringBefore(' '))
            }
            if (candidate != null) return candidate
        }
        return null
    }

    /** Prefer publisher-provided Open Graph / Twitter featured-image metadata. */
    fun fromArticleMetadata(markup: String, articleUrl: String): String? {
        if (markup.isBlank()) return null
        val document = Jsoup.parse(markup, articleUrl)
        val metadata = document.select("meta")
        val names = listOf("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src")
        for (name in names) {
            val meta = metadata.firstOrNull { node ->
                node.attr("property").equals(name, true) ||
                    node.attr("name").equals(name, true)
            } ?: continue
            val raw = meta.absUrl("content").ifBlank { meta.attr("content") }
            safeHttps(raw)?.let { return it }
        }
        return null
    }

    private fun safeHttps(raw: String): String? {
        val unescaped = Parser.unescapeEntities(raw.trim(), false)
        val candidate = if (unescaped.startsWith("//")) "https:$unescaped" else unescaped
        if (!candidate.startsWith("https://", ignoreCase = true)) return null
        return try {
            val uri = URI(candidate)
            if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
                uri.userInfo != null || uri.port !in setOf(-1, 443)
            ) return null
            val path = uri.path.orEmpty().lowercase(Locale.ROOT)
            if (path.endsWith(".svg") ||
                listOf("gravatar", "tracking", "pixel.gif", "avatar", "/logo.", "favicon")
                    .any { it in candidate.lowercase(Locale.ROOT) }
            ) return null
            candidate
        } catch (_: Exception) {
            null
        }
    }
}
