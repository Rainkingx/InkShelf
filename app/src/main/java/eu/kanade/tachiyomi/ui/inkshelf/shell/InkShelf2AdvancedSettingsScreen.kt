package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredporciuncula.flow.preferences.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBackgroundColor
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** New Compose-owned settings, backed exclusively by the original J2K preferences. */
@Composable
internal fun InkShelf2AdvancedSettingsScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onThemes: () -> Unit,
    onDownloads: () -> Unit,
    onBackup: () -> Unit,
    readerOnly: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember { Injekt.get<PreferencesHelper>() }
    val downloadFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                prefs.downloadsDirectory().set(uri.toString())
                Toast.makeText(context, "Download folder updated", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot use that folder for downloads", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 26.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "heading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 5.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, if (readerOnly) "Back to Reader" else "Back to Menu", tint = InkShelf2Colors.Text)
                }
                InkShelf2PageHeading(if (readerOnly) "Reader Settings" else "Advanced Settings")
            }
            Text(
                if (readerOnly) "Your default reading preferences. Changes are saved instantly."
                else "Your existing reader, download and library settings. Changes are saved instantly.",
                modifier = Modifier.padding(horizontal = 17.dp),
                color = InkShelf2Colors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        if (!readerOnly) {
            item(key = "data-saver-heading") { SettingsHeading("DATA SAVER") }
            item(key = "data-saver") {
                SettingsPanel {
                    ToggleSetting(
                        "Data Saver", "Wi-Fi-only automatic updates and downloads. Reduce background artwork fetching. Manual browsing and reading remain available.",
                        prefs.inkShelfDataSaver(),
                        onChange = {
                            val app = context.applicationContext
                            eu.kanade.tachiyomi.ui.source.InkShelfDiscoverRefreshWorker.schedule(app)
                            InkShelf2ReleaseRadarWorker.schedule(app)
                            if (eu.kanade.tachiyomi.BuildConfig.INCLUDE_UPDATER) eu.kanade.tachiyomi.data.updater.AppUpdateJob.setupTask(app)
                            eu.kanade.tachiyomi.extension.ExtensionUpdateJob.setupTask(app)
                            LibraryUpdateJob.setupTask(app, keepExisting = true)
                        },
                    )
                }
            }
        }
        item(key = "reader-heading") { SettingsHeading("READER") }
        item(key = "reader-modes") {
            SettingsPanel {
                ChoiceSetting(
                    "Default reading mode", "Controls the reader direction for new comics",
                    prefs.defaultReadingModeSetting(),
                    ReadingModeType.entries.drop(1).map { context.getString(it.stringRes) to it.flagValue },
                )
                SettingsDivider()
                ChoiceSetting(
                    "Screen orientation", "Free rotation or a fixed reader orientation",
                    prefs.defaultOrientationType(),
                    OrientationType.entries.drop(1).map { context.getString(it.stringRes) to it.flagValue },
                )
                SettingsDivider()
                ChoiceSetting(
                    "Page background", "Choose how the reader fills empty space",
                    prefs.readerTheme(),
                    ReaderBackgroundColor.entries.map { context.getString(it.longStringRes ?: it.stringRes) to it.prefValue },
                )
                SettingsDivider()
                ChoiceSetting(
                    "Page scaling", "Fit the image to your screen",
                    prefs.imageScaleType(),
                    listOf(
                        "Fit screen" to 1, "Stretch" to 2, "Fit width" to 3,
                        "Fit height" to 4, "Original size" to 5, "Smart fit" to 6,
                    ),
                )
            }
        }
        item(key = "reader-switches") {
            SettingsPanel {
                ToggleSetting(
                    "Smart Source Fallback",
                    "Automatically replace failed images only when the edition, issue and page count match. No new library entries.",
                    prefs.inkShelfAutoSourceFallback(),
                )
                SettingsDivider()
                ToggleSetting("Panel Flow (beta)", "Jump between comic panels", prefs.panelFlowEnabled())
                SettingsDivider()
                ToggleSetting("Panel Flow V3 (experimental)", "Detect panel gutters and silent artwork. Turn off for V2; reopen the comic after changing.", prefs.panelFlowPanelBoundaries())
                SettingsDivider()
                ToggleSetting("Animate page transitions", "Animate movement between pages", prefs.pageTransitions())
                SettingsDivider()
                ToggleSetting("Fullscreen reader", "Use the full display while reading", prefs.fullscreen())
                SettingsDivider()
                ToggleSetting("Keep screen awake", "Prevent the display from sleeping", prefs.keepScreenOn())
                SettingsDivider()
                ToggleSetting("Show page number", "Display the current page indicator", prefs.showPageNumber())
                SettingsDivider()
                ToggleSetting("Volume-button page turns", "Turn pages with the volume keys", prefs.readWithVolumeKeys())
                SettingsDivider()
                ToggleSetting("Skip already-read issues", "Jump past issues marked read", prefs.skipReadSetting())
                SettingsDivider()
                ToggleSetting("Skip filtered issues", "Respect your issue filters while reading", prefs.skipFilteredSetting())
                SettingsDivider()
                ToggleSetting("Skip duplicate issues", "Avoid duplicate chapter entries", prefs.skipDupe())
            }
        }
        if (readerOnly) {
            item(key = "reader-display-extra") {
                SettingsPanel {
                    ChoiceSetting("Double-tap animation", "Zoom animation speed", prefs.doubleTapAnimSpeed(),
                        listOf("None" to 1, "Fast" to 250, "Normal" to 500))
                    SettingsDivider()
                    ToggleSetting("Always show issue transition", "Show the screen between issues", prefs.alwaysShowChapterTransition())
                    SettingsDivider()
                    ChoiceSetting("Landscape cutout", "Display around the camera cutout", prefs.landscapeCutoutBehavior(),
                        listOf("Pad cutout" to 0, "Ignore cutout" to 1))
                    SettingsDivider()
                    MultiChoiceSetting("Reader toolbar buttons", "Choose shortcuts shown in the reader",
                        prefs.readerBottomButtons(), ReaderBottomButton.entries.map { context.getString(it.stringRes) to it.value })
                }
            }
            item(key = "paged-heading") { SettingsHeading("PAGED READER") }
            item(key = "paged-options") {
                SettingsPanel {
                    ChoiceSetting("Tap zones", "Choose where taps turn pages", prefs.navigationModePager(),
                        context.resources.getStringArray(R.array.reader_nav).mapIndexed { i, label -> label to i })
                    SettingsDivider()
                    ChoiceSetting("Invert tapping", "Reverse page-turn tap zones", prefs.pagerNavInverted(),
                        ViewerNavigation.TappingInvertMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it })
                    SettingsDivider()
                    ChoiceSetting("Page layout", "Choose single, double or split pages", prefs.pageLayout(),
                        PageLayout.entries.map { context.getString(it.fullStringRes) to it.value })
                    SettingsDivider()
                    ChoiceSetting("Zoom start position", "Where zoomed pages begin", prefs.zoomStart(),
                        listOf("Automatic" to 1, "Left" to 2, "Right" to 3, "Centre" to 4))
                    SettingsDivider()
                    ChoiceSetting("Display cutout", "How pages fit camera cutouts", prefs.pagerCutoutBehavior(),
                        listOf("Pad cutout" to 0, "Start past cutout" to 1, "Ignore cutout" to 2))
                    SettingsDivider()
                    ToggleSetting("Crop borders", "Remove empty page margins", prefs.cropBorders())
                    SettingsDivider()
                    ToggleSetting("Zoom double-page spreads", "Enlarge spreads in landscape", prefs.landscapeZoom())
                    SettingsDivider()
                    ToggleSetting("Navigate to pan", "Pan across a zoomed page before turning", prefs.navigateToPan())
                    SettingsDivider()
                    ToggleSetting("Split double pages in portrait", "Split spreads in automatic layout", prefs.automaticSplitsPage())
                    SettingsDivider()
                    ToggleSetting("Invert double pages", "Reverse double-page order", prefs.invertDoublePages())
                }
            }
            item(key = "webtoon-heading") { SettingsHeading("CONTINUOUS READER") }
            item(key = "webtoon-options") {
                SettingsPanel {
                    ChoiceSetting("Tap zones", "Continuous reading tap controls", prefs.navigationModeWebtoon(),
                        context.resources.getStringArray(R.array.reader_nav).mapIndexed { i, label -> label to i })
                    SettingsDivider()
                    ChoiceSetting("Invert tapping", "Reverse tap zones", prefs.webtoonNavInverted(),
                        ViewerNavigation.TappingInvertMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it })
                    SettingsDivider()
                    ChoiceSetting("Hide controls threshold", "How easily controls hide", prefs.webtoonReaderHideThreshold(),
                        PreferenceValues.ReaderHideThreshold.entries.map { context.getString(it.titleResId) to it })
                    SettingsDivider()
                    ChoiceSetting("Side padding", "Space beside continuous pages", prefs.webtoonSidePadding(),
                        (0..25 step 5).map { "$it%" to it })
                    SettingsDivider()
                    ChoiceSetting("Page layout", "Single or split pages", prefs.webtoonPageLayout(),
                        listOf(PageLayout.SINGLE_PAGE, PageLayout.SPLIT_PAGES).map { context.getString(it.fullStringRes) to it.webtoonValue })
                    SettingsDivider()
                    ToggleSetting("Crop borders", "Remove empty margins", prefs.cropBordersWebtoon())
                    SettingsDivider()
                    ToggleSetting("Invert double pages", "Reverse spread order", prefs.webtoonInvertDoublePages())
                    SettingsDivider()
                    ToggleSetting("Enable zoom out", "Allow zooming out in continuous mode", prefs.webtoonEnableZoomOut())
                }
            }
            item(key = "reader-controls-heading") { SettingsHeading("NAVIGATION & ACTIONS") }
            item(key = "reader-controls") {
                SettingsPanel {
                    ToggleSetting("Invert volume keys", "Reverse volume-button page turns", prefs.readWithVolumeKeysInverted())
                    SettingsDivider()
                    MultiChoiceSetting("Vertical progress bar", "Reading modes using a vertical bar",
                        prefs.readerVerticalSeekbarModes(), ReadingModeType.entries.drop(1).map { context.getString(it.stringRes) to it.prefValue.toString() })
                    SettingsDivider()
                    ChoiceSetting("Progress bar height", "Vertical bar height", prefs.readerVerticalSeekbarHeightPercent(),
                        (60..100 step 5).map { "$it%" to it })
                    SettingsDivider()
                    ToggleSetting("Dock progress bar left", "Place vertical progress bar on the left", prefs.readerVerticalSeekbarDockLeft())
                    SettingsDivider()
                    ChoiceSetting("Page preload amount", "Pages to load ahead while reading", prefs.preloadSize(),
                        (4..20 step 2).map { "$it pages" to it })
                    SettingsDivider()
                    ToggleSetting("Long-press actions", "Show page actions when holding a page", prefs.readWithLongTap())
                    SettingsDivider()
                    ToggleSetting("Save pages separately", "Create a folder per comic when saving pages", prefs.folderPerManga())
                }
            }
        }
        if (!readerOnly) {
        item(key = "downloads-heading") { SettingsHeading("DOWNLOADS") }
        item(key = "downloads-toggles") {
            SettingsPanel {
                ToggleSetting(
                    "Wi-Fi only", "Downloads wait for Wi-Fi if enabled here or in Data Saver",
                    prefs.downloadOnlyOverWifiSetting(),
                )
                SettingsDivider()
                ToggleSetting("Save chapters as CBZ", "Keep completed issues in CBZ format", prefs.saveChaptersAsCBZ())
                SettingsDivider()
                ToggleSetting("Download new issues", "Queue newly available issues automatically", prefs.downloadNewChapters())
                SettingsDivider()
                ToggleSetting("Split very tall images", "Handle unusually long pages in downloads", prefs.splitTallImages())
                SettingsDivider()
                ToggleSetting(
                    "Remove on mark as read", "Automatically delete an offline issue after marking it read",
                    prefs.removeAfterMarkedAsReadSetting(),
                )
            }
        }
        item(key = "downloads-choices") {
            SettingsPanel {
                ChoiceSetting(
                    "Simultaneous comic sources", "Parallel source downloads",
                    prefs.numberOfConcurrentSourceDownloads(),
                    (1..10).map { "$it sources" to it },
                )
                SettingsDivider()
                ChoiceSetting(
                    "Simultaneous pages", "Pages fetched in parallel",
                    prefs.numberOfConcurrentPageDownloads(),
                    (1..15).map { "$it pages" to it },
                )
                SettingsDivider()
                ChoiceSetting(
                    "Download ahead", "Pre-download unread issues while reading",
                    prefs.autoDownloadWhileReading(),
                    listOf("Never" to 0, "2 issues" to 2, "3 issues" to 3, "5 issues" to 5, "10 issues" to 10),
                )
                SettingsDivider()
                ChoiceSetting(
                    "After reading", "How many recently read issues to remove",
                    prefs.removeAfterReadSlots(),
                    listOf("Never" to -1, "Last read" to 0, "Second last" to 1, "Third last" to 2, "Fourth last" to 3, "Fifth last" to 4),
                )
                SettingsDivider()
                SettingsLink("Choose download folder", "Select a folder with write permission") {
                    downloadFolder.launch(null)
                }
                SettingsDivider()
                SettingsLink("Manage downloads", "View queue and completed issues", onDownloads)
            }
        }
        item(key = "library-heading") { SettingsHeading("LIBRARY & UPDATES") }
        item(key = "library-choices") {
            SettingsPanel {
                ChoiceSetting(
                    "Library update frequency", "Schedule checks for new issues",
                    prefs.libraryUpdateInterval(),
                    listOf(
                        "Manual" to 0, "Every 12 hours" to 12, "Daily" to 24,
                        "Every 2 days" to 48, "Every 3 days" to 72, "Weekly" to 168,
                    ),
                    onChange = { hours ->
                        // Use the original scheduler, exactly as J2K settings do.
                        LibraryUpdateJob.setupTask(context.applicationContext, 0)
                        if (hours > 0) LibraryUpdateJob.setupTask(context.applicationContext, hours)
                    },
                )
                SettingsDivider()
                ToggleSetting("Refresh covers with updates", "Check for updated comic artwork", prefs.refreshCoversToo())
                SettingsDivider()
                ToggleSetting("Search suggestions", "Suggest results from your library", prefs.showLibrarySearchSuggestions())
                SettingsDivider()
                ToggleSetting("Ignore titles' leading articles", "Ignore A, An and The when sorting", prefs.removeArticles())
            }
        }
        item(key = "other-heading") { SettingsHeading("APPEARANCE & PRIVACY") }
        item(key = "other-panel") {
            SettingsPanel {
                SettingsLink("Themes & appearance", "Choose your ARC colours", onThemes)
                SettingsDivider()
                ToggleSetting("Hide notification content", "Keep comic names out of notification previews", prefs.hideNotificationContentSetting())
                SettingsDivider()
                SettingsLink("Backup & restore", "Protect your library and reading history", onBackup)
            }
        }
        }
        item(key = "footnote") {
            Text(
                "Reader preferences apply to your comics across InkShelf. " +
                    "Additional reader controls are available while reading a comic.",
                modifier = Modifier.padding(horizontal = 17.dp, vertical = 6.dp),
                color = InkShelf2Colors.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun SettingsHeading(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 6.dp),
        color = InkShelf2Colors.AccentLight,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp)
            .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.55f), RoundedCornerShape(13.dp))
            .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.72f), RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 5.dp),
        content = content,
    )
}

