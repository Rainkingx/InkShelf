package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toApiStatus(): Int =
    when (status) {
        Bangumi.PLAN_TO_READ, Bangumi.COMPLETED, Bangumi.READING, Bangumi.ON_HOLD, Bangumi.DROPPED -> status
        else -> throw NotImplementedError("Unknown status: $status")
    }
