package eu.kanade.tachiyomi.data.track.hikka

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.hikka.dto.HKManga
import eu.kanade.tachiyomi.data.track.hikka.dto.HKMangaPagination
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.data.track.hikka.dto.HKRead
import eu.kanade.tachiyomi.data.track.hikka.dto.HKUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class HikkaApi(
    private val trackId: Int,
    private val client: OkHttpClient,
    interceptor: HikkaInterceptor,
) {
    suspend fun getCurrentUser(): HKUser =
        withIOContext {
            val request =
                Request
                    .Builder()
                    .url("${BASE_API_URL}/user/me")
                    .get()
                    .build()
            with(json) {
                authClient
                    .newCall(request)
                    .awaitSuccess()
                    .parseAs<HKUser>()
            }
        }

    suspend fun accessToken(reference: String): HKOAuth =
        withIOContext {
            with(json) {
                client
                    .newCall(authTokenCreate(reference))
                    .awaitSuccess()
                    .parseAs<HKOAuth>()
            }
        }

    suspend fun searchManga(query: String): List<TrackSearch> =
        withIOContext {
            val url =
                "$BASE_API_URL/manga"
                    .toUri()
                    .buildUpon()
                    .appendQueryParameter("page", "1")
                    .appendQueryParameter("size", "50")
                    .build()

            val payload =
                buildJsonObject {
                    put("media_type", buildJsonArray { })
                    put("status", buildJsonArray { })
                    put("only_translated", false)
                    put("magazines", buildJsonArray { })
                    put("genres", buildJsonArray { })
                    put(
                        "score",
                        buildJsonArray {
                            add(0)
                            add(10)
                        },
                    )
                    put("query", query)
                    put(
                        "sort",
                        buildJsonArray {
                            add("score:desc")
                            add("scored_by:desc")
                        },
                    )
                }

            with(json) {
                authClient
                    .newCall(POST(url.toString(), body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .parseAs<HKMangaPagination>()
                    .list
                    .map { it.toTrack(trackId) }
            }
        }

    suspend fun getRead(track: Track): HKRead? =
        withIOContext {
            val slug = track.tracking_url.split("/")[4]
            val url = "$BASE_API_URL/read/manga/$slug".toUri().buildUpon().build()
            with(json) {
                try {
                    authClient
                        .newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<HKRead>()
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        null
                    } else {
                        throw e
                    }
                }
            }
        }

    suspend fun getManga(track: Track): TrackSearch =
        withIOContext {
            val slug = track.tracking_url.split("/")[4]
            val url =
                "$BASE_API_URL/manga/$slug"
                    .toUri()
                    .buildUpon()
                    .build()

            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<HKManga>()
                    .toTrack(trackId)
            }
        }

    suspend fun deleteUserManga(track: Track) =
        withIOContext {
            val slug = track.tracking_url.split("/")[4]

            val url =
                "$BASE_API_URL/read/manga/$slug"
                    .toUri()
                    .buildUpon()
                    .build()

            authClient
                .newCall(DELETE(url.toString()))
                .awaitSuccess()
        }

    suspend fun addUserManga(track: Track): Track =
        withIOContext {
            val slug = track.tracking_url.split("/")[4]

            val url =
                "$BASE_API_URL/read/manga/$slug"
                    .toUri()
                    .buildUpon()
                    .build()

            var rereads = getRead(track)?.rereads ?: 0
            if (track.status == Hikka.REREADING && rereads == 0) {
                rereads = 1
            }

            val payload =
                buildJsonObject {
                    put("note", "")
                    put("chapters", track.last_chapter_read.toInt())
                    put("volumes", 0)
                    put("rereads", rereads)
                    put("score", track.score.toInt())
                    put("status", track.toApiStatus())
                    put("start_date", if (track.started_reading_date > 0L) track.started_reading_date / 1000 else null)
                    put("end_date", if (track.finished_reading_date > 0L) track.finished_reading_date / 1000 else null)
                }

            val response =
                with(json) {
                    authClient
                        .newCall(PUT(url.toString(), body = payload.toString().toRequestBody(jsonMime)))
                        .awaitSuccess()
                        .parseAs<HKRead>()
                }

            // Mutate and return the original track so callers that key inserts off the
            // returned object (eg. manga_id) don't lose data to a freshly built TrackSearch.
            track.apply {
                last_chapter_read = response.chapters.toFloat()
                score = response.score.toFloat()
                status = toTrackStatus(response.status)
                started_reading_date = response.startDate?.let { it * 1000 } ?: started_reading_date
                finished_reading_date = response.endDate?.let { it * 1000 } ?: finished_reading_date
            }
        }

    suspend fun updateUserManga(track: Track): Track = addUserManga(track)

    private val json: Json by injectLazy()
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    companion object {
        const val BASE_API_URL = "https://api.hikka.io"
        const val BASE_URL = "https://hikka.io"
        private const val SCOPE = "readlist,read:user-details"
        private const val CLIENT_REFERENCE = "8bd64e1b-4d93-4c41-a9a7-9149c437131f"
        private const val CLIENT_SECRET =
            "ZQR9eSZiYg_T1-xlVQcfXRMzCeEmBj9GLfEwlM5ASEwVh6d2T_KBuXCKx9A818vKT155Bot6NpB61yIx8S_CaEJxDLh4L7xw5oxuOjAs8QFw7THwY-lS8uYfdVLE4zZ6"

        fun authUrl(): Uri =
            "$BASE_URL/oauth"
                .toUri()
                .buildUpon()
                .appendQueryParameter("reference", CLIENT_REFERENCE)
                .appendQueryParameter("scope", SCOPE)
                .build()

        fun refreshTokenRequest(accessToken: String): Request {
            val headers =
                Headers
                    .Builder()
                    .add("auth", accessToken)
                    .build()

            return GET("$BASE_API_URL/user/me", headers = headers) // Any request with auth
        }

        fun authTokenCreate(reference: String): Request {
            val payload =
                buildJsonObject {
                    put("request_reference", reference)
                    put("client_secret", CLIENT_SECRET)
                }
            return POST("$BASE_API_URL/auth/token", body = payload.toString().toRequestBody(jsonMime))
        }

        fun authTokenInfo(accessToken: String): Request {
            val headers =
                Headers
                    .Builder()
                    .add("auth", accessToken)
                    .build()

            return GET("$BASE_API_URL/auth/token/info", headers = headers)
        }
    }
}
