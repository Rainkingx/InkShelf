package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun InkShelf2PhoneNotificationControl() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(InkShelf2ReleaseRadar.canNotify(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = InkShelf2ReleaseRadar.canNotify(context)
    }
    // Recheck after returning from system notification settings.
    LaunchedEffect(Unit) {
        while (true) {
            allowed = InkShelf2ReleaseRadar.canNotify(context)
            kotlinx.coroutines.delay(1000)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (allowed) "Phone notifications enabled" else "Phone notifications are off",
            color = InkShelf2Colors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("App updates, source updates and new library issues use Android notification channels.",
            color = InkShelf2Colors.Muted, fontSize = 11.sp)
        InkShelf2ActionButton(
            label = if (allowed) "Manage phone notifications" else "Enable phone notifications",
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !allowed &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    request.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!allowed) {
            InkShelf2ActionButton(label = "Open Android notification settings",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun InkShelf2ReleaseRadarScreen(
    padding: PaddingValues,
    library: List<InkShelf2LibraryItem>,
    onBack: () -> Unit,
    onMetron: () -> Unit,
) {
    val context = LocalContext.current
    val configured = remember { InkShelf2ReleaseRadar.configured(context) }
    var calendar by remember { mutableStateOf(InkShelf2ReleaseRadar.cached(context)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }
    var myLibrary by remember { mutableStateOf(true) }
    var alerts by remember { mutableStateOf(InkShelf2ReleaseRadar.enabled(context)) }
    LaunchedEffect(epoch, configured) {
        if (!configured) return@LaunchedEffect
        loading = true
        error = false
        try {
            calendar = InkShelf2ReleaseRadar.load(context, force = epoch > 0)
            calendar?.let { InkShelf2ReleaseRadar.notifyReleases(context, it) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    val today = LocalDate.now()
    val filtered = calendar?.issues.orEmpty().filter { issue ->
        issue.date >= today && (!myLibrary || library.any { InkShelf2ReleaseRadar.matches(issue, it.manga.title) })
    }
    val shape = RoundedCornerShape(14.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = InkShelf2Colors.Text) }
                    InkShelf2PageHeading("Release Radar")
                }
                Text("Upcoming comic store releases · Next seven days", color = InkShelf2Colors.Muted, fontSize = 12.sp)
                Text("Release dates can change. A store release is not a guarantee an issue is available in your source.",
                    color = InkShelf2Colors.Muted, fontSize = 11.sp)
            }
        }
        item("alerts") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                .background(InkShelf2Colors.SurfaceRaised, shape).border(1.dp, InkShelf2Colors.Border, shape).padding(14.dp),
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
            }
        }
        if (!configured) {
            item("connect") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connect your Metron account to see verified store dates.", color = InkShelf2Colors.Text, fontSize = 13.sp)
                    InkShelf2ActionButton(label = "Connect Metron", onClick = onMetron, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            item("filters") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InkShelf2ActionButton(label = if (myLibrary) "✓ My library" else "My library", onClick = { myLibrary = true }, modifier = Modifier.weight(1f))
                        InkShelf2ActionButton(label = if (!myLibrary) "✓ All releases" else "All releases", onClick = { myLibrary = false }, modifier = Modifier.weight(1f))
                    }
                    InkShelf2ActionButton(label = if (loading) "Checking…" else "Refresh release dates", enabled = !loading,
                        onClick = { epoch++ }, modifier = Modifier.fillMaxWidth())
                    if (error) Text("Could not refresh Metron. Showing saved dates if available.", color = InkShelf2Colors.Muted, fontSize = 12.sp)
                    if (calendar?.partial == true) Text("Partial calendar · More releases may be listed on Metron.", color = InkShelf2Colors.Muted, fontSize = 11.sp)
                    if (myLibrary) Text("Titles without a publication year are possible matches only; phone alerts require an exact edition.",
                        color = InkShelf2Colors.Muted, fontSize = 11.sp)
                }
            }
            if (filtered.isEmpty()) {
                item("empty") {
                    Text(if (loading) "Loading store-release dates…" else "No matching upcoming releases in this calendar.",
                        Modifier.padding(horizontal = 16.dp), color = InkShelf2Colors.Muted, fontSize = 13.sp)
                }
            }
            filtered.groupBy { it.date }.forEach { (date, issues) ->
                item("date-$date") {
                    Text(if (date == today) "TODAY" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase(),
                        Modifier.padding(horizontal = 17.dp), color = InkShelf2Colors.AccentLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                items(issues, key = { it.id }) { issue ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .background(InkShelf2Colors.SurfaceRaised, shape).border(1.dp, InkShelf2Colors.Border, shape).padding(13.dp)) {
                        Text(issue.title + " #" + issue.number, color = InkShelf2Colors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (issue.year > 0) "Series began ${issue.year}" else "Series year unavailable",
                            color = InkShelf2Colors.Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
