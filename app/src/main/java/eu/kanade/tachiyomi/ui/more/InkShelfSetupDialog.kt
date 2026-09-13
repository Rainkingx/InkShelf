package eu.kanade.tachiyomi.ui.more

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.isVisible
import coil.Coil
import coil.dispose
import coil.request.CachePolicy
import coil.request.ImageRequest
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.InkshelfSetupDialogBinding
import eu.kanade.tachiyomi.databinding.InkshelfStarterComicItemBinding
import eu.kanade.tachiyomi.databinding.InkshelfStarterLibraryDialogBinding
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.Locale

/**
 * InkShelf's first-run source setup.
 *
 * This intentionally never removes extensions, library entries, downloads, history, or progress.
 * Re-running it from More only repairs the repo, enables the selected sources, and installs or
 * updates missing recommended extensions.
 */
class InkShelfSetupDialog(
    private val activity: MainActivity,
    private val firstRun: Boolean,
) : Dialog(activity) {
    private val binding = InkshelfSetupDialogBinding.inflate(activity.layoutInflater)
    private val preferences: PreferencesHelper by injectLazy()
    private val extensionManager: ExtensionManager by injectLazy()
    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var style = ReadingStyle.WESTERN
    private var waitingForInstallPermission = false
    private var starterBinding: InkshelfStarterLibraryDialogBinding? = null
    private val starterRows = mutableListOf<StarterRow>()

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setCanceledOnTouchOutside(false)
        setCancelable(false)

        selectStyle(ReadingStyle.WESTERN)

        binding.westernCard.setOnClickListener { selectStyle(ReadingStyle.WESTERN) }
        binding.westernCheck.setOnClickListener { selectStyle(ReadingStyle.WESTERN) }

        binding.mangaCard.setOnClickListener { selectStyle(ReadingStyle.MANGA) }
        binding.mangaCheck.setOnClickListener { selectStyle(ReadingStyle.MANGA) }

        binding.bothCard.setOnClickListener { selectStyle(ReadingStyle.BOTH) }
        binding.bothCheck.setOnClickListener { selectStyle(ReadingStyle.BOTH) }

        binding.installSelectedButton.setOnClickListener { installSelected() }
        binding.skipButton.setOnClickListener {
            markSetupComplete()
            dismiss()
        }
    }

    override fun show() {
        super.show()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.black)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            statusBarColor = Color.BLACK
            navigationBarColor = Color.BLACK
        }
        updateInstallPermissionUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus || !waitingForInstallPermission) return

        if (!needsPackageInstallPermission()) {
            waitingForInstallPermission = false
            binding.installSelectedButton.text = "Install selected"
            showStatus("Permission granted. Starting source installation…")
            binding.root.post { installSelected() }
        } else {
            waitingForInstallPermission = false
            setBusy(false)
            updateInstallPermissionUi()
            showStatus("Allow InkShelf to install unknown apps, then tap the button again.")
        }
    }

    override fun dismiss() {
        super.dismiss()
        scope.cancel()
    }

    private fun selectStyle(newStyle: ReadingStyle) {
        style = newStyle

        binding.westernCheck.isChecked = newStyle == ReadingStyle.WESTERN
        binding.mangaCheck.isChecked = newStyle == ReadingStyle.MANGA
        binding.bothCheck.isChecked = newStyle == ReadingStyle.BOTH

        val showWestern = newStyle == ReadingStyle.WESTERN || newStyle == ReadingStyle.BOTH
        val showManga = newStyle == ReadingStyle.MANGA || newStyle == ReadingStyle.BOTH

        binding.batCaveRow.isVisible = showWestern
        binding.readComicsOnlineRow.isVisible = showWestern
        binding.xoxoComicsRow.isVisible = showWestern

        binding.mangaDexRow.isVisible = showManga
        binding.comixRow.isVisible = showManga
        binding.mangaFireRow.isVisible = showManga

        // Selecting a style restores its recommended starter set. Users can still untick
        // individual sources afterwards.
        if (showWestern) {
            binding.batCaveCheck.isChecked = true
            binding.readComicsOnlineCheck.isChecked = true
            binding.xoxoComicsCheck.isChecked = true
        }
        if (showManga) {
            binding.mangaDexCheck.isChecked = true
            binding.comixCheck.isChecked = true
            binding.mangaFireCheck.isChecked = true
        }
    }

    private fun installSelected() {
        val selected = selectedSourceNames()

        // With Android's normal package installer, request "Install unknown apps"
        // permission BEFORE downloading or launching the first extension APK.
        if (selected.isNotEmpty() && needsPackageInstallPermission()) {
            requestPackageInstallPermission()
            return
        }

        setBusy(true, "Preparing recommended sources…")

        scope.launch {
            try {
                if (binding.repoCheck.isChecked) {
                    val resolvedRepo =
                        withContext(Dispatchers.IO) {
                            ExtensionApi().validateRepo(REPO_URL)
                        }
                    val current = preferences.extensionRepos().get()
                    preferences.extensionRepos().set((current - REPO_URL) + resolvedRepo)
                }

                // English is required for the recommended Western/Manga source set.
                preferences.enabledLanguages().set(preferences.enabledLanguages().get() + "en")

                extensionManager.findAvailableExtensions()
                val available = extensionManager.availableExtensionsFlow.value

                val matched =
                    selected.mapNotNull { sourceName ->
                        findMatchingExtension(sourceName, available)?.let { sourceName to it }
                    }

                if (selected.isNotEmpty() && matched.isEmpty()) {
                    setBusy(false)
                    showStatus("I couldn't find the selected sources in the repository. Check your connection and try again.")
                    return@launch
                }

                // Re-enable the exact recommended sources if they were previously hidden.
                val sourceIds =
                    matched
                        .flatMap { (target, extension) ->
                            val exactSources =
                                extension.sources.filter { matchesTarget(target, it.name) }
                            when {
                                exactSources.isNotEmpty() -> exactSources
                                matchesTarget(target, extension.name) -> extension.sources
                                else -> emptyList()
                            }
                        }.map { it.id.toString() }
                        .toSet()

                if (sourceIds.isNotEmpty()) {
                    preferences.hiddenSources().set(preferences.hiddenSources().get() - sourceIds)
                }

                val installedByPackage =
                    extensionManager.installedExtensionsFlow.value.associateBy { it.pkgName }

                val extensionsToInstall =
                    matched
                        .map { it.second }
                        .distinctBy { it.pkgName }
                        .filter { availableExtension ->
                            val installed = installedByPackage[availableExtension.pkgName]
                            installed == null ||
                                availableExtension.versionCode > installed.versionCode ||
                                availableExtension.libVersion > installed.libVersion
                        }

                val missing =
                    selected.filter { target ->
                        matched.none { (matchedTarget, _) -> matchedTarget == target }
                    }

                var failedInstalls = 0

                // ExtensionInstallerJob is for bulk UPDATES and filters out packages that
                // are not already installed. For onboarding we need to install brand-new
                // extensions, so use the same direct installer path as J2K's Extensions UI.
                //
                // Install sequentially so Android's package-installer confirmation windows
                // don't stack on top of each other.
                extensionsToInstall.forEachIndexed { index, extension ->
                    showStatus(
                        "Installing ${extension.name} (${index + 1}/${extensionsToInstall.size})… " +
                            "Approve the Android install prompt if one appears.",
                    )

                    var finalStep: InstallStep? = null

                    extensionManager
                        .installExtension(
                            ExtensionManager.ExtensionInfo(extension),
                            scope,
                        ).collect { installInfo ->
                            finalStep = installInfo.first

                            when (installInfo.first) {
                                InstallStep.Downloading ->
                                    showStatus(
                                        "Downloading ${extension.name} " +
                                            "(${index + 1}/${extensionsToInstall.size})…",
                                    )
                                InstallStep.Installing, InstallStep.Loading ->
                                    showStatus(
                                        "Installing ${extension.name} " +
                                            "(${index + 1}/${extensionsToInstall.size})… " +
                                            "Approve the Android install prompt if one appears.",
                                    )
                                else -> Unit
                            }
                        }

                    if (finalStep != InstallStep.Installed && finalStep != InstallStep.Done) {
                        failedInstalls++
                    }
                }

                // Refresh once installs finish so the newly-installed sources become available
                // to Discover immediately.
                if (extensionsToInstall.isNotEmpty()) {
                    extensionManager.findAvailableExtensions()
                }

                val message =
                    when {
                        failedInstalls > 0 ->
                            "Setup finished, but $failedInstalls extension(s) did not install. You can run New User Setup again from More."
                        missing.isNotEmpty() ->
                            "Setup finished. Not found in the repository: ${missing.joinToString()}."
                        extensionsToInstall.isNotEmpty() ->
                            "Your selected sources are installed and ready."
                        else ->
                            "Your recommended sources are already installed and ready."
                    }

                setBusy(false)
                showStatus(message)
                activity.toast(message)

                if (firstRun) {
                    // Keep Starter Library as the final page of the genuine first-run flow.
                    // More -> New User Setup remains source repair only, so existing installs
                    // never get starter titles added unexpectedly.
                    delay(350)
                    showStarterLibraryStep(selected)
                } else {
                    markSetupComplete()
                    delay(900)
                    dismiss()
                }
            } catch (e: Throwable) {
                setBusy(false)
                showStatus("Couldn't reach the source repository. Check your connection and try again.")
            }
        }
    }

    private fun showStarterLibraryStep(preferredSourceNames: List<String>) {
        val starter = InkshelfStarterLibraryDialogBinding.inflate(activity.layoutInflater)
        starterBinding = starter
        starterRows.clear()
        setContentView(starter.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        starter.addStarterButton.setOnClickListener { addSelectedStarterComics() }
        starter.emptyLibraryButton.setOnClickListener { finishStarterSetupEmpty() }

        setStarterBusy(true, "Finding a few great starter comics…")

        scope.launch {
            val resolved = resolveStarterComics(preferredSourceNames)
            if (!isShowing || starterBinding !== starter) return@launch

            renderStarterComics(resolved)
            starter.starterProgress.isVisible = false

            if (resolved.isEmpty()) {
                starter.starterStatus.text =
                    "I couldn't resolve any starter comics from your enabled catalogues right now. You can continue with an empty Library and add anything you like from Discover."
                starter.starterStatus.isVisible = true
                starter.addStarterButton.isEnabled = false
                starter.addStarterButton.text = "No starter comics found"
            } else {
                starter.starterStatus.text =
                    "These are real catalogue results. Untick anything you don't want before adding them."
                starter.starterStatus.isVisible = true
                updateStarterButton()
            }

            starter.emptyLibraryButton.isEnabled = true
        }
    }

    private suspend fun resolveStarterComics(preferredSourceNames: List<String>): List<Manga> {
        val sources = waitForStarterSources(preferredSourceNames)
        if (sources.isEmpty()) return emptyList()

        val results = mutableListOf<Manga>()
        val seeds = starterSeeds(style)

        for ((index, seed) in seeds.withIndex()) {
            withContext(Dispatchers.Main) {
                starterBinding?.starterStatus?.apply {
                    text = "Finding ${seed.label} (${index + 1} of ${seeds.size})…"
                    isVisible = true
                }
            }

            val resolved = resolveStarterSeed(seed, sources, results)
            if (resolved != null) results += resolved
        }

        return results.take(STARTER_LIMIT)
    }

    private suspend fun waitForStarterSources(preferredSourceNames: List<String>): List<CatalogueSource> {
        repeat(12) {
            val sources = orderedStarterSources(preferredSourceNames)
            if (sources.isNotEmpty()) return sources
            delay(250)
        }
        return orderedStarterSources(preferredSourceNames)
    }

    private fun orderedStarterSources(preferredSourceNames: List<String>): List<CatalogueSource> {
        val hiddenSources = preferences.hiddenSources().get()
        val enabledEnglish =
            sourceManager
                .getCatalogueSources()
                .filter { source ->
                    source.lang == "en" && source.id.toString() !in hiddenSources
                }

        val preferred =
            enabledEnglish.filter { source ->
                preferredSourceNames.any { target -> matchesTarget(target, source.name) }
            }

        val base = if (preferred.isNotEmpty()) preferred else enabledEnglish

        return base.sortedWith(
            compareBy<CatalogueSource> { source ->
                preferredSourceNames
                    .indexOfFirst { target -> matchesTarget(target, source.name) }
                    .let { if (it < 0) Int.MAX_VALUE else it }
            }.thenBy { it.name.lowercase(Locale.ROOT) },
        )
    }

    private suspend fun resolveStarterSeed(
        seed: StarterSeed,
        sources: List<CatalogueSource>,
        alreadyResolved: List<Manga>,
    ): Manga? {
        val matchingFamily =
            sources.filter { source ->
                val targets =
                    when (seed.kind) {
                        StarterKind.WESTERN -> WESTERN_SOURCE_NAMES
                        StarterKind.MANGA -> MANGA_SOURCE_NAMES
                    }
                targets.any { target -> matchesTarget(target, source.name) }
            }
        val searchSources = matchingFamily.ifEmpty { sources }

        for (query in seed.queries) {
            for (source in searchSources) {
                val page =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            withTimeoutOrNull(STARTER_SEARCH_TIMEOUT_MS) {
                                source.getSearchManga(1, query, source.getFilterList())
                            }
                        }.getOrNull()
                    } ?: continue

                val match = bestStarterMatch(page.mangas, seed) ?: continue
                val local =
                    withContext(Dispatchers.IO) {
                        networkToLocalStarterManga(match, source.id)
                    }

                val alreadyPicked =
                    alreadyResolved.any { existing ->
                        normalize(existing.title) == normalize(local.title)
                    }
                if (alreadyPicked) continue

                val alreadyInLibrary =
                    withContext(Dispatchers.IO) {
                        local.favorite || db.getDuplicateLibraryManga(local).executeAsBlocking() != null
                    }
                if (!alreadyInLibrary) return local
            }
        }
        return null
    }

    private fun bestStarterMatch(
        results: List<SManga>,
        seed: StarterSeed,
    ): SManga? {
        val acceptedQueries = seed.queries.map(::normalize)

        return results
            .mapNotNull { manga ->
                val candidate = normalize(manga.title)
                val score =
                    acceptedQueries.minOfOrNull { wanted ->
                        when {
                            candidate == wanted -> 0
                            candidate.startsWith(wanted) &&
                                candidate.drop(wanted.length).firstOrNull()?.isDigit() == true -> 1
                            candidate.startsWith(wanted) -> 2
                            candidate.contains(wanted) -> 3
                            else -> 100
                        }
                    } ?: 100

                if (score < 100) manga to score else null
            }.minByOrNull { it.second }
            ?.first
    }

    private fun networkToLocalStarterManga(
        sManga: SManga,
        sourceId: Long,
    ): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        } else if (!localManga.favorite) {
            localManga.copyFrom(sManga)
        }
        return localManga
    }

    private fun renderStarterComics(comics: List<Manga>) {
        val starter = starterBinding ?: return
        starter.starterList.removeAllViews()
        starterRows.clear()

        comics.forEach { manga ->
            val row =
                InkshelfStarterComicItemBinding.inflate(
                    activity.layoutInflater,
                    starter.starterList,
                    false,
                )

            row.starterTitle.text = manga.title
            row.starterCheck.isChecked = true
            row.starterCheck.setOnCheckedChangeListener { _, _ -> updateStarterButton() }
            row.root.setOnClickListener {
                row.starterCheck.isChecked = !row.starterCheck.isChecked
            }

            row.starterCover.dispose()
            if (!manga.thumbnail_url.isNullOrBlank()) {
                val request =
                    ImageRequest
                        .Builder(activity)
                        .data(manga)
                        .placeholder(android.R.color.transparent)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .target(CoverViewTarget(row.starterCover, row.starterCoverProgress))
                        .setParameter(MangaCoverFetcher.useCustomCover, false)
                        .crossfade(false)
                        .build()
                Coil.imageLoader(activity).enqueue(request)
            } else {
                row.starterCoverProgress.isVisible = false
            }

            starter.starterList.addView(row.root)
            starterRows += StarterRow(manga, row)
        }
    }

    private fun updateStarterButton() {
        val starter = starterBinding ?: return
        val count = starterRows.count { it.binding.starterCheck.isChecked }
        starter.addStarterButton.text =
            if (count == 1) "Add 1 to Library" else "Add $count to Library"
        starter.addStarterButton.isEnabled = count > 0
    }

    private fun addSelectedStarterComics() {
        val starter = starterBinding ?: return
        val selected =
            starterRows
                .filter { it.binding.starterCheck.isChecked }
                .map { it.manga }

        if (selected.isEmpty()) return

        setStarterBusy(true, "Adding ${selected.size} starter comics to your Library…")

        scope.launch {
            try {
                val added =
                    withContext(Dispatchers.IO) {
                        var addedCount = 0
                        selected.forEachIndexed { index, manga ->
                            val local = db.getManga(manga.url, manga.source).executeAsBlocking() ?: manga
                            val duplicate = db.getDuplicateLibraryManga(local).executeAsBlocking()

                            if (!local.favorite && duplicate == null) {
                                local.favorite = true
                                local.date_added = Date().time - index
                                db.insertManga(local).executeAsBlocking()
                                db.setMangaCategories(emptyList(), listOf(local))
                                addedCount++
                            }
                        }
                        addedCount
                    }

                markSetupComplete()
                val message =
                    when (added) {
                        0 -> "Your starter choices were already in the Library."
                        1 -> "Added 1 starter comic to your Library."
                        else -> "Added $added starter comics to your Library."
                    }
                setStarterBusy(false, message)
                activity.toast(message)
                delay(650)
                dismiss()
                activity.openDiscoverAfterInkShelfSetup()
            } catch (e: Throwable) {
                setStarterBusy(false, "I couldn't add those starter comics. Try again, or continue with an empty Library.")
            }
        }
    }

    private fun finishStarterSetupEmpty() {
        markSetupComplete()
        dismiss()
        activity.openDiscoverAfterInkShelfSetup()
    }

    private fun setStarterBusy(
        busy: Boolean,
        message: String? = null,
    ) {
        val starter = starterBinding ?: return
        starter.starterProgress.isVisible = busy
        starter.addStarterButton.isEnabled = !busy && starterRows.any { it.binding.starterCheck.isChecked }
        starter.emptyLibraryButton.isEnabled = !busy
        starterRows.forEach { it.binding.starterCheck.isEnabled = !busy }
        if (message != null) {
            starter.starterStatus.text = message
            starter.starterStatus.isVisible = true
        }
    }

    private fun starterSeeds(readingStyle: ReadingStyle): List<StarterSeed> =
        when (readingStyle) {
            ReadingStyle.WESTERN -> WESTERN_STARTERS
            ReadingStyle.MANGA -> MANGA_STARTERS
            ReadingStyle.BOTH -> MIXED_STARTERS
        }

    private fun needsPackageInstallPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            preferences.extensionInstaller().get() == ExtensionInstaller.PACKAGE_INSTALLER &&
            !activity.packageManager.canRequestPackageInstalls()

    private fun requestPackageInstallPermission() {
        waitingForInstallPermission = true
        setBusy(false)
        binding.installSelectedButton.text = "Allow extension installs"
        showStatus(
            "Before installing comic sources, Android needs permission for InkShelf to install extension APKs. " +
                "Enable “Allow from this source”, then return to InkShelf.",
        )

        val intent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )

        runCatching { activity.startActivity(intent) }
            .onFailure {
                waitingForInstallPermission = false
                showStatus(
                    "Open Android Settings → Apps → Special app access → Install unknown apps → InkShelf, " +
                        "then enable “Allow from this source”.",
                )
            }
    }

    private fun updateInstallPermissionUi() {
        if (needsPackageInstallPermission()) {
            binding.installSelectedButton.text = "Allow extension installs"
        } else {
            binding.installSelectedButton.text = "Install selected"
        }
    }

    private fun selectedSourceNames(): List<String> =
        buildList {
            if (binding.batCaveRow.isVisible && binding.batCaveCheck.isChecked) add("BatCave")
            if (binding.readComicsOnlineRow.isVisible && binding.readComicsOnlineCheck.isChecked) add("Read Comics Online")
            if (binding.xoxoComicsRow.isVisible && binding.xoxoComicsCheck.isChecked) add("XOXO Comics")

            if (binding.mangaDexRow.isVisible && binding.mangaDexCheck.isChecked) add("MangaDex")
            if (binding.comixRow.isVisible && binding.comixCheck.isChecked) add("Comix")
            if (binding.mangaFireRow.isVisible && binding.mangaFireCheck.isChecked) add("MangaFire")
        }

    private fun findMatchingExtension(
        target: String,
        available: List<Extension.Available>,
    ): Extension.Available? {
        val exactPackage = SOURCE_PACKAGES[target]

        // Prefer the exact Keiyoushi package for sources where we know it.
        if (exactPackage != null) {
            available.firstOrNull { it.pkgName == exactPackage }?.let { return it }
        }

        // Fallback is deliberately exact-name only. The previous substring matching could
        // accidentally select a different extension with a vaguely similar name.
        return available.firstOrNull { extension ->
            matchesTarget(target, extension.name) ||
                extension.sources.any { source -> matchesTarget(target, source.name) }
        }
    }

    private fun matchesTarget(
        target: String,
        candidate: String,
    ): Boolean {
        val aliases = SOURCE_ALIASES[target].orEmpty() + normalize(target)
        val normalizedCandidate = normalize(candidate)
        return normalizedCandidate in aliases
    }

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "")

    private fun setBusy(
        busy: Boolean,
        message: String? = null,
    ) {
        binding.setupProgress.isVisible = busy
        binding.installSelectedButton.isEnabled = !busy
        binding.skipButton.isEnabled = !busy
        binding.westernCard.isEnabled = !busy
        binding.mangaCard.isEnabled = !busy
        binding.bothCard.isEnabled = !busy
        if (message != null) showStatus(message)
    }

    private fun showStatus(message: String) {
        binding.setupStatus.isVisible = true
        binding.setupStatus.text = message
    }

    private fun markSetupComplete() {
        activity
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
    }

    private data class StarterSeed(
        val label: String,
        val queries: List<String>,
        val kind: StarterKind,
    )

    private enum class StarterKind {
        WESTERN,
        MANGA,
    }

    private data class StarterRow(
        val manga: Manga,
        val binding: InkshelfStarterComicItemBinding,
    )

    private enum class ReadingStyle {
        WESTERN,
        MANGA,
        BOTH,
    }

    companion object {
        const val REPO_URL = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"

        private const val PREF_FILE = "inkshelf_onboarding"
        private const val KEY_SETUP_COMPLETE = "source_setup_complete"
        private const val STARTER_LIMIT = 6
        private const val STARTER_SEARCH_TIMEOUT_MS = 6_000L

        private val WESTERN_STARTERS =
            listOf(
                StarterSeed("Batman", listOf("Batman"), StarterKind.WESTERN),
                StarterSeed("Spider-Man", listOf("Spider-Man", "Amazing Spider-Man"), StarterKind.WESTERN),
                StarterSeed("X-Men", listOf("X-Men"), StarterKind.WESTERN),
                StarterSeed("Invincible", listOf("Invincible"), StarterKind.WESTERN),
                StarterSeed("Saga", listOf("Saga"), StarterKind.WESTERN),
                StarterSeed(
                    "Something Is Killing the Children",
                    listOf("Something Is Killing the Children"),
                    StarterKind.WESTERN,
                ),
            )

        private val MANGA_STARTERS =
            listOf(
                StarterSeed("One Piece", listOf("One Piece"), StarterKind.MANGA),
                StarterSeed("Chainsaw Man", listOf("Chainsaw Man"), StarterKind.MANGA),
                StarterSeed("Jujutsu Kaisen", listOf("Jujutsu Kaisen"), StarterKind.MANGA),
                StarterSeed("SPY x FAMILY", listOf("SPY x FAMILY", "Spy x Family"), StarterKind.MANGA),
                StarterSeed(
                    "Frieren",
                    listOf("Frieren: Beyond Journey's End", "Frieren", "Sousou no Frieren"),
                    StarterKind.MANGA,
                ),
                StarterSeed("Berserk", listOf("Berserk"), StarterKind.MANGA),
            )

        private val MIXED_STARTERS =
            listOf(
                StarterSeed("Batman", listOf("Batman"), StarterKind.WESTERN),
                StarterSeed("Invincible", listOf("Invincible"), StarterKind.WESTERN),
                StarterSeed("Saga", listOf("Saga"), StarterKind.WESTERN),
                StarterSeed("One Piece", listOf("One Piece"), StarterKind.MANGA),
                StarterSeed("Chainsaw Man", listOf("Chainsaw Man"), StarterKind.MANGA),
                StarterSeed(
                    "Frieren",
                    listOf("Frieren: Beyond Journey's End", "Frieren", "Sousou no Frieren"),
                    StarterKind.MANGA,
                ),
            )

        private val WESTERN_SOURCE_NAMES =
            listOf("BatCave", "Read Comics Online", "XOXO Comics")

        private val MANGA_SOURCE_NAMES =
            listOf("MangaDex", "Comix", "MangaFire")

        private val SOURCE_PACKAGES =
            mapOf(
                "BatCave" to "eu.kanade.tachiyomi.extension.en.batcave",
                "Read Comics Online" to "eu.kanade.tachiyomi.extension.en.readcomicsonline",
                "XOXO Comics" to "eu.kanade.tachiyomi.extension.en.xoxocomics",
            )

        private val SOURCE_ALIASES =
            mapOf(
                "BatCave" to setOf("batcave"),
                "Read Comics Online" to setOf("readcomicsonline", "readcomiconline"),
                "XOXO Comics" to setOf("xoxocomics", "xoxo"),
                "MangaDex" to setOf("mangadex"),
                "Comix" to setOf("comix"),
                "MangaFire" to setOf("mangafire"),
            )

        fun isSetupComplete(context: Context): Boolean =
            context
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_SETUP_COMPLETE, false)
    }
}
