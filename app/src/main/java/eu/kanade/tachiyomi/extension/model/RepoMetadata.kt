package eu.kanade.tachiyomi.extension.model

import kotlinx.serialization.Serializable

@Serializable
data class RepoMetadata(
    val name: String,
    val website: String,
    val discordUrl: String? = null,
)
