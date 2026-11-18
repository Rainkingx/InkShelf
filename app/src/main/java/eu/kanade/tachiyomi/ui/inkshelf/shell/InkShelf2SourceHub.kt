package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
) {
    val manager: ExtensionManager = remember { Injekt.get() }
    val prefs: PreferencesHelper = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    if (selectedSourceId != null) {
        InkShelf2CatalogueScreen(
            source = sources.firstOrNull { it.id == selectedSourceId },
            padding = padding,
            onBack = { onSourceSelected(null) },
            onOpenComic = onOpenComic,
        )
        return
    }

    var tab by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var search by rememberSaveable { mutableStateOf("") }
    var repoUrl by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var repoBusy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(setOf<String>()) }
    var trustPending by remember { mutableStateOf<Extension.Untrusted?>(null) }
    var removal by remember { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_VARIABLE")
    val revisionDependency = revision
    val repos = prefs.extensionRepos().get().sorted()
    val languages = prefs.enabledLanguages().get()
    val hidden = prefs.hiddenSources().get()
    val showNsfw = prefs.showNsfwSources().get()
    val catalogues = sources.filterNot { it.id == LocalSource.ID }
        .sortedWith(compareBy<CatalogueSource> {
            it.lang !in languages || it.id.toString() in hidden
        }.thenBy { it.name.lowercase() })
    val enabled = catalogues.filter { it.lang in languages && it.id.toString() !in hidden }

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
                IconButton(onClick = onClose) {
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
                        onClick = { tab = option },
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
                        "Browse catalogues from your existing J2K extensions.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = InkShelf2Colors.Muted, fontSize = 12.sp,
                    )
                }
                if (catalogues.isEmpty()) {
                    item {
                        InkShelf2EmptyShelf("No installed comic sources found. Open Extensions to install or trust an extension.")
                        InkShelf2ActionButton("Find Extensions",
                            onClick = { tab = "Extensions" },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
                items(catalogues, key = { it.id }) { source ->
                    val active = source in enabled
                    HubInfoRow(
                        title = source.name,
                        subtitle = "${source.lang.uppercase()} • " +
                            if (active) "Ready to browse" else "Disabled in source settings",
                        action = if (active) "Browse" else "Enable",
                        onAction = {
                            if (active) onSourceSelected(source.id)
                            else {
                                prefs.enabledLanguages().set(prefs.enabledLanguages().get() + source.lang)
                                prefs.hiddenSources().set(prefs.hiddenSources().get() - source.id.toString())
                                revision++
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
                items(installed.filter { showNsfw || !it.isNsfw }.sortedBy { it.name },
                    key = { "installed:" + it.pkgName }) { ext ->
                    val update = available.firstOrNull { it.pkgName == ext.pkgName }
                        ?.takeIf { it.versionCode > ext.versionCode || it.libVersion > ext.libVersion }
                    HubInfoRow(
                        title = ext.name,
                        subtitle = "${ext.lang.uppercase()} • ${ext.sources.size} sources • " +
                            if (update != null) "Update available" else "Installed ${ext.versionName}",
                        action = if (update != null) "Update" else "Browse",
                        onAction = {
                            if (update != null) install(update)
                            else {
                                val first = ext.sources.filterIsInstance<CatalogueSource>().firstOrNull()
                                if (first != null) onSourceSelected(first.id) else tab = "Sources"
                            }
                        },
                    )
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
                        (search.isBlank() || ext.name.contains(search, true) ||
                            ext.sources.any { it.name.contains(search, true) })
                }.sortedBy { it.name }
                item {
                    InkShelf2SectionHeading("Available (${candidates.size})", null)
                    InkShelf2SearchField(search, onValueChange = { search = it },
                        modifier = Modifier.padding(horizontal = 16.dp))
                    if (repos.isEmpty()) {
                        InkShelf2EmptyShelf("No extension repositories configured.")
                        InkShelf2ActionButton("Add Repository", onClick = { tab = "Repos" },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
                items(candidates.take(250), key = { "available:" + it.pkgName }) { ext ->
                    HubInfoRow(ext.name,
                        "${ext.lang.uppercase()} • ${ext.sources.size} sources • ${ext.versionName}",
                        if (ext.pkgName in installing) "Installing…" else "Install",
                        onAction = { install(ext) })
                }
                if (candidates.isEmpty() && repos.isNotEmpty() && !refreshing) {
                    item { InkShelf2EmptyShelf("No extensions matched. Try another search or refresh.") }
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

@Composable
private fun HubInfoRow(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape)
            .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.72f))
            .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.40f), shape)
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
