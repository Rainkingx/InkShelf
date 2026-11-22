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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * InkShelf 2.0's own optional, local-first onboarding.
 * The steps invoke proven extension / Metron / J2K backup / local import
 * workflows. They NEVER create an alternate account or move comic data.
 */
@Composable
internal fun InkShelf2WelcomeScreen(
    padding: PaddingValues,
    displayName: String,
    installedSourceCount: Int,
    step: Int,
    onStepChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onFinish: () -> Unit,
    onBrowseSources: () -> Unit,
    onSourcesChanged: () -> Unit,
    onBackupTools: () -> Unit,
    onImportComics: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(displayName) }
    var backupChoice by rememberSaveable { mutableStateOf("Start fresh") }
    var importChoice by rememberSaveable { mutableStateOf("Not right now") }

    val steps = listOf(
        "Your Profile", "Comic Sources", "Metron Metadata",
        "Backup & Restore", "Your Own Comics",
    )

    fun advance() {
        if (step == 0) onNameChange(name)
        if (step >= steps.lastIndex) onFinish() else onStepChange(step + 1)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 36.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (step > 0) onStepChange(step - 1) else onFinish()
                }) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = InkShelf2Colors.Text,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "WELCOME TO INKSHELF",
                        color = InkShelf2Colors.AccentLight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 10.sp,
                    )
                    InkShelf2PageHeading(steps[step])
                }
                Text(
                    "${step + 1} / ${steps.size}",
                    color = InkShelf2Colors.Muted,
                    fontSize = 12.sp,
                )
            }
        }

        item(key = "body") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> {
                        GuideText(
                            "Make it yours",
                            "Choose what InkShelf calls you. Your display name is saved in InkShelf's local settings — no email, password or online account needed.",
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(35) },
                            label = { Text("Display name (optional)") },
                            placeholder = { Text("What should we call you?") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Text(
                            "You can change your name later through Menu → Your Profile.",
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    1 -> {
                        InkShelf2RecommendedSourcesSetup(
                            installedSourceCount = installedSourceCount,
                            onManageSources = onBrowseSources,
                            onSourcesChanged = onSourcesChanged,
                        )
                    }
                    2 -> {
                        GuideText(
                            "Optional Metron integration",
                            "Metron can enrich comic details and covers, but it does not provide readable issues. Your installed extensions still handle reading.",
                        )
                        InkShelf2MetronSettings()
                        Text(
                            "You may skip Metron entirely. Credentials are encrypted locally and are not copied into backups.",
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    3 -> {
                        GuideText(
                            "Keep your reading history safe",
                            "Are you moving from an existing InkShelf or J2K installation, or starting a fresh library?",
                        )
                        ChoiceRow(
                            label = "Start fresh — keep my current library",
                            selected = backupChoice == "Start fresh",
                            onClick = { backupChoice = "Start fresh" },
                        )
                        ChoiceRow(
                            label = "Restore an existing backup",
                            selected = backupChoice == "Restore a backup",
                            onClick = { backupChoice = "Restore a backup" },
                        )
                        GuidePanel {
                            Text(
                                if (backupChoice == "Restore a backup")
                                    "Restore files using InkShelf's established backup validator. Review its warnings and confirm before restoring."
                                else
                                    "You can create a backup now or choose automatic backup frequency and location.",
                                color = InkShelf2Colors.Text,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            InkShelf2ActionButton(
                                label = if (backupChoice == "Restore a backup")
                                    "Open Restore Backup"
                                else "Open Backup Settings",
                                onClick = onBackupTools,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            "Restoring is never automatic. The existing backup tool checks the file and asks for confirmation.",
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    else -> {
                        GuideText(
                            "Bring your own comics",
                            "Have CBZ, CBR, ZIP, RAR or EPUB comics? InkShelf can import them into its existing Local Source library.",
                        )
                        ChoiceRow(
                            label = "Yes — I want to import my comics",
                            selected = importChoice == "Import",
                            onClick = { importChoice = "Import" },
                        )
                        ChoiceRow(
                            label = "Not right now",
                            selected = importChoice == "Not right now",
                            onClick = { importChoice = "Not right now" },
                        )
                        if (importChoice == "Import") {
                            GuidePanel {
                                Text(
                                    "Choose one or more comic files using Android's document picker. The original importer handles ComicInfo metadata and Local Source registration.",
                                    color = InkShelf2Colors.Text,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                InkShelf2ActionButton(
                                    label = "Choose Comic Files",
                                    onClick = onImportComics,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Text(
                            "You can import more files later from Menu → Import Local Comics.",
                            color = InkShelf2Colors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        item(key = "actions") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                InkShelf2ActionButton(
                    label = if (step == steps.lastIndex) "Finish Setup" else "Continue",
                    onClick = { advance() },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        // Skip only the current step; first-run setup is
                        // always optional and can be reopened via the Menu.
                        if (step == steps.lastIndex) onFinish() else onStepChange(step + 1)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (step == steps.lastIndex) "Finish without importing" else "Skip this step",
                        color = InkShelf2Colors.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideText(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = InkShelf2Colors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(description, color = InkShelf2Colors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun GuidePanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(13.dp))
            .border(1.dp, InkShelf2Colors.Border, RoundedCornerShape(13.dp))
            .padding(15.dp),
    ) {
        content()
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (selected) InkShelf2Colors.Accent.copy(alpha = 0.20f)
                else InkShelf2Colors.SurfaceRaised,
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (selected) InkShelf2Colors.AccentLight else InkShelf2Colors.Border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "●" else "○", color = InkShelf2Colors.AccentLight, fontSize = 17.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
