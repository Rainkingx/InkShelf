package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R

/** The app's own update sheet; Android still owns the final install confirmation. */
@Composable
internal fun InkShelf2UpdateDialog(
    notes: String,
    progress: Int? = null,
    canInstall: Boolean = true,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = progress != null
    InkShelf2Theme {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = !downloading, dismissOnClickOutside = !downloading),
        ) {
            val shape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(shape)
                    .background(InkShelf2Colors.Surface)
                    .border(1.dp, InkShelf2Colors.Border, shape),
            ) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(InkShelf2Colors.AccentGradient))
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.inkshelf_brand_emblem), "InkShelf",
                            modifier = Modifier.size(48.dp))
                        Column {
                            Text("INKSHELF UPDATE", color = InkShelf2Colors.AccentLight,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Text(if (downloading) "Downloading update" else "New version available",
                                color = InkShelf2Colors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (progress != null) {
                        Text(if (progress < 0) "Downloading…" else "$progress%",
                            color = InkShelf2Colors.AccentLight, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        if (progress < 0) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                                color = InkShelf2Colors.Accent, trackColor = InkShelf2Colors.SurfaceRaised)
                        } else {
                            LinearProgressIndicator(progress = { (progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(), color = InkShelf2Colors.Accent,
                                trackColor = InkShelf2Colors.SurfaceRaised)
                        }
                        Text("Keep InkShelf open. Android will ask you to install when the download is ready.",
                            color = InkShelf2Colors.Muted, fontSize = 13.sp)
                    } else {
                        Text("INSTALLED · ${BuildConfig.VERSION_NAME}", color = InkShelf2Colors.Muted,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp)).background(InkShelf2Colors.SurfaceRaised)
                            .verticalScroll(rememberScrollState()).padding(12.dp)) {
                            Text(notes.ifBlank { "A new version of InkShelf is available." }.take(2500),
                                color = InkShelf2Colors.Text, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                        Button(onClick = onInstall, enabled = canInstall,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkShelf2Colors.Accent,
                                contentColor = InkShelf2Colors.Text)) {
                            Text("Download & Install", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Later", color = InkShelf2Colors.Muted)
                        }
                    }
                }
            }
        }
    }
}
