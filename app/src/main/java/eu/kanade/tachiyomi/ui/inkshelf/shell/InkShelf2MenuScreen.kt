package eu.kanade.tachiyomi.ui.inkshelf.shell

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * InkShelf 2.0's own navigation destination for utilities and configuration.
 * Do not embed J2K's old settings controllers beneath the Compose app shell.
 * The remaining classic tools launch in their original activity until migrated.
 */
@Composable
internal fun InkShelf2MenuScreen(
    padding: PaddingValues,
    onBrowseSources: () -> Unit,
    onExtensions: () -> Unit,
    onRepositories: () -> Unit,
    onMetron: () -> Unit,
    onThemes: () -> Unit,
    onProfile: () -> Unit,
    onImportComics: () -> Unit,
    onBackupTools: () -> Unit,
    profileName: String,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(19.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                InkShelf2PageHeading("Menu")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your reading tools, comic sources and app settings.",
                    color = InkShelf2Colors.Muted,
                    fontSize = 13.sp,
                )
            }
        }
        item {
            MenuSection("YOUR INKSHELF") {
                MenuEntry(
                    "Your Profile & Guided Setup",
                    if (profileName.isBlank()) "Choose a name and personalise your library"
                    else "Local profile: $profileName · Edit name and setup",
                    Icons.Outlined.PersonOutline,
                    onProfile,
                )
                MenuEntry(
                    "Import Local Comics",
                    "Add your own CBZ, CBR, ZIP, RAR or EPUB files",
                    Icons.Outlined.FileUpload,
                    onImportComics,
                )
                MenuEntry(
                    "Backup & Restore",
                    "Protect your library or restore an existing backup",
                    Icons.Outlined.Backup,
                    onBackupTools,
                )
            }
        }
        item {
            MenuSection("READING") {
                MenuEntry("Downloads", "Manage queued and downloaded issues", Icons.Outlined.Download, onDownloads)
                MenuEntry("Reading History", "Recently opened comics", Icons.Outlined.History, onHistory)
            }
        }
        item {
            MenuSection("SOURCES & EXTENSIONS") {
                MenuEntry("Browse Sources", "Explore your installed comic catalogues", Icons.Outlined.AutoStories, onBrowseSources)
                MenuEntry("Manage Extensions", "Install, update and trust extensions", Icons.Outlined.Extension, onExtensions)
                MenuEntry("Extension Repositories", "Manage extension catalogue repositories", Icons.Outlined.Storage, onRepositories)
            }
        }
        item {
            MenuSection("METADATA & SETTINGS") {
                MenuEntry("Appearance & Themes", "Comic colour themes and custom palettes", Icons.Outlined.Palette, onThemes)
                MenuEntry("Metron", "Comic metadata connection and API token", Icons.Outlined.Key, onMetron)
                MenuEntry("Advanced Settings", "Reader, appearance and download preferences", Icons.Outlined.Settings, onSettings)
            }
        }
        item {
            Text(
                "Downloads and Reading History now use the InkShelf 2.0 interface. " +
                    "Backup, import and advanced settings still use the established tools until their migration. " +
                    "Your library and reading data are preserved.",
                modifier = Modifier.padding(horizontal = 18.dp),
                color = InkShelf2Colors.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
internal fun InkShelf2MenuMetronScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 3.dp,
            bottom = padding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 7.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to Menu", tint = InkShelf2Colors.Text)
                }
                InkShelf2PageHeading("Metron", modifier = Modifier.weight(1f))
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                InkShelf2MetronSettings()
            }
        }
    }
}

@Composable
private fun MenuSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            title,
            modifier = Modifier.padding(start = 5.dp, bottom = 2.dp),
            color = InkShelf2Colors.AccentLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
        )
        content()
    }
}

@Composable
private fun MenuEntry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.76f))
            .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.52f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            tint = InkShelf2Colors.AccentLight,
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = InkShelf2Colors.Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = InkShelf2Colors.Muted,
        )
    }
}
