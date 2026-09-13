package eu.kanade.tachiyomi.data.track.hikka

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

class Hikka(
    private val context: Context,
    id: Int,
) : TrackService(id) {
    companion object {
        const val READING = 0
        const val COMPLETED = 1
        const val ON_HOLD = 2
        const val DROPPED = 3
        const val PLAN_TO_READ = 4
        const val REREADING = 5

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0
    }

    @StringRes
    override fun nameRes() = R.string.hikka

    private val json: Json by injectLazy()

    private val interceptor by lazy { HikkaInterceptor(this) }

    private val api by lazy { HikkaApi(id, client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override fun getLogo() = R.drawable.ic_tracker_hikka

    override fun getTrackerColor() = Color.rgb(0xD6, 0x91, 0xF7)

    override fun getLogoColor() = Color.rgb(0x22, 0x6C, 0xBB)

    override fun getStatusList(): List<Int> = listOf(PLAN_TO_READ, COMPLETED, ON_HOLD, READING, DROPPED, REREADING)

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun completedStatus(): Int = COMPLETED

    override fun readingStatus() = READING

    override fun planningStatus() = PLAN_TO_READ

    override fun getStatus(status: Int): String =
        with(context) {
            when (status) {
                READING -> getString(R.string.reading)
                PLAN_TO_READ -> getString(R.string.plan_to_read)
                COMPLETED -> getString(R.string.completed)
                ON_HOLD -> getString(R.string.on_hold)
                DROPPED -> getString(R.string.dropped)
                REREADING -> getString(R.string.rereading)
                else -> ""
            }
        }

    override fun getGlobalStatus(status: Int): String = getStatus(status)

    override fun getScoreList(): List<String> = IntRange(0, 10).map(Int::toString)

    override fun displayScore(track: Track): String = track.score.toInt().toString()

    override suspend fun add(track: Track): Track {
        track.status = DEFAULT_STATUS
        track.score = DEFAULT_SCORE.toFloat()
        updateNewTrackInfo(track)
        return api.addUserManga(track)
    }

    override suspend fun update(
        track: Track,
        setToRead: Boolean,
    ): Track {
        updateTrackStatus(track, setToRead, setToComplete = true, mustReadToComplete = false)
        return api.updateUserManga(track)
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.getManga(track)
        val readContent = api.getRead(track)

        track.copyPersonalFrom(remoteTrack)
        track.media_id = remoteTrack.media_id
        track.library_id = remoteTrack.library_id

        return if (readContent != null) {
            track.score = readContent.score.toFloat()
            track.last_chapter_read = readContent.chapters.toFloat()
            track.started_reading_date = (readContent.startDate ?: 0L) * 1000
            track.finished_reading_date = (readContent.endDate ?: 0L) * 1000
            update(track)
        } else {
            add(track)
        }
    }

    override fun canRemoveFromService(): Boolean = true

    override suspend fun removeFromService(track: Track): Boolean =
        try {
            api.deleteUserManga(track)
            true
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    override suspend fun search(query: String): List<TrackSearch> = api.searchManga(query)

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters

        val readContent = api.getRead(track) ?: throw Exception("Could not find manga")

        track.score = readContent.score.toFloat()
        track.last_chapter_read = readContent.chapters.toFloat()
        track.status = toTrackStatus(readContent.status)
        track.started_reading_date = (readContent.startDate ?: 0L) * 1000
        track.finished_reading_date = (readContent.endDate ?: 0L) * 1000

        return track
    }

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    suspend fun login(reference: String) {
        try {
            val oauth = api.accessToken(reference)
            interceptor.setAuth(oauth)
            val user = api.getCurrentUser()
            saveCredentials(user.username, oauth.accessToken)
        } catch (e: Exception) {
            logout()
            throw e
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: HKOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): HKOAuth? =
        try {
            json.decodeFromString<HKOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
}
