package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** First-run preset. Exact package names prevent picking a similarly named source. */
private const val RECOMMENDED_REPO = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
private data class RecommendedSource(val title: String, val packageName: String)
private val RECOMMENDED_WESTERN_SOURCES = listOf(
    RecommendedSource("BatCave", "eu.kanade.tachiyomi.extension.en.batcave"),
    RecommendedSource("ReadComicsOnline.lol", "eu.kanade.tachiyomi.extension.en.readcomicsonlinelol"),
    RecommendedSource("ComicHubFree", "eu.kanade.tachiyomi.extension.en.comichubfree"),
)

/** Adds the official index non-destructively. Existing repositories are never replaced. */
private suspend fun ensureStarterRepository(prefs: PreferencesHelper) {
    val current = prefs.extensionRepos().get()
    if (current.any { it == RECOMMENDED_REPO || it.contains("/keiyoushi/extensions/") }) return
    val verified = withContext(Dispatchers.IO) { ExtensionApi().validateRepo(RECOMMENDED_REPO) }
    prefs.extensionRepos().set(prefs.extensionRepos().get() + verified)
}

@Composable
internal fun InkShelf2RecommendedSourcesSetup(
    installedSourceCount: Int,
    onManageSources: () -> Unit,
    onSourcesChanged: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs = remember { Injekt.get<PreferencesHelper>() }
    val extensions = remember { Injekt.get<ExtensionManager>() }
    val scope = rememberCoroutineScope()

    var batCaveSelected by rememberSaveable { mutableStateOf(true) }
    var readComicsLolSelected by rememberSaveable { mutableStateOf(true) }
    var comicHubSelected by rememberSaveable { mutableStateOf(true) }
    var repositoryReady by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connecting the recommended comic repository…") }
    var permissionGranted by remember { mutableStateOf(false) }

    fun hasInstallPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            prefs.extensionInstaller().get() != ExtensionInstaller.PACKAGE_INSTALLER ||
            context.packageManager.canRequestPackageInstalls()

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionGranted = hasInstallPermission()
        }
        lifecycle.addObserver(observer)
        permissionGranted = hasInstallPermission()
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        try {
            ensureStarterRepository(prefs)
            repositoryReady = true
            extensions.findAvailableExtensions()
            status = "Keiyoushi repository connected. Recommended sources are ready to install."
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            status = "Could not connect to the repository. Check your internet connection and retry below."
        }
    }

    val selected = buildList {
        if (batCaveSelected) add(RECOMMENDED_WESTERN_SOURCES[0])
        if (readComicsLolSelected) add(RECOMMENDED_WESTERN_SOURCES[1])
        if (comicHubSelected) add(RECOMMENDED_WESTERN_SOURCES[2])
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Start reading Western comics",
            color = InkShelf2Colors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
        Text(
            "The Keiyoushi extension repository is added automatically. These three sources " +
                "are preselected; uncheck any you don't want.",
            color = InkShelf2Colors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        val selections = listOf(
            Triple(RECOMMENDED_WESTERN_SOURCES[0].title, batCaveSelected, { checked: Boolean -> batCaveSelected = checked }),
            Triple(RECOMMENDED_WESTERN_SOURCES[1].title, readComicsLolSelected, { checked: Boolean -> readComicsLolSelected = checked }),
            Triple(RECOMMENDED_WESTERN_SOURCES[2].title, comicHubSelected, { checked: Boolean -> comicHubSelected = checked }),
        )
        selections.forEach { (title, checked, update) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = update,
                    enabled = !running,
                )
                Text(title, color = InkShelf2Colors.Text, fontSize = 13.sp)
            }
        }
        Text(
            if (installedSourceCount > 0) installedSourceCount.toString() + " comic sources currently enabled"
            else "No enabled comic sources yet",
            color = InkShelf2Colors.Muted,
            fontSize = 12.sp,
        )
        Text(status, color = InkShelf2Colors.AccentLight, fontSize = 12.sp, lineHeight = 17.sp)
        InkShelf2ActionButton(
            label = when {
                running -> "Installing Selected Sources…"
                !permissionGranted -> "Allow Extension Installation"
                else -> "Install " + selected.size + " Selected Sources"
            },
            enabled = !running && selected.isNotEmpty(),
            onClick = {
                if (!hasInstallPermission()) {
                    status = "Android needs permission to install extension APKs. Allow installs, return here and tap Install."
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + context.packageName),
                            ),
                        )
                    } catch (_: Exception) {
                        status = "Enable Install unknown apps for InkShelf in Android Settings, then retry."
                    }
                } else {
                    scope.launch {
                        running = true
                        try {
                            if (!repositoryReady) {
                                status = "Verifying the source repository…"
                                ensureStarterRepository(prefs)
                                repositoryReady = true
                            }
                            prefs.enabledLanguages().set(prefs.enabledLanguages().get() + "en")
                            status = "Checking the three recommended sources…"
                            extensions.findAvailableExtensions()
                            val available = extensions.availableExtensionsFlow.value
                            val found = selected.mapNotNull { target ->
                                available.firstOrNull { it.pkgName == target.packageName }
                                    ?.let { target to it }
                            }
                            val missing = selected.filter { target ->
                                found.none { (match, _) -> match.packageName == target.packageName }
                            }
                            if (found.isEmpty()) {
                                status = "None of the selected extensions were found. Check your connection or open Manage Sources."
                            } else {
                                val ids = found.flatMap { it.second.sources }.map { it.id.toString() }.toSet()
                                prefs.hiddenSources().set(prefs.hiddenSources().get() - ids)
                                val installed = extensions.installedExtensionsFlow.value.associateBy { it.pkgName }
                                val needed = found.map { it.second }.distinctBy { it.pkgName }.filter { candidate ->
                                    val current = installed[candidate.pkgName]
                                    current == null || candidate.versionCode > current.versionCode ||
                                        candidate.libVersion > current.libVersion
                                }
                                var failures = 0
                                for ((index, candidate) in needed.withIndex()) {
                                    status = "Installing " + candidate.name + " (" + (index + 1) + "/" +
                                        needed.size + "). Approve the Android installer if asked."
                                    var lastStep: InstallStep? = null
                                    extensions.installExtension(
                                        ExtensionManager.ExtensionInfo(candidate), scope,
                                    ).collect { update ->
                                        lastStep = update.first
                                        when (update.first) {
                                            InstallStep.Downloading -> status = "Downloading " + candidate.name + "…"
                                            InstallStep.Installing, InstallStep.Loading ->
                                                status = "Installing " + candidate.name + "… Approve Android's prompt."
                                            else -> Unit
                                        }
                                    }
                                    if (lastStep != InstallStep.Installed && lastStep != InstallStep.Done) failures++
                                }
                                status = when {
                                    failures > 0 -> failures.toString() + " source installation(s) could not complete. " +
                                        "Check Android installation prompts and retry."
                                    missing.isNotEmpty() -> "Some sources could not be found: " +
                                        missing.joinToString { it.title } + ". Other sources are ready."
                                    else -> "Recommended sources installed or already present. " +
                                        "If Android asks to trust an extension, review it in Manage Sources."
                                }
                                onSourcesChanged()
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            status = "Source setup failed. Check the connection and retry."
                        } finally {
                            running = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        InkShelf2ActionButton(
            label = "Manage Sources & Extensions",
            onClick = onManageSources,
            outlined = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Extensions are third-party code. Android may ask you to approve each installation " +
                "and InkShelf may ask you to trust its signature. No extensions are installed silently.",
            color = InkShelf2Colors.Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}
