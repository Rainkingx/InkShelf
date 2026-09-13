package eu.kanade.tachiyomi.data.track.bangumi

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String,
    val nickname: String? = null,
)
