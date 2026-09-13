package eu.kanade.tachiyomi.data.track.bangumi

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class BangumiApi(
    private val client: OkHttpClient,
    interceptor: BangumiInterceptor,
) {
    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track =
        withIOContext {
            val body =
                buildJsonObject {
                    put("type", track.toApiStatus())
                    put("rate", track.score.toInt().coerceIn(0, 10))
                    put("ep_status", track.last_chapter_read.toInt())
                    put("private", track.private)
                }.toString().toRequestBody()
            authClient
                .newCall(
                    POST(
                        "$apiUrl/v0/users/-/collections/${track.media_id}",
                        body = body,
                        headers = headersOf("Content-Type", APP_JSON),
                    ),
                ).awaitSuccess()
            track
        }

    suspend fun updateLibManga(track: Track): Track =
        withIOContext {
            val body =
                buildJsonObject {
                    put("type", track.toApiStatus())
                    put("rate", track.score.toInt().coerceIn(0, 10))
                    put("ep_status", track.last_chapter_read.toInt())
                    put("private", track.private)
                }.toString().toRequestBody()
            val request =
                Request
                    .Builder()
                    .url("$apiUrl/v0/users/-/collections/${track.media_id}")
                    .patch(body)
                    .headers(headersOf("Content-Type", APP_JSON))
                    .build()
            authClient.newCall(request).awaitSuccess()
            track
        }

    suspend fun search(search: String): List<TrackSearch> =
        withIOContext {
            val body =
                buildJsonObject {
                    put("keyword", search)
                    put("sort", "match")
                    putJsonObject("filter") {
                        putJsonArray("type") { add(1) }
                    }
                }.toString().toRequestBody()
            with(json) {
                authClient
                    .newCall(
                        POST(
                            "$apiUrl/v0/search/subjects?limit=20",
                            body = body,
                            headers = headersOf("Content-Type", APP_JSON),
                        ),
                    ).awaitSuccess()
                    .parseAs<SearchResult>()
                    .data
                    .filter { it.platform == null || it.platform == "漫画" }
                    .map { it.toTrackSearch() }
            }
        }

    suspend fun statusLibManga(
        track: Track,
        username: String,
    ): Track? =
        withIOContext {
            val url = "$apiUrl/v0/users/$username/collections/${track.media_id}"
            with(json) {
                try {
                    authClient
                        .newCall(GET(url, cache = CacheControl.FORCE_NETWORK))
                        .awaitSuccess()
                        .parseAs<CollectionResponse>()
                        .let {
                            track.status = it.getStatus()
                            track.last_chapter_read = (it.ep_status ?: 0).toFloat()
                            track.score = (it.rate ?: 0).toFloat()
                            track.total_chapters = it.subject?.eps ?: 0
                            track.private = it.private
                            track
                        }
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        null
                    } else {
                        throw e
                    }
                }
            }
        }

    suspend fun getCurrentUser(): User =
        withIOContext {
            with(json) {
                authClient
                    .newCall(GET("$apiUrl/v0/me"))
                    .awaitSuccess()
                    .parseAs<User>()
            }
        }

    suspend fun accessToken(code: String): OAuth =
        withIOContext {
            with(json) {
                client
                    .newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    private fun accessTokenRequest(code: String) =
        POST(
            oauthUrl,
            body =
                FormBody
                    .Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("code", code)
                    .add("redirect_uri", redirectUrl)
                    .build(),
        )

    companion object {
        private const val clientId = "bgm67926a6c31a905d09"
        private const val clientSecret = "0519b3d139522a0257c2d0dedb894b35"

        private const val apiUrl = "https://api.bgm.tv"
        private const val oauthUrl = "https://bgm.tv/oauth/access_token"
        private const val loginUrl = "https://bgm.tv/oauth/authorize"

        private const val redirectUrl = "tachiyomij2k://bangumi-auth"

        private const val APP_JSON = "application/json"

        fun authUrl(): Uri =
            loginUrl
                .toUri()
                .buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUrl)
                .build()

        fun refreshTokenRequest(token: String) =
            POST(
                oauthUrl,
                body =
                    FormBody
                        .Builder()
                        .add("grant_type", "refresh_token")
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("refresh_token", token)
                        .add("redirect_uri", redirectUrl)
                        .build(),
            )
    }
}
