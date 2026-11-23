package eu.kanade.tachiyomi.ui.inkshelf.shell

import java.util.Locale

/** Editorial topics for publisher-supplied Pulse headlines, not article scraping. */
internal enum class InkShelf2PulseTopic { COMICS, FILM_TV, INDUSTRY }

internal object InkShelf2PulseTopics {
    // General entertainment feeds contain games and unrelated coverage.
    private val unrelated = Regex(
        """\b(?:video games?|gaming|playstation|nintendo|xbox|fortnite|pokemon|wrestling|wwe|mtg|tabletop|board games?)\b""",
    )
    private val comicContext = Regex(
        """\b(?:comics?|graphic novels?|manga|superheroes?|marvel|dc comics|dc studios|image comics|dark horse|idw|vertigo|batman|superman|supergirl|spider[\s-]?man|x[\s-]?men|avengers|fantastic four|daredevil|the boys|invincible|deadpool|wolverine|wonder woman|peacemaker|captain america|iron man|green lantern|black panther|the penguin|blade|flashpoint|secret wars)\b""",
    )
    private val screenContext = Regex(
        """\b(?:movies?|films?|cinema|box office|trailers?|cast(?:ing)?|actors?|actresses|television|tv|streaming|netflix|hbo|disney\+?|prime video|mcu|marvel studios|seasons?|episodes?|live[\s-]?action|adaptation|reboot|showrunner)\b""",
    )
    private val industryContext = Regex(
        """\b(?:industry|publishers?|publishing|comic shops?|retailers?|distribution|distributors?|sales charts?|market share|solicitations|licensing|imprints?|creators?|editorial|acquisitions?|layoffs?|bankruptcy|diamond comic)\b""",
    )

    /** General feeds require a comic link; a dedicated comics RSS category supplies that context. */
    fun classify(
        title: String,
        rssCategories: String = "",
        dedicatedComicsFeed: Boolean = false,
    ): InkShelf2PulseTopic? {
        val text = "$title $rssCategories".lowercase(Locale.ROOT)
        if (unrelated.containsMatchIn(title.lowercase(Locale.ROOT))) return null
        val comic = dedicatedComicsFeed || comicContext.containsMatchIn(text)
        if (!comic) return null
        if (industryContext.containsMatchIn(title.lowercase(Locale.ROOT))) {
            return InkShelf2PulseTopic.INDUSTRY
        }
        if (screenContext.containsMatchIn(text)) return InkShelf2PulseTopic.FILM_TV
        return InkShelf2PulseTopic.COMICS
    }

    /**
     * Approximate 60% comics, 25% film/TV and 15% industry when available.
     * Missing topics fill with genuine items rather than leaving blank slots.
     * Input order stays publisher-balanced; the buckets preserve that order.
     */
    fun mix(news: List<InkShelf2PulseArticle>, limit: Int = 36): List<InkShelf2PulseArticle> {
        if (limit <= 0) return emptyList()
        val clean = news.filterNot { it.isVideo }.distinctBy { it.url }
        val queues = InkShelf2PulseTopic.entries.associateWith { topic ->
            clean.filter { it.topic == topic }.toMutableList()
        }
        val cycle = listOf(
            InkShelf2PulseTopic.COMICS, InkShelf2PulseTopic.COMICS, InkShelf2PulseTopic.FILM_TV,
            InkShelf2PulseTopic.COMICS, InkShelf2PulseTopic.INDUSTRY, InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopic.FILM_TV, InkShelf2PulseTopic.COMICS, InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopic.FILM_TV, InkShelf2PulseTopic.COMICS, InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopic.INDUSTRY, InkShelf2PulseTopic.FILM_TV, InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopic.COMICS, InkShelf2PulseTopic.FILM_TV, InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopic.COMICS, InkShelf2PulseTopic.INDUSTRY,
        )
        val result = mutableListOf<InkShelf2PulseArticle>()
        for (slot in 0 until limit) {
            val desired = queues[cycle[slot % cycle.size]]
            val next = when {
                !desired.isNullOrEmpty() -> desired.removeAt(0)
                else -> queues.values.filter { it.isNotEmpty() }
                    .map { it.first() }.maxByOrNull { it.publishedAt }
                    ?.also { fallback -> queues[fallback.topic]?.removeAt(0) }
            } ?: break
            result += next
        }
        return result
    }

    /**
     * Give Screen Rant slightly more visibility without creating articles,
     * dropping other publishers or changing the comic/film/industry mix.
     *
     * Prefer one Screen Rant headline in the first two news cards (including
     * Home's Pulse preview), and a second within the first eight if available.
     * Preserve the relative order of every other publisher's stories.
     */
    fun promoteScreenRant(news: List<InkShelf2PulseArticle>): List<InkShelf2PulseArticle> {
        if (news.size < 2 || news.none { it.publisher == "Screen Rant" }) return news
        val reordered = news.toMutableList()
        listOf(1, 7).forEachIndexed { ordinal, target ->
            if (target >= reordered.size) return@forEachIndexed
            val alreadyVisible = reordered.take(target + 1)
                .count { it.publisher == "Screen Rant" }
            if (alreadyVisible > ordinal) return@forEachIndexed
            val next = reordered.indexOfFirst { article ->
                article.publisher == "Screen Rant" &&
                    reordered.indexOf(article) > target
            }
            if (next > target) {
                val featured = reordered.removeAt(next)
                reordered.add(target, featured)
            }
        }
        return reordered
    }

    fun label(topic: InkShelf2PulseTopic): String = when (topic) {
        InkShelf2PulseTopic.COMICS -> "Comics"
        InkShelf2PulseTopic.FILM_TV -> "Film & TV"
        InkShelf2PulseTopic.INDUSTRY -> "Industry"
    }
}
