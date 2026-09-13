package eu.kanade.tachiyomi.ui.library

import java.util.Locale

/** Shared aliases used by the modern Library tabs and status-category helpers. */
internal object ModernLibraryStatus {
    val READING = setOf("reading", "ongoing")
    val COMPLETED = setOf("completed", "complete")
    val ON_HOLD = setOf("on hold", "onhold", "paused")
    val PLAN_TO_READ = setOf("plan to read", "plantoread", "planned")

    val ALL = READING + COMPLETED + ON_HOLD + PLAN_TO_READ
    val EXCLUDED_FROM_CONTINUE_READING = COMPLETED + ON_HOLD + PLAN_TO_READ

    fun normalize(name: String): String =
        name
            .trim()
            .lowercase(Locale.ROOT)
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
}
