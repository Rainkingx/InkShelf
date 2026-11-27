package eu.kanade.tachiyomi.ui.inkshelf.shell

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
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.setting.InkShelfLocalComicImportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native 2.0 file picker backed by the exact established local importer. */
@Composable
internal fun InkShelf2LocalImportScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            importResult = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { InkShelfLocalComicImportEngine.importComics(context.applicationContext, uris) }
                }
                importing = false
                result.onSuccess {
                    importResult = buildString {
                        append(it.imported)
                        append(" files processed  •  ")
                        append(it.ready)
                        append(" comic series ready in Library")
                        if (it.skipped > 0) append("  •  " + it.skipped + " skipped")
                        if (it.failed > 0) append("  •  " + it.failed + " failed")
                    }
                    onImported()
                }.onFailure {
                    importResult = "Import failed: " + (it.message ?: "Storage unavailable")
                    Toast.makeText(context, "Local comic import failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 22.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back to Menu", tint = InkShelf2Colors.Text)
                }
                InkShelf2PageHeading("Import Your Comics")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Bring your own digital comics into ARC without creating an online account.",
                    color = InkShelf2Colors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Choose one or several CBZ, CBR, ZIP, RAR or EPUB files. ARC reads ComicInfo.xml where available, groups issues into series and checks for duplicate files.",
                    color = InkShelf2Colors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                InkShelf2ActionButton(
                    label = if (importing) "Importing Comics…" else "Choose Comic Files",
                    leadingIcon = Icons.Outlined.FileUpload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importing,
                    onClick = { pickFiles.launch(arrayOf("*/*")) },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Local files are copied into ARC's existing Local Source and registered in your Library.",
                    color = InkShelf2Colors.Muted,
                    fontSize = 12.sp,
                )
                if (importing) {
                    Spacer(Modifier.height(12.dp))
                    Text("Importing and checking files…", color = InkShelf2Colors.AccentLight, fontSize = 12.sp)
                }
                importResult?.let { result ->
                    Spacer(Modifier.height(16.dp))
                    Text(result, color = InkShelf2Colors.AccentLight, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}
