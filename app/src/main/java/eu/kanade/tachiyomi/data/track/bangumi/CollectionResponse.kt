package eu.kanade.tachiyomi.data.track.bangumi

import kotlinx.serialization.Serializable

@Serializable
// Incomplete DTO with only our needed attributes
data class CollectionResponse(
    val rate: Int? = 0,
    val type: Int? = null,
    val ep_status: Int? = 0,
    val vol_status: Int? = 0,
    val private: Boolean = false,
    val subject: SlimSubject? = null,
) {
    fun getStatus(): Int =
        when (type) {
            Bangumi.PLAN_TO_READ, Bangumi.COMPLETED, Bangumi.READING, Bangumi.ON_HOLD, Bangumi.DROPPED -> type
            else -> throw NotImplementedError("Unknown status: $type")
        }
}

@Serializable
// Incomplete DTO with only our needed attributes
data class SlimSubject(
    val volumes: Int? = 0,
    val eps: Int? = 0,
)
