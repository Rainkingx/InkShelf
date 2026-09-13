package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val data: List<Subject> = emptyList(),
)

@Serializable
// Incomplete DTO with only our needed attributes
data class Subject(
    val id: Long,
    val name_cn: String,
    val name: String,
    val summary: String? = null,
    val date: String? = null,
    val images: SubjectImages? = null,
    val eps: Int = 0,
    val rating: SubjectRating? = null,
    val platform: String? = null,
) {
    fun toTrackSearch(): TrackSearch =
        TrackSearch.create(TrackManager.BANGUMI).apply {
            media_id = this@Subject.id
            title = name_cn.ifBlank { name }
            cover_url = images?.common.orEmpty()
            summary =
                if (name_cn.isNotBlank()) {
                    "作品原名：$name" + this@Subject.summary?.let { "\n${it.trim()}" }.orEmpty()
                } else {
                    this@Subject.summary?.trim().orEmpty()
                }
            score = rating?.score?.toFloat() ?: -1f
            tracking_url = "https://bangumi.tv/subject/${this@Subject.id}"
            total_chapters = eps
            start_date = date ?: ""
        }
}

@Serializable
data class SubjectImages(
    val common: String? = null,
)

@Serializable
data class SubjectRating(
    val score: Double? = null,
)
