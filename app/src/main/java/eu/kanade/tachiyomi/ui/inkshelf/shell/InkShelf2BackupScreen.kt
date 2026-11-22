package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.backup.BackupConst
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Native InkShelf 2.0 backup UI. The data format, WorkManager jobs and
 * validator remain those already trusted by J2K/InkShelf 1.
 */
@Composable
internal fun InkShelf2BackupScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onRestoreQueued: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val preferences = remember { Injekt.get<PreferencesHelper>() }
    val scope = rememberCoroutineScope()
    var includeRead by remember { mutableStateOf(true) }
    var autoInterval by remember { mutableStateOf(preferences.backupInterval().get()) }
    var maxBackups by remember { mutableStateOf(preferences.numberOfBackups().get()) }
    var validationRunning by remember { mutableStateOf(false) }
    var createRunning by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreWarnings by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    val fullBackupFlags = BackupConst.BACKUP_ALL or
        (if (includeRead) BackupConst.BACKUP_READ_MANGA else 0)

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    // A document provider may grant access without persistence.
                }
                BackupCreatorJob.startNow(appContext, uri, fullBackupFlags)
                Toast.makeText(context, "Backup started. Check the notification for the result.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Could not start backup.", Toast.LENGTH_LONG).show()
            }
        }
        createRunning = false
    }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // The picker may already grant access without persistence.
            }
            validationRunning = true
            validationError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { BackupFileValidator().validate(appContext, uri) }
                }
                validationRunning = false
                result.onSuccess { check ->
                    restoreUri = uri
                    val warnings = buildList {
                        if (check.missingSources.isNotEmpty()) {
                            add("Missing comic sources:\n" + check.missingSources.joinToString("\n"))
                        }
                        if (check.missingTrackers.isNotEmpty()) {
                            add("Disconnected trackers:\n" + check.missingTrackers.joinToString("\n"))
                        }
                    }
                    restoreWarnings = warnings.joinToString("\n\n")
                    confirmRestore = true
                }.onFailure {
                    validationError = it.message ?: "Could not validate this backup."
                }
            }
        }
    }
    val chooseFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                preferences.backupsDirectory().set(uri.toString())
                Toast.makeText(context, "Automatic backup folder updated.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Could not save access to this folder.", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (confirmRestore && restoreUri != null) {
        val selectedUri = restoreUri!!
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("InkShelf will merge this backup into the current library, using the original restore system. Review any missing sources or tracker connections first.")
                    if (restoreWarnings.isNotBlank()) {
                        Text(restoreWarnings, color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
                    }
                    Text("Existing downloaded comic files are not included in a normal library backup.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (BackupRestoreJob.isRunning(appContext)) {
                        Toast.makeText(context, "A restore is already running.", Toast.LENGTH_LONG).show()
                    } else {
                        BackupRestoreJob.start(appContext, selectedUri)
                        Toast.makeText(context, "Restore started. Follow the notification for the result.", Toast.LENGTH_LONG).show()
                        onRestoreQueued()
                    }
                    confirmRestore = false
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }
    if (validationError != null) {
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = { Text("Invalid backup") },
            text = { Text(validationError ?: "Could not open the backup.") },
            confirmButton = { TextButton(onClick = { validationError = null }) { Text("Close") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back to Menu", tint = InkShelf2Colors.Text)
                }
                InkShelf2PageHeading("Backup & Restore")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Protect your library, reading progress and settings using the established InkShelf backup format.",
                    color = InkShelf2Colors.Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))
                Text("CREATE A BACKUP", color = InkShelf2Colors.AccentLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text(
                    "Full backup: library, categories, issues, history, tracking, settings and metadata.",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = includeRead,
                        onCheckedChange = { includeRead = it },
                    )
                    Text("Include comics you've already read", color = InkShelf2Colors.Text, fontSize = 13.sp)
                }
                InkShelf2ActionButton(
                    label = "Create Backup",
                    leadingIcon = Icons.Outlined.Backup,
                    enabled = !createRunning,
                    onClick = {
                        createRunning = true
                        createDocument.launch(Backup.getBackupFilename())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("RESTORE A BACKUP", color = InkShelf2Colors.AccentLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text(
                    "Choose a .tachibk or compatible legacy backup. InkShelf checks it before asking you to restore.",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                InkShelf2ActionButton(
                    label = if (validationRunning) "Checking Backup…" else "Select Backup File",
                    leadingIcon = Icons.Outlined.Restore,
                    enabled = !validationRunning,
                    outlined = true,
                    onClick = { openDocument.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("AUTOMATIC BACKUPS", color = InkShelf2Colors.AccentLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                val choices = listOf(
                    0 to "Off", 6 to "Every 6 hours", 12 to "Every 12 hours",
                    24 to "Daily", 48 to "Every 2 days", 168 to "Weekly",
                )
                choices.forEach { (hours, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = autoInterval == hours,
                            onClick = {
                                autoInterval = hours
                                preferences.backupInterval().set(hours)
                                BackupCreatorJob.setupTask(appContext, hours)
                            },
                        )
                        Text(label, color = InkShelf2Colors.Text, fontSize = 13.sp)
                    }
                }
                if (autoInterval != 0) {
                    Spacer(Modifier.height(8.dp))
                    InkShelf2ActionButton(
                        label = "Choose Backup Folder",
                        leadingIcon = Icons.Outlined.FolderOpen,
                        outlined = true,
                        onClick = { chooseFolder.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Number of automatic backups to retain: " + maxBackups, color = InkShelf2Colors.Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InkShelf2ActionButton(
                            label = "−",
                            onClick = {
                                maxBackups = (maxBackups - 1).coerceAtLeast(1)
                                preferences.numberOfBackups().set(maxBackups)
                            },
                            enabled = maxBackups > 1,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                        )
                        InkShelf2ActionButton(
                            label = "+",
                            onClick = {
                                maxBackups = (maxBackups + 1).coerceAtMost(5)
                                preferences.numberOfBackups().set(maxBackups)
                            },
                            enabled = maxBackups < 5,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Backups are not a copy of the downloaded page files. Keep the original backup file somewhere safe.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = InkShelf2Colors.Muted, fontSize = 11.sp,
            )
        }
    }
}
