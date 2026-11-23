package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context

/**
 * A lightweight, local-only unread dot for The Pulse. No notifications,
 * tracking IDs, or extra background network requests.
 */
internal object InkShelf2PulseStatus {
    private const val PREFS = "inkshelf2_pulse_status"
    private const val LAST_VIEWED_AT = "last_viewed_at"

    fun markViewed(context: Context, timestamp: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(LAST_VIEWED_AT, timestamp).apply()
    }

    fun hasUnread(context: Context, articles: List<InkShelf2PulseArticle>): Boolean {
        val viewedAt = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_VIEWED_AT, 0L)
        return hasNewerArticles(articles, viewedAt)
    }

    /** Only dated news stories can turn on the indicator; videos do not. */
    internal fun hasNewerArticles(
        articles: List<InkShelf2PulseArticle>,
        viewedAt: Long,
    ): Boolean = articles.any { article ->
        !article.isVideo && article.publishedAt > 0 && article.publishedAt > viewedAt
    }
}
