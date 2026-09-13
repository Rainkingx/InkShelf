package eu.kanade.tachiyomi.data.preference

import android.content.SharedPreferences
import androidx.core.content.edit
import timber.log.Timber

/**
 * Wraps [delegate] so a value persisted under a key with a different primitive type than the
 * caller expects (e.g. a backup restored from another Tachiyomi fork that declares the same
 * key name as a different type) resets to the caller's default instead of crashing.
 *
 * Android's default SharedPreferencesImpl does an unchecked cast in every typed getter and
 * throws a ClassCastException in that situation. flow-preferences' IntPreference/BooleanPreference/etc.
 * call straight through to that getter with no guard of their own, so this needs to sit below it.
 */
class TypeSafeSharedPreferences(
    private val delegate: SharedPreferences,
) : SharedPreferences by delegate {
    override fun getInt(
        key: String,
        defValue: Int,
    ) = safeGet(key, defValue) { delegate.getInt(key, defValue) }

    override fun getLong(
        key: String,
        defValue: Long,
    ) = safeGet(key, defValue) { delegate.getLong(key, defValue) }

    override fun getFloat(
        key: String,
        defValue: Float,
    ) = safeGet(key, defValue) { delegate.getFloat(key, defValue) }

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ) = safeGet(key, defValue) { delegate.getBoolean(key, defValue) }

    override fun getString(
        key: String,
        defValue: String?,
    ) = safeGet(key, defValue) { delegate.getString(key, defValue) }

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ) = safeGet(key, defValues) { delegate.getStringSet(key, defValues) }

    private inline fun <T> safeGet(
        key: String,
        default: T,
        read: () -> T,
    ): T =
        try {
            read()
        } catch (e: ClassCastException) {
            // Logged at error level so it lands in the shared crash log, which is filtered to
            // logcat *:E - this is the only trace that a preference silently reset
            Timber.e(e, "Preference \"$key\" had an unexpected type; resetting to default")
            delegate.edit { remove(key) }
            default
        }
}
