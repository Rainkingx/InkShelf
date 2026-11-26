package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Real extension management using the already-installed J2K ExtensionManager.
 * All panels are native Compose; no legacy Conductor UI is mounted.
 */
@Composable
internal fun InkShelf2SourceHub(
    padding: PaddingValues,
    sources: List<CatalogueSource>,
    installed: List<Extension.Installed>,
    available: List<Extension.Available>,
    untrusted: List<Extension.Untrusted>,
    selectedSourceId: Long?,
    onSourceSelected: (Long?) -> Unit,
    onOpenComic: (Manga) -> Unit,
    onClose: () -> Unit,
    initialMode: String,
    initialRepoUrl: String = "",
    updateAllOnOpen: Boolean = false,
    onUpdateAllHandled: () -> Unit = {},
    onExtensionStateChanged: () -> Unit = {},
) {
    val manager: ExtensionManager = remember { Injekt.get() }
    val prefs: PreferencesHelper = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val catalogueState = rememberSaveableStateHolder()
    var tab by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var hubTrail by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var search by rememberSaveable { mutableStateOf("") }
    var availableExpanded by rememberSaveable { mutableStateOf(false) }
    var repoUrl by rememberSaveable(initialRepoUrl) { mutableStateOf(initialRepoUrl) }
    if (selectedSourceId != null) {
        catalogueState.SaveableStateProvider("catalogue:$selectedSourceId") {
        InkShelf2CatalogueScreen(
            source = sources.firstOrNull { it.id == selectedSourceId },
            padding = padding,
            onBack = { onSourceSelected(null) },
            onOpenComic = onOpenComic,
        )
        }
        return
    }
    fun openHubTab(target: String) {
        if (target != tab) {
            hubTrail = (hubTrail.split('|').takeLast(10) + target).joinToString("|")
            tab = target
        }
    }
    // A user who navigates Sources → Extensions → Repositories expects
    // Android Back to retrace those inner screens before leaving Discover.
    fun backHub() {
        if (hubTrail.contains('|')) {
            hubTrail = hubTrail.substringBeforeLast('|')
            tab = hubTrail.substringAfterLast('|')
        } else onClose()
    }
    BackHandler(enabled = true) { backHub() }
    var info by remember { mutableStateOf<String?>(null) }
    var repoBusy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(setOf<String>()) }
    var bulkUpdating by remember { mutableStateOf(false) }
    var trustPending by remember { mutableStateOf<Extension.Untrusted?>(null) }
    var removal by remember { mutableStateOf<String?>(null) }
    var extensionActions by remember { mutableStateOf<String?>(null) }
    var extensionRemoveConfirm by remember { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_VARIABLE")
    val revisionDependency = revision
    val repos = prefs.extensionRepos().get().sorted()
    val languages = prefs.enabledLanguages().get()
    val hidden = prefs.hiddenSources().get()
    val pausedPackages = prefs.inkShelf2PausedExtensions().get()
    val pausedIds = installed.filter { it.pkgName in pausedPackages }
        .flatMap { extension -> extension.sources.map { it.id } }.toSet()
    val showNsfw = prefs.showNsfwSources().get()
    val catalogues = sources.filterNot { it.id == LocalSource.ID }
        .sortedWith(compareBy<CatalogueSource> {
            it.lang !in languages || it.id.toString() in hidden || it.id in pausedIds
        }.thenBy { it.name.lowercase() })
    val enabled = catalogues.filter {
        it.lang in languages && it.id.toString() !in hidden && it.id !in pausedIds
    }
    val pendingUpdates = installed.filterNot { it.pkgName in pausedPackages }.mapNotNull { installedExt ->
        available.firstOrNull { candidate ->
            candidate.pkgName == installedExt.pkgName &&
                (candidate.versionCode > installedExt.versionCode || candidate.libVersion > installedExt.libVersion)
        }
    }.distinctBy { it.pkgName }

    LaunchedEffect(tab, revision) {
        if (tab == "Extensions" && repos.isNotEmpty()) {
            refreshing = true
            try { manager.findAvailableExtensions() } finally { refreshing = false }
        }
    }
    LaunchedEffect(installed) {
        installing = installing - installed.map { it.pkgName }.toSet()
    }
    LaunchedEffect(Unit) {
        manager.downloadSharedFlow.collect { (key, status) ->
            if (status.first == InstallStep.Error || status.first == InstallStep.Installed) {
                installing = installing - key
            }
        }
    }

    fun install(ext: Extension.Available) {
        if (ext.pkgName in installing) return
        installing = installing + ext.pkgName
        scope.launch {
            try {
                manager.installExtension(ExtensionManager.ExtensionInfo(ext), scope).collect { result ->
                    if (result.first == InstallStep.Error) {
                        installing = installing - ext.pkgName
                        info = "Could not install ${ext.name}. Check the source repository."
                    }
                }
            } catch (error: Exception) {
                installing = installing - ext.pkgName
                info = "Installation failed: ${error.message ?: "unknown error"}"
            }
        }
    }

    fun installAllUpdates() {
        if (bulkUpdating || pendingUpdates.isEmpty()) return
        scope.launch {
            bulkUpdating = true
            var failures = 0
            try {
                // One installer at a time; Android may ask for confirmation.
                for (extension in pendingUpdates) {
                    installing = installing + extension.pkgName
                    try {
                        var failed = false
                        manager.installExtension(
                            ExtensionManager.ExtensionInfo(extension), scope,
                        ).collect { result ->
                            if (result.first == InstallStep.Error) failed = true
                        }
                        if (failed) failures++
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        failures++
                    } finally {
                        installing = installing - extension.pkgName
                    }
                }
                if (failures > 0) {
                    info = failures.toString() + " extension update(s) could not complete."
                } else {
                    info = "Extension updates finished. Check any Android installation prompts."
                }
            } finally {
                bulkUpdating = false
            }
        }
    }

    LaunchedEffect(updateAllOnOpen, refreshing, pendingUpdates) {
        if (updateAllOnOpen && !refreshing && pendingUpdates.isNotEmpty()) {
            onUpdateAllHandled()
            installAllUpdates()
        }
    }

    // A long press offers maintenance for the actual installed extension,
    // never deletion of comic library entries or reading progress.
    extensionActions?.let { pkgName ->
        val extension = installed.firstOrNull { it.pkgName == pkgName }
        if (extension == null) {
            extensionActions = null
        } else {
            val packageCandidate = available.firstOrNull { it.pkgName == pkgName }
            AlertDialog(
                onDismissRequest = { extensionActions = null },
                title = { Text("Manage ${extension.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Version ${extension.versionName}. Reinstall to refresh the extension without removing your comics, or uninstall and add it again from Extensions.",
                            color = InkShelf2Colors.Muted,
                            fontSize = 13.sp,
                        )
                        InkShelf2ActionButton(
                            label = if (packageCandidate != null &&
                                (packageCandidate.versionCode > extension.versionCode ||
                                    packageCandidate.libVersion > extension.libVersion)
                            ) "Install available update" else "Reinstall extension",
                            enabled = packageCandidate != null && pkgName !in installing,
                            onClick = {
                                packageCandidate?.let { install(it) }
                                extensionActions = null
                                info = "Android may ask you to approve the extension installation."
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (packageCandidate == null) {
                            Text(
                                "This extension is not currently listed in your repository. Refresh Extensions before removing it.",
                                color = InkShelf2Colors.Muted,
                                fontSize = 12.sp,
                            )
                        }
                        InkShelf2ActionButton(
                            label = "Remove extension",
                            outlined = true,
                            onClick = {
                                extensionActions = null
                                extensionRemoveConfirm = pkgName
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Saved comics, downloads and reading progress are kept. Removing an extension temporarily makes its comics unavailable until it is installed again.",
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { extensionActions = null }) { Text("Close") }
                },
            )
        }
    }
    extensionRemoveConfirm?.let { pkgName ->
        val extension = installed.firstOrNull { it.pkgName == pkgName }
        AlertDialog(
            onDismissRequest = { extensionRemoveConfirm = null },
            title = { Text("Uninstall ${extension?.name ?: "extension"}?") },
            text = {
                Text(
                    "Only the extension app is uninstalled. Your InkShelf library, downloaded issues and reading progress remain. Reinstall it from the Available extensions list to restore this source."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    extensionRemoveConfirm = null
                    if (extension != null) {
                        try {
                            manager.uninstallExtension(pkgName)
                            openHubTab("Extensions")
                            info = "Complete Android's uninstall confirmation, then find ${extension.name} in Available and tap Install."
                        } catch (error: Exception) {
                            info = "Could not launch uninstall: ${error.message ?: "unknown error"}"
                        }
                    }
                }) { Text("Uninstall extension") }
            },
            dismissButton = {
                TextButton(onClick = { extensionRemoveConfirm = null }) { Text("Cancel") }
            },
        )
    }

    trustPending?.let { ext ->
        AlertDialog(
            onDismissRequest = { trustPending = null },
            title = { Text("Trust this extension?") },
            text = {
                Text(
                    "${ext.name}\n\nPackage: ${ext.pkgName}\n\n" +
                        "Only trust extensions from a developer you recognise. " +
                        "Trusted extensions can execute code in InkShelf.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.trust(ext.pkgName, ext.versionCode, ext.signatureHash)
                    trustPending = null
                }) { Text("Trust") }
            },
            dismissButton = { TextButton(onClick = { trustPending = null }) { Text("Cancel") } },
        )
    }
    removal?.let { url ->
        AlertDialog(
            onDismissRequest = { removal = null },
            title = { Text("Remove repository?") },
            text = { Text(url) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.extensionRepos().set(prefs.extensionRepos().get() - url)
                    revision++
                    removal = null
                    info = "Repository removed; installed extensions are unaffected."
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removal = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 14.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { backHub() }) {
                    Icon(Icons.Outlined.ArrowBack, "Back to Discover", tint = InkShelf2Colors.Text)
                }
                InkShelf2PageHeading("Sources & Extensions", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Sources", "Extensions", "Repos").forEach { option ->
                    HubTab(
                        label = when (option) {
                            "Sources" -> "Sources (${enabled.size})"
                            "Extensions" -> "Extensions"
                            else -> "Repos (${repos.size})"
                        },
                        active = tab == option,
                        modifier = Modifier.weight(1f),
                        onClick = { openHubTab(option) },
                    )
                }
            }
            info?.let {
                Text(it, color = InkShelf2Colors.AccentLight, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        when (tab) {
            "Sources" -> {
                item {
                    InkShelf2SectionHeading("Installed Comic Sources", null)
                    Text(
                        "Browse installed sources. Long-press a source for update or reinstall options.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = InkShelf2Colors.Muted, fontSize = 12.sp,
                    )
                }
                if (catalogues.isEmpty()) {
                    item {
                        InkShelf2EmptyShelf("No installed comic sources found. Open Extensions to install or trust an extension.")
                        InkShelf2ActionButton("Find Extensions",
                            onClick = { openHubTab("Extensions") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
                items(catalogues, key = { it.id }) { source ->
                    val active = source in enabled
                    val owningExtension = installed.firstOrNull { ext ->
                        ext.sources.any { it.id == source.id }
                    }
                    HubInfoRow(
                        title = source.name,
                        subtitle = "${source.lang.uppercase()} • " + when {
                            source.id in pausedIds -> "Extension paused"
                            active -> "Ready to browse"
                            else -> "Disabled in source settings"
                        },
                        action = when {
                            source.id in pausedIds -> "Extensions"
                            active -> "Browse"
                            else -> "Enable"
                        },
                        onLongPress = owningExtension?.let { ext ->
                            { extensionActions = ext.pkgName }
                        },
                        onAction = {
                            when {
                                source.id in pausedIds -> openHubTab("Extensions")
                                active -> onSourceSelected(source.id)
                                else -> {
                                    prefs.enabledLanguages().set(prefs.enabledLanguages().get() + source.lang)
                                    prefs.hiddenSources().set(prefs.hiddenSources().get() - source.id.toString())
                                    revision++
                                    onExtensionStateChanged()
                                }
                            }
                        },
                    )
                }
            }
            "Extensions" -> {
                item {
                    InkShelf2SectionHeading("Installed (${installed.size})", null)
                    if (refreshing) {
                        Text("Refreshing extension catalogue…",
                            color = InkShelf2Colors.Muted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (installed.isEmpty()) {
                    item { InkShelf2EmptyShelf("No trusted extensions installed. Check available extensions below.") }
                }
                if (pendingUpdates.isNotEmpty()) {
                    item(key = "update-all") {
                        InkShelf2ActionButton(
                            if (bulkUpdating) "Updating Extensions…" else "Update All (" + pendingUpdates.size + ")",
                            onClick = { installAllUpdates() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            enabled = !bulkUpdating,
                        )
                    }
                }
                items(installed.sortedBy { it.name },
                    key = { "installed:" + it.pkgName }) { ext ->
                    val paused = ext.pkgName in pausedPackages
                    val update = available.firstOrNull { it.pkgName == ext.pkgName }
                        ?.takeIf { it.versionCode > ext.versionCode || it.libVersion > ext.libVersion }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HubInfoRow(
                            title = ext.name,
                            subtitle = "${ext.lang.uppercase()} • ${ext.sources.size} sources • " +
                                when {
                                    paused -> "Paused · ${ext.versionName}"
                                    update != null -> "Update available"
                                    else -> "Installed ${ext.versionName}"
                                },
                            action = when {
                                paused -> "Resume"
                                update != null -> "Update"
                                else -> "Browse"
                            },
                            onLongPress = { extensionActions = ext.pkgName },
                            onAction = {
                                if (paused) {
                                    InkShelf2ExtensionPause.setPaused(ext, false)
                                    revision++
                                    onExtensionStateChanged()
                                    info = "${ext.name} resumed."
                                } else if (update != null) {
                                    install(update)
                                } else {
                                    val first = ext.sources.filterIsInstance<CatalogueSource>().firstOrNull()
                                    if (first != null) onSourceSelected(first.id) else openHubTab("Sources")
                                }
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (paused) "Paused — hidden from discovery and fallback"
                                else "Enabled — discover and read from this source",
                                modifier = Modifier.weight(1f),
                                color = InkShelf2Colors.Muted,
                                fontSize = 11.sp,
                            )
                            Switch(
                                checked = !paused,
                                onCheckedChange = { enabledNow ->
                                    InkShelf2ExtensionPause.setPaused(ext, !enabledNow)
                                    revision++
                                    onExtensionStateChanged()
                                    info = if (enabledNow) "${ext.name} resumed."
                                        else "${ext.name} paused; saved comics and downloads are kept."
                                },
                            )
                        }
                    }
                }
                if (untrusted.isNotEmpty()) {
                    item { InkShelf2SectionHeading("Untrusted (${untrusted.size})", null) }
                    items(untrusted, key = { "untrusted:" + it.pkgName }) { ext ->
                        HubInfoRow(ext.name, "Untrusted signature • ${ext.versionName}", "Review",
                            onAction = { trustPending = ext })
                    }
                }
                val candidates = available.filter { ext ->
                    (showNsfw || !ext.isNsfw) &&
                        (ext.lang == "all" || ext.lang in languages) &&
                        installed.none { it.pkgName == ext.pkgName } &&
                        untrusted.none { it.pkgName == ext.pkgName } &&
                        (!availableExpanded || search.isBlank() || ext.name.contains(search, true) ||
                            ext.sources.any { it.name.contains(search, true) })
                }.sortedBy { it.name }
                item {
                    InkShelf2SectionHeading("Available (${candidates.size})", null)
                    InkShelf2ActionButton(
                        if (availableExpanded) "Hide available sources" else "See more sources (${candidates.size})",
                        onClick = {
                            availableExpanded = !availableExpanded
                            if (!availableExpanded) search = ""
                        },
                        outlined = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    if (availableExpanded) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search available sources") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    if (repos.isEmpty()) {
                        InkShelf2EmptyShelf("No extension repositories configured.")
                        InkShelf2ActionButton("Add Repository", onClick = { openHubTab("Repos") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
                if (availableExpanded) {
                    items(candidates, key = { "available:" + it.pkgName }) { ext ->
                        HubInfoRow(ext.name,
                            "${ext.lang.uppercase()} • ${ext.sources.size} sources • ${ext.versionName}",
                            if (ext.pkgName in installing) "Installing…" else "Install",
                            onAction = { install(ext) })
                    }
                    if (candidates.isEmpty() && repos.isNotEmpty() && !refreshing) {
                        item { InkShelf2EmptyShelf("No extensions matched. Try another search or refresh.") }
                    }
                }
                item {
                    InkShelf2ActionButton(
                        if (refreshing) "Refreshing…" else "Refresh Extensions",
                        onClick = {
                            if (!refreshing) scope.launch {
                                refreshing = true
                                try { manager.findAvailableExtensions() } finally { refreshing = false }
                            }
                        },
                        outlined = true, enabled = repos.isNotEmpty() && !refreshing,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            else -> {
                item {
                    InkShelf2SectionHeading("Extension Repositories", null)
                    Text("Only add repositories you trust. Extensions run code inside InkShelf.",
                        color = InkShelf2Colors.Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    OutlinedTextField(
                        value = repoUrl, onValueChange = { repoUrl = it },
                        placeholder = { Text("https://example.org/repo.json") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                    )
                    InkShelf2ActionButton(
                        label = if (repoBusy) "Validating…" else "Add Repository",
                        leadingIcon = Icons.Outlined.Add,
                        enabled = !repoBusy && repoUrl.isNotBlank(),
                        onClick = {
                            val candidate = repoUrl.trim()
                            val parsed = Uri.parse(candidate)
                            if (parsed.scheme != "https" || parsed.host.isNullOrBlank()) {
                                info = "Enter a valid HTTPS repository index URL."
                            } else if (candidate in prefs.extensionRepos().get()) {
                                info = "That repository is already configured."
                            } else {
                                scope.launch {
                                    repoBusy = true
                                    try {
                                        val resolved = ExtensionApi().validateRepo(candidate)
                                        prefs.extensionRepos().set(prefs.extensionRepos().get() + resolved)
                                        repoUrl = ""
                                        revision++
                                        info = "Repository verified and added."
                                    } catch (_: Exception) {
                                        info = "Could not verify the repository. Check the URL and connection."
                                    } finally { repoBusy = false }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                if (repos.isEmpty()) {
                    item { InkShelf2EmptyShelf("No repositories configured. Your installed extensions remain available.") }
                }
                items(repos, key = { it }) { url ->
                    HubInfoRow(
                        prefs.extensionRepoMetadata().get()[url]?.name ?: Uri.parse(url).host.orEmpty(),
                        url, "Remove", onAction = { removal = url })
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun HubTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.height(35.dp).clip(shape)
            .background(
                if (active) InkShelf2Colors.AccentGradient
                else Brush.horizontalGradient(listOf(InkShelf2Colors.SurfaceRaised, InkShelf2Colors.Surface)),
            )
            .border(1.dp, if (active) InkShelf2Colors.AccentLight else InkShelf2Colors.Border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HubInfoRow(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape)
            .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.72f))
            .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.40f), shape)
            .then(
                if (onLongPress != null) Modifier.combinedClickable(
                    onClick = onAction,
                    onLongClick = onLongPress,
                ) else Modifier
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = InkShelf2Colors.Text, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = InkShelf2Colors.Muted, fontSize = 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(9.dp))
        InkShelf2ActionButton(action, onClick = onAction, outlined = true,
            modifier = Modifier.width(94.dp))
    }
}
