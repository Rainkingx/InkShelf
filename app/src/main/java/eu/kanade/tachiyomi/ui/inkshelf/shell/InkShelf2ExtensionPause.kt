package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Reversible, app-level extension pause.
 *
 * It hides all extension source IDs using J2K's original hidden-source setting,
 * and retains package-level status so updated extensions with changed IDs
 * remain paused. Neither extension APKs nor comics/progress are deleted.
 */
internal object InkShelf2ExtensionPause {
    private val prefs: PreferencesHelper by lazy { Injekt.get() }
    private val manager: ExtensionManager by lazy { Injekt.get() }

    fun pausedPackages(): Set<String> = prefs.inkShelf2PausedExtensions().get()

    fun pausedSourceIds(): Set<Long> {
        val disabled = pausedPackages()
        if (disabled.isEmpty()) return emptySet()
        return manager.installedExtensionsFlow.value
            .filter { it.pkgName in disabled }
            .flatMap { extension -> extension.sources.map { it.id } }
            .toSet()
    }

    fun isPausedSource(sourceId: Long): Boolean {
        val pkg = manager.getPackageName(sourceId) ?: return false
        return pkg in pausedPackages()
    }

    /**
     * When resuming, unhide only IDs we hid ourselves, not sources the user
     * had already chosen to hide before pausing the extension.
     */
    fun setPaused(extension: Extension.Installed, paused: Boolean) {
        val pkgNames = prefs.inkShelf2PausedExtensions().get()
        val ids = extension.sources.map { it.id.toString() }.toSet()
        val hidden = prefs.hiddenSources().get()
        val managed = prefs.inkShelf2PauseHiddenSources().get()
        if (paused) {
            prefs.inkShelf2PauseHiddenSources().set(managed + (ids - hidden))
            prefs.hiddenSources().set(hidden + ids)
            prefs.inkShelf2PausedExtensions().set(pkgNames + extension.pkgName)
        } else {
            prefs.hiddenSources().set(hidden - (managed intersect ids))
            prefs.inkShelf2PauseHiddenSources().set(managed - ids)
            prefs.inkShelf2PausedExtensions().set(pkgNames - extension.pkgName)
        }
    }
}
