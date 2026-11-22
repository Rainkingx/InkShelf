package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context

/**
 * Optional, device-local InkShelf 2.0 profile. This is NOT an online account,
 * a login system or another comic database. The existing J2K library and
 * reader remain the single sources of truth.
 */
internal class InkShelf2ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "inkshelf2_local_profile_v1",
        Context.MODE_PRIVATE,
    )

    fun displayName(): String = prefs.getString("display_name", null).orEmpty().trim()
    fun hasCompletedWelcome(): Boolean = prefs.getBoolean("welcome_completed", false)

    fun setName(name: String) {
        // A short display name only, not credentials or a cloud identity.
        prefs.edit().putString("display_name", name.trim().take(35)).apply()
    }

    fun completeWelcome() {
        prefs.edit().putBoolean("welcome_completed", true).apply()
    }
}
