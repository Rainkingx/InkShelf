package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import eu.kanade.tachiyomi.ui.source.BrowseController
import kotlinx.coroutines.CancellationException

/**
 * Repair recommendations after an extension is paused or disabled.
 * Uses the existing J2K source discovery engine, without creating favourites,
 * switching sources on saved comics, or launching network work on every visit.
 */
internal object InkShelf2RecommendationRecovery {
    const val FALLBACK_REASON = "Popular on your enabled sources"
    private const val PREFS = "inkshelf2_recommendation_recovery"
    private const val COOLDOWN_MS = 30 * 60 * 1000L

    data class Missing(val trending: Boolean, val newest: Boolean, val personal: Boolean) {
        val any: Boolean get() = trending || newest || personal
        val feeds: Boolean get() = trending || newest
    }

    fun missing(
        discover: InkShelf2Engine.Discover,
        enabledSourceIds: Set<Long>,
    ): Missing {
        if (enabledSourceIds.isEmpty()) return Missing(false, false, false)
        // A couple of cached cards from a functioning source is enough to
        // avoid an expensive startup refresh. Never count paused XOXO cards.
        return Missing(
            trending = discover.trending.count { it.source in enabledSourceIds } < 2,
            newest = discover.newest.count { it.source in enabledSourceIds } < 2,
            personal = discover.recommendationDetails.count {
                it.manga.source in enabledSourceIds && it.reason != FALLBACK_REASON
            } < 2,
        )
    }

    /**
     * One recovery per source configuration per cooldown window. Changing the
     * enabled extension set can try immediately; a failing source cannot
     * trigger repeated searches on every recomposition or cold launch.
     */
    fun claimAttempt(
        context: Context,
        enabledSourceIds: Set<Long>,
        missing: Missing,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (enabledSourceIds.isEmpty() || !missing.any) return false
        val signature = enabledSourceIds.sorted().joinToString(",")
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSignature = prefs.getString("sources", "").orEmpty()
        val lastAttempt = prefs.getLong("last_attempt", 0L)
        if (lastSignature == signature && now - lastAttempt in 0L until COOLDOWN_MS) return false
        prefs.edit().putString("sources", signature).putLong("last_attempt", now).apply()
        return true
    }

    suspend fun refresh(context: Context, missing: Missing): Boolean {
        var refreshed = false
        if (missing.feeds) {
            refreshed = try {
                BrowseController.refreshDiscoverFeedsInBackground(context.applicationContext)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
        }
        if (missing.personal) {
            // An XOXO-only cached batch must not suppress healthy mirror
            // titles through the normal 90-day manual-refresh exclusion.
            val personal = try {
                BrowseController.refreshPickedForYouForInkShelf2(
                    context = context.applicationContext,
                    force = true,
                    sourceChangeRecovery = true,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            refreshed = personal || refreshed
        }
        return refreshed
    }
}
