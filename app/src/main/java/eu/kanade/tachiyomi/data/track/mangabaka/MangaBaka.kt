package eu.kanade.tachiyomi.data.track.mangabaka

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

class MangaBaka(
    private val context: Context,
    id: Int,
) : TrackService(id) {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val PAUSED = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
        const val REREADING = 6
        const val CONSIDERING = 7

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0

        const val STEP_1 = "STEP_1"
        const val STEP_5 = "STEP_5"
        const val STEP_10 = "STEP_10"
        const val STEP_20 = "STEP_20"
        const val STEP_25 = "STEP_25"

        private const val SEARCH_ID_PREFIX = "id:"
    }

    @StringRes
    override fun nameRes() = R.string.mangabaka

    private val json: Json by injectLazy()

    private val interceptor by lazy { MangaBakaInterceptor(this) }
    private val api by lazy { MangaBakaApi(id, client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val scorePreference = trackPreferences.mangabakaScoreType()

    override fun getLogo() = R.drawable.ic_tracker_mangabaka

    override fun getTrackerColor() = Color.rgb(124, 77, 255)

    override fun getLogoColor() = Color.rgb(124, 77, 255)

    override fun getStatusList(): List<Int> = listOf(CONSIDERING, PLAN_TO_READ, READING, COMPLETED, REREADING, PAUSED, DROPPED)

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun completedStatus(): Int = COMPLETED

    override fun readingStatus() = READING

    override fun planningStatus() = PLAN_TO_READ

    override fun getStatus(status: Int): String =
        with(context) {
            when (status) {
                CONSIDERING -> getString(R.string.considering)
                COMPLETED -> getString(R.string.completed)
                DROPPED -> getString(R.string.dropped)
                PAUSED -> getString(R.string.paused)
                PLAN_TO_READ -> getString(R.string.plan_to_read)
                READING -> getString(R.string.reading)
                REREADING -> getString(R.string.rereading)
                else -> ""
            }
        }

    override fun getGlobalStatus(status: Int): String = getStatus(status)

    override fun getScoreList(): List<String> =
        when (scorePreference.get()) {
            // 1, 2, ..., 99, 100
            STEP_1 -> IntRange(0, 100).map(Int::toString)
            // 5, 10, ..., 95, 100
            STEP_5 -> IntRange(0, 100).step(5).map(Int::toString)
            // 10, 20, ..., 90, 100
            STEP_10 -> IntRange(0, 100).step(10).map(Int::toString)
            // 20, 40, ..., 80, 100
            STEP_20 -> IntRange(0, 100).step(20).map(Int::toString)
            // 25, 50, 75, 100
            STEP_25 -> IntRange(0, 100).step(25).map(Int::toString)
            else -> throw Exception("Unknown score type")
        }

    override fun displayScore(track: Track): String = track.score.toInt().toString()

    override suspend fun add(track: Track): Track {
        track.status = DEFAULT_STATUS
        track.score = DEFAULT_SCORE.toFloat()
        updateNewTrackInfo(track)
        return api.addLibManga(track)
    }

    override suspend fun update(
        track: Track,
        setToRead: Boolean,
    ): Track {
        updateTrackStatus(track, setToRead, setToComplete = true, mustReadToComplete = false)
        return api.updateLibManga(track)
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.title = remoteTrack.title
            update(track)
        } else {
            add(track)
        }
    }

    override fun canRemoveFromService(): Boolean = true

    override suspend fun removeFromService(track: Track): Boolean =
        try {
            api.deleteLibManga(track)
            true
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).toIntOrNull()?.let { id ->
                return api.getMangaDetails(id)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibManga(track) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        return track
    }

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.getAccessToken(code)
            interceptor.setAuth(oauth)
            val currentUser = api.getCurrentUser()
            val scoreType =
                when (currentUser.ratingSteps) {
                    1 -> STEP_1
                    5 -> STEP_5
                    10 -> STEP_10
                    20 -> STEP_20
                    25 -> STEP_25
                    else -> throw Exception("Unknown score step size ${currentUser.ratingSteps}")
                }
            scorePreference.set(scoreType)
            saveCredentials(
                currentUser.nickname ?: currentUser.preferredUsername ?: currentUser.id,
                oauth.accessToken,
            )
        } catch (e: Exception) {
            logout()
            throw e
        }
    }

    fun saveToken(oauth: MangaBakaOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): MangaBakaOAuth? =
        try {
            json.decodeFromString<MangaBakaOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }

    fun verifyOAuthState(state: String): Boolean = api.verifyOAuthState(state)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }
}
