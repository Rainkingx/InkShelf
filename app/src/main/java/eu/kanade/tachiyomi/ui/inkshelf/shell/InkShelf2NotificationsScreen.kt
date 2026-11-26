package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.extension.model.Extension

/**
 * The InkShelf 2.0 bell displays actionable extension updates from the real
 * J2K ExtensionManager. Opening a row uses its existing installer screen.
 */
@Composable
internal fun InkShelf2NotificationsScreen(
    padding: PaddingValues,
    updates: List<Pair<Extension.Installed, Extension.Available>>,
    checking: Boolean,
    hasRepositories: Boolean,
    appUpdatesEnabled: Boolean,
    availableAppUpdate: String?,
    checkingAppUpdate: Boolean,
    onAppUpdate: () -> Unit,
    onCheckAppUpdate: () -> Unit,
    onBack: () -> Unit,
    onExtensions: () -> Unit,
    onRefresh: () -> Unit,
    onReleaseRadar: () -> Unit,
) {
    val context = LocalContext.current
    val configured = InkShelf2ReleaseRadar.configured(context)
    var alerts by remember { mutableStateOf(InkShelf2ReleaseRadar.enabled(context)) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(key = "heading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = InkShelf2Colors.Text)
                }
                InkShelf2PageHeading("Notifications")
            }
        }
        item(key = "phone-notifications") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InkShelf2PhoneNotificationControl()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Release-day alerts", color = InkShelf2Colors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Optional alerts for exact title and publication-year matches in your library.",
                            color = InkShelf2Colors.Muted, fontSize = 11.sp)
                    }
                    Switch(checked = alerts, enabled = configured, onCheckedChange = {
                        alerts = it
                        InkShelf2ReleaseRadar.setEnabled(context, it)
                    })
                }
                if (!configured) {
                    Text("Connect Metron in Menu to enable release-day alerts.", color = InkShelf2Colors.Muted, fontSize = 11.sp)
                }
                InkShelf2ActionButton(label = "Release Radar", onClick = onReleaseRadar, outlined = true, modifier = Modifier.fillMaxWidth())
            }
        }
        if (appUpdatesEnabled) {
            item(key = "app-update") {
                val shape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(shape).background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.8f))
                        .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.6f), shape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SystemUpdate, null,
                            modifier = Modifier.size(26.dp), tint = InkShelf2Colors.AccentLight)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (availableAppUpdate != null) "InkShelf update available" else "InkShelf updates",
                                color = InkShelf2Colors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(availableAppUpdate?.let { "New version: $it" }
                                ?: "Installed: " + eu.kanade.tachiyomi.BuildConfig.VERSION_NAME,
                                color = InkShelf2Colors.Muted, fontSize = 12.sp)
                        }
                    }
                    InkShelf2ActionButton(
                        label = when {
                            checkingAppUpdate -> "Checking…"
                            availableAppUpdate != null -> "View Update & Install"
                            else -> "Check for App Updates"
                        },
                        enabled = !checkingAppUpdate,
                        onClick = if (availableAppUpdate != null) onAppUpdate else onCheckAppUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 17.dp)) {
                Text(
                    when {
                        checking && updates.isEmpty() -> "Checking extension repositories…"
                        updates.isNotEmpty() -> updates.size.toString() + " extension update" +
                            (if (updates.size == 1) "" else "s") + " available"
                        !hasRepositories -> "No extension repositories configured"
                        else -> "No extension updates"
                    },
                    color = InkShelf2Colors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        updates.isNotEmpty() -> "Updates stay here until you've installed them."
                        !hasRepositories -> "Add a repository to receive extension update notices."
                        else -> "New extension updates will appear here when detected."
                    },
                    modifier = Modifier.padding(top = 5.dp),
                    color = InkShelf2Colors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        if (updates.isEmpty()) {
            item(key = "empty") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = InkShelf2Colors.AccentLight,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("No extension updates to review", color = InkShelf2Colors.Muted, fontSize = 13.sp)
                }
            }
        } else {
            item(key = "section") {
                InkShelf2SectionHeading("Extension Updates", onClick = onExtensions)
            }
            items(updates, key = { (installed, _) -> installed.pkgName }) { (installed, available) ->
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(shape)
                        .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.8f))
                        .border(1.dp, InkShelf2Colors.Border.copy(alpha = 0.6f), shape)
                        .clickable(onClick = onExtensions)
                        .padding(horizontal = 13.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = InkShelf2Colors.AccentLight,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            installed.name, color = InkShelf2Colors.Text,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Version " + installed.versionName + " → " + available.versionName,
                            modifier = Modifier.padding(top = 3.dp),
                            color = InkShelf2Colors.Muted, fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Update ›", color = InkShelf2Colors.AccentLight,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        item(key = "actions") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                InkShelf2ActionButton(
                    label = if (updates.isEmpty()) "Manage Extensions" else "View Extension Updates",
                    onClick = onExtensions,
                    modifier = Modifier.fillMaxWidth(),
                )
                InkShelf2ActionButton(
                    label = if (checking) "Checking…" else "Check for Extension Updates",
                    onClick = onRefresh,
                    outlined = true,
                    enabled = hasRepositories && !checking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
