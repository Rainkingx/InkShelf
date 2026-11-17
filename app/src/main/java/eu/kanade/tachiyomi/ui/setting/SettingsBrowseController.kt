package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCredentials
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCovers
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.migration.MigrationController
import eu.kanade.tachiyomi.ui.source.browse.repos.RepoController
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy
import kotlinx.coroutines.launch

class SettingsBrowseController : SettingsController() {
    val sourceManager: SourceManager by injectLazy()
    var updatedExtNotifPref: SwitchPreferenceCompat? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.browse

            preferenceCategory {
                switchPreference {
                    bindTo(preferences.hideInLibraryItems())
                    titleRes = R.string.hide_in_library_items
                }
                switchPreference {
                    bindTo(preferences.showDuplicateInLibraryItems())
                    titleRes = R.string.show_duplicate_in_library_items
                    summaryRes = R.string.show_duplicate_in_library_items_summary
                }
            }

            preferenceCategory {
                titleRes = R.string.extensions
                switchPreference {
                    key = PreferenceKeys.automaticExtUpdates
                    titleRes = R.string.check_for_extension_updates
                    defaultValue = true

                    onChange {
                        it as Boolean
                        ExtensionUpdateJob.setupTask(context, it)
                        true
                    }
                }
                preference {
                    key = "pref_edit_extension_repos"

                    val repoCount = preferences.extensionRepos().get().count()
                    titleRes = R.string.extension_repos
                    if (repoCount > 0) summary = context.resources.getQuantityString(R.plurals.num_repos, repoCount, repoCount)

                    onClick { router.pushController(RepoController().withFadeTransaction()) }
                }
                if (ExtensionManager.canAutoInstallUpdates()) {
                    val intPref =
                        intListPreference(activity) {
                            key = PreferenceKeys.autoUpdateExtensions
                            titleRes = R.string.auto_update_extensions
                            entryRange = 0..2
                            entriesRes =
                                arrayOf(
                                    R.string.over_any_network,
                                    R.string.over_wifi_only,
                                    R.string.dont_auto_update,
                                )
                            defaultValue = AppDownloadInstallJob.ONLY_ON_UNMETERED
                        }
                    val infoPref =
                        if (preferences.extensionInstaller().get() != ExtensionInstaller.SHIZUKU) {
                            infoPreference(R.string.some_extensions_may_not_update)
                        } else {
                            null
                        }
                    val switchPref =
                        switchPreference {
                            key = "notify_ext_updated"
                            isPersistent = false
                            titleRes = R.string.notify_extension_updated
                            isChecked =
                                Notifications.isNotificationChannelEnabled(
                                    context,
                                    Notifications.CHANNEL_EXT_UPDATED,
                                )
                            updatedExtNotifPref = this
                            onChange {
                                false
                            }
                            onClick {
                                val intent =
                                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                                        putExtra(
                                            Settings.EXTRA_CHANNEL_ID,
                                            Notifications.CHANNEL_EXT_UPDATED,
                                        )
                                    }
                                startActivity(intent)
                            }
                        }
                    preferences.automaticExtUpdates().asImmediateFlowIn(viewScope) { value ->
                        arrayOf(intPref, infoPref, switchPref).forEach { it?.isVisible = value }
                    }
                }
            }

            preferenceCategory {
                title = "Comic issue artwork"
                preference {
                    key = "inkshelf_metron_api_token_settings"
                    title = "Metron API token"
                    summary = if (InkShelfMetronCredentials.get(context).isNullOrBlank()) {
                        "Tap to add your private token and enable issue cover lookups"
                    } else {
                        "Configured — tap to replace or clear your token"
                    }
                    onClick {
                        val tokenField = EditText(context).apply {
                            hint = "Paste Metron API token"
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            setSingleLine(true)
                            val pad = (20 * resources.displayMetrics.density).toInt()
                            setPadding(pad, pad, pad, pad)
                        }
                        AlertDialog.Builder(context)
                            .setTitle("Metron API token")
                            .setMessage("Your token stays encrypted on this device. InkShelf sends it only to metron.cloud for metadata lookups. Leave this blank to clear it.")
                            .setView(tokenField)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setNeutralButton("Clear") { _, _ ->
                                InkShelfMetronCredentials.clear(context)
                                summary = "Tap to add your private token and enable issue cover lookups"
                                Toast.makeText(context, "Metron token cleared", Toast.LENGTH_SHORT).show()
                            }
                            .setPositiveButton("Save") { _, _ ->
                                val value = tokenField.text.toString().trim()
                                if (InkShelfMetronCredentials.save(context, value)) {
                                    summary = if (value.isBlank()) {
                                        "Tap to add your private token and enable issue cover lookups"
                                    } else {
                                        "Configured — tap to replace or clear your token"
                                    }
                                    Toast.makeText(context, "Metron setting saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Could not save Metron token", Toast.LENGTH_LONG).show()
                                }
                            }
                            .show()
                    }
                }
                preference {
                    key = "inkshelf_metron_test_connection"
                    title = "Test Metron connection"
                    summary = "Check your token and see whether Absolute Wonder Woman (2024) has matching covers"
                    onClick {
                        Toast.makeText(context, "Checking Metron…", Toast.LENGTH_SHORT).show()
                        viewScope.launch {
                            val message = InkShelfMetronCovers.testConnection(context)
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                infoPreference(R.string.inkshelf_metron_info)
            }

            preferenceCategory {
                titleRes = R.string.label_global_search
                twoButtonPreference {
                    bindTo(preferences.onlySearchPinned())
                    titleRes = R.string.sources_to_search
                    startTextRes = R.string.all_enabled
                    endTextRes = R.string.pinned_only
                }
            }

            preferenceCategory {
                titleRes = R.string.migration
                // Only show this if someone has mass migrated manga once

                preference {
                    titleRes = R.string.source_migration
                    onClick { router.pushController(MigrationController().withFadeTransaction()) }
                }
                if (preferences.skipPreMigration().get() ||
                    preferences
                        .migrationSources()
                        .isSet()
                ) {
                    switchPreference {
                        key = PreferenceKeys.skipPreMigration
                        titleRes = R.string.skip_pre_migration
                        summaryRes = R.string.use_last_saved_migration_preferences
                        defaultValue = false
                    }
                }
                preference {
                    key = "match_pinned_sources"
                    titleRes = R.string.match_pinned_sources
                    summaryRes = R.string.only_enable_pinned_for_migration
                    onClick {
                        val ogSources = preferences.migrationSources().get()
                        val pinnedSources =
                            preferences.pinnedCatalogues().get().joinToString("/")
                        preferences.migrationSources().set(pinnedSources)
                        (activity as? MainActivity)?.setUndoSnackBar(
                            view?.snack(
                                R.string.migration_sources_changed,
                            ) {
                                setAction(R.string.undo) {
                                    preferences.migrationSources().set(ogSources)
                                }
                            },
                        )
                    }
                }

                preference {
                    key = "match_enabled_sources"
                    titleRes = R.string.match_enabled_sources
                    summaryRes = R.string.only_enable_enabled_for_migration
                    onClick {
                        val ogSources = preferences.migrationSources().get()
                        val languages = preferences.enabledLanguages().get()
                        val hiddenCatalogues = preferences.hiddenSources().get()
                        val enabledSources =
                            sourceManager
                                .getCatalogueSources()
                                .filter { it.lang in languages }
                                .filterNot { it.id.toString() in hiddenCatalogues }
                                .sortedBy { "(${it.lang}) ${it.name}" }
                                .joinToString("/") { it.id.toString() }
                        preferences.migrationSources().set(enabledSources)
                        (activity as? MainActivity)?.setUndoSnackBar(
                            view?.snack(
                                R.string.migration_sources_changed,
                            ) {
                                setAction(R.string.undo) {
                                    preferences.migrationSources().set(ogSources)
                                }
                            },
                        )
                    }
                }

                infoPreference(R.string.you_can_migrate_in_library)
            }

            preferenceCategory {
                titleRes = R.string.nsfw_sources

                switchPreference {
                    key = PreferenceKeys.showNsfwSource
                    titleRes = R.string.show_in_sources_and_extensions
                    summaryRes = R.string.requires_app_restart
                    defaultValue = true
                }
                infoPreference(R.string.does_not_prevent_unofficial_nsfw)
            }
        }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatedExtNotifPref?.isChecked = Notifications.isNotificationChannelEnabled(activity, Notifications.CHANNEL_EXT_UPDATED)
    }
}