@Composable
private fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(color = InkShelf2Colors.Border.copy(alpha = 0.38f))
}

@Composable
private fun SettingCopy(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title, color = InkShelf2Colors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(detail, color = InkShelf2Colors.Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ToggleSetting(title: String, detail: String, pref: Preference<Boolean>, onChange: (Boolean) -> Unit = {}) {
    var checked by remember(pref) { mutableStateOf(pref.get()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingCopy(title, detail, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                pref.set(it)
                checked = it
                onChange(it)
            },
        )
    }
}

@Composable
private fun <T : Any> ChoiceSetting(
    title: String,
    detail: String,
    pref: Preference<T>,
    options: List<Pair<String, T>>,
    onChange: (T) -> Unit = {},
) {
    var current by remember(pref) { mutableStateOf(pref.get()) }
    var isOpen by remember { mutableStateOf(false) }
    if (isOpen) {
        AlertDialog(
            onDismissRequest = { isOpen = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    options.forEach { (label, value) ->
                        TextButton(
                            onClick = {
                                pref.set(value)
                                current = value
                                onChange(value)
                                isOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (value == current) "✓  $label" else label,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isOpen = false }) { Text("Cancel") }
            },
        )
    }
    val label = options.firstOrNull { it.second == current }?.first ?: "Custom"
    Row(
        modifier = Modifier.fillMaxWidth().clickable { isOpen = true }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingCopy(title, detail, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(label, color = InkShelf2Colors.AccentLight, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun MultiChoiceSetting(title: String, detail: String, pref: Preference<Set<String>>, options: List<Pair<String, String>>) {
    var current by remember(pref) { mutableStateOf(pref.get()) }
    var open by remember { mutableStateOf(false) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { (label, value) ->
                        val toggle = {
                            current = if (value in current) current - value else current + value
                            pref.set(current)
                        }
                        Row(Modifier.fillMaxWidth().clickable { toggle() },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = value in current, onCheckedChange = { toggle() })
                            Text(label, color = InkShelf2Colors.Text)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } },
        )
    }
    SettingsLink(title, detail + " · " + current.size + " selected") { open = true }
}

@Composable
private fun SettingsLink(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingCopy(title, detail, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("›", color = InkShelf2Colors.AccentLight, fontSize = 21.sp)
    }
}
