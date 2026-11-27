package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The user can paste a token straight from the phone into this field.
 * We never pre-fill it with the stored secret or show it as plain text.
 */
@Composable
internal fun InkShelf2MetronSettings() {
    val context = LocalContext.current
    val store = remember(context) { InkShelf2MetronTokenStore(context) }
    val scope = rememberCoroutineScope()
    var tokenInput by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(store.hasToken()) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }

    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove Metron token?") },
            text = { Text("This removes the saved API token from this device only.") },
            confirmButton = {
                TextButton(onClick = {
                    store.clear()
                    tokenInput = ""
                    hasToken = false
                    status = "Metron token removed."
                    removing = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removing = false }) { Text("Cancel") }
            },
        )
    }

    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.78f), shape)
            .border(1.dp, InkShelf2Colors.Border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text("Metron Comic Metadata", color = Color.White, fontSize = 18.sp,
            fontWeight = FontWeight.Bold)
        Text(
            "Connect your Metron account to improve comic metadata and recommendations.",
            color = InkShelf2Colors.Muted,
            fontSize = 12.sp,
        )
        Text(
            if (hasToken) "API token saved on this device" else "No Metron token saved",
            color = if (hasToken) InkShelf2Colors.AccentLight else InkShelf2Colors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        OutlinedTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
                status = null
            },
            label = { Text(if (hasToken) "Replace API token" else "Metron API token") },
            placeholder = { Text("Paste your Metron token here") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        InkShelf2ActionButton(
            label = "Save Token",
            enabled = tokenInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                try {
                    store.save(tokenInput)
                    tokenInput = ""
                    hasToken = true
                    status = "Token encrypted and saved. Test the connection below."
                } catch (_: Exception) {
                    status = "Could not save the token securely on this device."
                }
            },
        )
        InkShelf2ActionButton(
            label = if (testing) "Testing connection…" else "Test Connection",
            outlined = true,
            enabled = hasToken && !testing,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val saved = store.read()
                if (saved.isNullOrBlank()) {
                    status = "Token could not be unlocked. Save a fresh token."
                    return@InkShelf2ActionButton
                }
                scope.launch {
                    testing = true
                    try {
                        status = when (val result = InkShelf2MetronApi.testConnection(saved)) {
                            InkShelf2MetronApi.ConnectionResult.Connected ->
                                "Connected to Metron successfully."
                            InkShelf2MetronApi.ConnectionResult.InvalidToken ->
                                "Metron rejected the token. Check or replace it."
                            InkShelf2MetronApi.ConnectionResult.RateLimited ->
                                "Metron rate limit reached. Try again later."
                            is InkShelf2MetronApi.ConnectionResult.Error ->
                                result.message
                        }
                    } finally {
                        testing = false
                    }
                }
            },
        )
        if (hasToken) {
            TextButton(onClick = { removing = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Remove Saved Token", color = InkShelf2Colors.Muted, fontSize = 12.sp)
            }
        }
        status?.let {
            Text(it, color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
        }
        Text(
            "Your token is encrypted by Android Keystore and sent only to metron.cloud over HTTPS. " +
                "It is not included in ARC builds, GitHub, or shared settings.",
            color = InkShelf2Colors.Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}
