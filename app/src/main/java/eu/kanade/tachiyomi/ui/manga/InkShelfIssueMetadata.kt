package eu.kanade.tachiyomi.ui.manga

import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Chapter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Optional, source-supplied metadata for a specific issue.
 *
 * SChapter has no standard issue-cover property. Never infer an issue cover from
 * the first story page or from a series title: those are frequently incorrect.
 * Sources may attach explicit metadata through the persisted SChapter.memo JSON.
 */
internal object InkShelfIssueMetadata {
    data class Details(
        val coverUrl: String? = null,
        val synopsis: String? = null,
        val pageCount: Int? = null,
    )

    private val issueCoverKeys =
        listOf(
            "inkshelf.issueCoverUrl",
            "comic.issue_cover_url",
            "issue_cover_url",
            "cover_url",
            "coverUrl",
            "cover",
            "coverImage",
            "cover_image",
            "thumbnailUrl",
            "thumbnail_url",
        )

    private val issueSynopsisKeys =
        listOf(
            "inkshelf.issueSynopsis",
            "comic.issue_synopsis",
            "issue_synopsis",
            "synopsis",
        )

    private val issuePageCountKeys =
        listOf("inkshelf.pageCount", "comic.page_count", "page_count", "pageCount", "pages")

    fun from(chapter: Chapter?): Details {
        val memo = chapter?.memo ?: return Details()
        val coverUrl =
            memo.firstText(issueCoverKeys)
                ?.takeIf { it.length <= 2048 }
                ?.takeIf { url ->
                    val uri = Uri.parse(url)
                    uri.scheme.equals("https", ignoreCase = true) &&
                        !uri.host.isNullOrBlank() &&
                        uri.authority?.contains("@") != true
                }
        val synopsis =
            memo.firstText(issueSynopsisKeys)
                ?.takeIf { it.length in 30..10_000 }
        val pageCount = memo.firstText(issuePageCountKeys)?.toIntOrNull()?.takeIf { it in 1..5000 }
        return Details(coverUrl = coverUrl, synopsis = synopsis, pageCount = pageCount)
    }

    fun missingSynopsis(description: String?): Boolean =
        description.isNullOrBlank() ||
            description.trim().equals("No description", ignoreCase = true) ||
            description.trim().equals("N/A", ignoreCase = true)

    /**
     * Different extensions place metadata at the root or under an issue/comic
     * namespace. Only consume explicit cover/thumbnail fields, never story pages.
     */
    private fun JsonObject.firstText(keys: List<String>): String? {
        val metadataObjects =
            listOf(this) + listOf("issue", "comic", "inkshelf", "metadata")
                .mapNotNull { get(it) as? JsonObject }
        return metadataObjects.firstNotNullOfOrNull { metadata ->
            keys.firstNotNullOfOrNull { key ->
                (metadata[key] as? JsonPrimitive)
                    ?.content
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }
    }
}
