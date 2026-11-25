package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** Native InkShelf 2.0 appearance picker, with live previews and saved colours. */
@Composable
internal fun InkShelf2ThemesScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var cinematicEnabled by remember(context) { mutableStateOf(InkShelf2CinematicSplash.enabled(context)) }
    var splashSeconds by remember(context) { mutableStateOf(InkShelf2CinematicSplash.durationSeconds(context)) }
    var artworkOpen by rememberSaveable { mutableStateOf(false) }
    var pendingArtwork by rememberSaveable { mutableStateOf(-1) }
    var artworkEpoch by remember { mutableStateOf(0) }
    var importingArtwork by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val index = pendingArtwork
        pendingArtwork = -1
        if (uri != null && index in InkShelf2SplashArtwork.slots.indices) {
            importingArtwork = true
            scope.launch {
                try {
                    val ok = withContext(Dispatchers.IO) { InkShelf2SplashArtwork.importImage(context, index, uri) }
                    artworkEpoch += 1
                    Toast.makeText(context, if (ok) "Artwork saved for next cold launch" else "Could not read this image", Toast.LENGTH_SHORT).show()
                } finally {
                    importingArtwork = false
                }
            }
        }
    }
    val storedCustom = remember(context) { InkShelf2Themes.savedCustom(context) }
    val startingPalette = if (InkShelf2Themes.selectedId == "custom") storedCustom
        else InkShelf2Themes.current
    var primary by remember { mutableStateOf(startingPalette.accent) }
    var secondary by remember { mutableStateOf(startingPalette.highlight) }
    var background by remember { mutableStateOf(startingPalette.background) }
    var editingCustom by remember { mutableStateOf(false) }
    var editorSlot by remember { mutableStateOf<String?>(null) }

    // An unsaved preview is discarded if the user leaves Themes via Back or another tab.
    // Saved preset and custom selections persist across launches.
    DisposableEffect(context) {
        onDispose { InkShelf2Themes.restore(context) }
    }

    fun previewChanges(a: Color = primary, b: Color = secondary, bg: Color = background) {
        primary = a
        secondary = b
        background = bg
        editingCustom = true
        InkShelf2Themes.previewCustom(a, b, bg)
    }

    editorSlot?.let { slot ->
        // Keep the colour from before opening this dialog so Cancel actually
        // discards slider changes, rather than restoring the last preview value.
        val chosen = remember(slot) {
            when (slot) {
                "Accent" -> primary
                "Highlight" -> secondary
                else -> background
            }
        }
        InkShelf2ColorEditor(
            label = slot,
            initial = chosen,
            onChange = { color ->
                when (slot) {
                    "Accent" -> previewChanges(a = color)
                    "Highlight" -> previewChanges(b = color)
                    else -> previewChanges(bg = color)
                }
            },
            onDone = { editorSlot = null },
            onCancel = {
                when (slot) {
                    "Accent" -> previewChanges(a = chosen)
                    "Highlight" -> previewChanges(b = chosen)
                    else -> previewChanges(bg = chosen)
                }
                editorSlot = null
            },
        )
    }

    val preview = InkShelf2Themes.current
    val savedThemeId = InkShelf2Themes.selectedId
    val backgroundTooLight = background.luminance() > 0.20f
    LazyColumn(
        modifier = Modifier.fillMaxSize().inkShelf2Atmosphere(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back to Menu", tint = InkShelf2Colors.Text)
                }
                InkShelf2PageHeading("Appearance & Themes", Modifier.weight(1f))
            }
            Text(
                "Comic-inspired colours for InkShelf 2.0. Your library, source extensions and reader are unchanged.",
                modifier = Modifier.padding(horizontal = 18.dp),
                color = InkShelf2Colors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        item {
            InkShelfThemePreview(preview)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cinematic opening", color = InkShelf2Colors.Text, fontWeight = FontWeight.Bold)
                    Text("Play your image sequence on cold startup", color = InkShelf2Colors.Muted, fontSize = 12.sp)
                }
                androidx.compose.material3.Switch(checked = cinematicEnabled, onCheckedChange = {
                    cinematicEnabled = it
                    InkShelf2CinematicSplash.setEnabled(context, it)
                })
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Splash speed", modifier = Modifier.weight(1f),
                        color = InkShelf2Colors.Text, fontWeight = FontWeight.Bold)
                    Text("$splashSeconds seconds", color = InkShelf2Colors.AccentLight, fontSize = 13.sp)
                }
                Slider(
                    value = splashSeconds.toFloat(),
                    onValueChange = { splashSeconds = it.roundToInt() },
                    onValueChangeFinished = {
                        InkShelf2CinematicSplash.setDurationSeconds(context, splashSeconds)
                    },
                    valueRange = InkShelf2CinematicSplash.MIN_DURATION_SECONDS.toFloat()..
                        InkShelf2CinematicSplash.MAX_DURATION_SECONDS.toFloat(),
                    steps = InkShelf2CinematicSplash.MAX_DURATION_SECONDS -
                        InkShelf2CinematicSplash.MIN_DURATION_SECONDS - 1,
                    enabled = cinematicEnabled,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fast · 3s", color = InkShelf2Colors.Muted, fontSize = 11.sp)
                    Text("Slow · 7s", color = InkShelf2Colors.Muted, fontSize = 11.sp)
                }
                Text("Changes apply on the next cold launch. Tap the splash to skip.",
                    color = InkShelf2Colors.Muted, fontSize = 12.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                TextButton(onClick = { artworkOpen = !artworkOpen }) {
                    Text(if (artworkOpen) "Hide custom images" else "Add your own splash",
                        color = InkShelf2Colors.AccentLight)
                }
                if (artworkOpen) {
                    Text("Use your own permitted PNG, JPEG or WebP artwork. Transparent, isolated images work best. Files stay on this device.",
                        color = InkShelf2Colors.Muted, fontSize = 12.sp)
                    InkShelf2SplashArtwork.slots.forEachIndexed { index, slot ->
                        val imported = remember(artworkEpoch, index) { InkShelf2SplashArtwork.has(context, index) }
                        Spacer(Modifier.height(12.dp))
                        Text(slot, color = InkShelf2Colors.Text, fontWeight = FontWeight.Bold)
                        Text(if (imported) "Custom image saved" else "Default image",
                            color = InkShelf2Colors.Muted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !importingArtwork, onClick = {
                                pendingArtwork = index
                                picker.launch("image/*")
                            }) { Text(if (imported) "Replace" else "Choose image") }
                            if (imported) {
                                TextButton(enabled = !importingArtwork, onClick = {
                                    InkShelf2SplashArtwork.reset(context, index)
                                    artworkEpoch += 1
                                }) { Text("Reset") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "READY-MADE THEMES",
                modifier = Modifier.padding(horizontal = 19.dp),
                color = InkShelf2Colors.AccentLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
            )
        }
        items(InkShelf2Themes.presets, key = { it.id }) { preset ->
            val selected = !editingCustom && savedThemeId == preset.id
            InkShelfPresetRow(
                preset,
                selected,
                onClick = {
                    InkShelf2Themes.select(context, preset.id)
                    editingCustom = false
                    primary = preset.colors.accent
                    secondary = preset.colors.highlight
                    background = preset.colors.background
                },
            )
        }
        if (InkShelf2Themes.hasSavedCustom(context)) {
            item(key = "saved-custom") {
                val saved = InkShelf2Themes.savedCustom(context)
                InkShelfPresetRow(
                    InkShelf2Preset("custom", "My Custom Theme", "Your saved colours", saved),
                    selected = !editingCustom && savedThemeId == "custom",
                    onClick = {
                        InkShelf2Themes.select(context, "custom")
                        primary = saved.accent
                        secondary = saved.highlight
                        background = saved.background
                        editingCustom = false
                    },
                )
            }
        }
        item {
            Text(
                "CREATE YOUR OWN",
                modifier = Modifier.padding(start = 19.dp, end = 19.dp, top = 8.dp),
                color = InkShelf2Colors.AccentLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .background(InkShelf2Colors.SurfaceRaised, RoundedCornerShape(16.dp))
                    .border(1.dp, InkShelf2Colors.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Custom Colour Set",
                    color = InkShelf2Colors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Tap a colour to adjust its hue, saturation and brightness. Changes preview immediately.",
                    color = InkShelf2Colors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                InkShelfColorSlot("Accent", primary) { editorSlot = "Accent" }
                InkShelfColorSlot("Highlight", secondary) { editorSlot = "Highlight" }
                InkShelfColorSlot("Background", background) { editorSlot = "Background" }
                if (backgroundTooLight) {
                    Text(
                        "Choose a darker background so comic titles stay readable.",
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 12.sp,
                    )
                }
                InkShelf2ActionButton(
                    label = if (savedThemeId == "custom" && !editingCustom) "Custom Theme Saved ✓" else "Save Custom Theme",
                    enabled = !backgroundTooLight,
                    onClick = {
                        InkShelf2Themes.saveCustom(context, primary, secondary, background)
                        editingCustom = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editingCustom) {
                    TextButton(
                        onClick = {
                            InkShelf2Themes.restore(context)
                            val stored = InkShelf2Themes.current
                            primary = stored.accent
                            secondary = stored.highlight
                            background = stored.background
                            editingCustom = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Discard Preview", color = InkShelf2Colors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun InkShelfThemePreview(palette: InkShelf2Palette) {
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(shape)
            .background(palette.background)
            .border(1.dp, palette.border, shape)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Palette, null, tint = palette.highlight, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(8.dp))
            Text("InkShelf", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("LIVE PREVIEW", color = palette.highlight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text("Discover your next comic", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier.weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (index % 2 == 0) palette.accent.copy(alpha = 0.65f)
                                    else palette.highlight.copy(alpha = 0.58f),
                                    palette.surfaceRaised,
                                ),
                            ),
                        )
                        .border(1.dp, palette.border.copy(alpha = 0.65f), RoundedCornerShape(8.dp)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(palette.surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("HOME", color = palette.highlight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("DISCOVER", color = Color.White, fontSize = 11.sp)
            Text("LIBRARY", color = Color.White, fontSize = 11.sp)
            Text("MENU", color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InkShelfPresetRow(
    preset: InkShelf2Preset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(shape)
            .background(InkShelf2Colors.SurfaceRaised.copy(alpha = 0.84f))
            .border(
                if (selected) 1.7.dp else 1.dp,
                if (selected) InkShelf2Colors.AccentLight else InkShelf2Colors.Border.copy(alpha = 0.55f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(83.dp).height(45.dp).clip(RoundedCornerShape(9.dp)),
        ) {
            Box(Modifier.weight(1f).fillMaxSize().background(preset.colors.background))
            Box(Modifier.weight(1f).fillMaxSize().background(preset.colors.accent))
            Box(Modifier.weight(1f).fillMaxSize().background(preset.colors.highlight))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(preset.title, color = InkShelf2Colors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(preset.description, color = InkShelf2Colors.Muted, fontSize = 11.sp)
        }
        if (selected) {
            Icon(Icons.Outlined.CheckCircle, "Selected theme", tint = InkShelf2Colors.AccentLight)
        } else {
            Icon(Icons.Outlined.ChevronRight, null, tint = InkShelf2Colors.Muted)
        }
    }
}

@Composable
private fun InkShelfColorSlot(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(color)
                .border(1.dp, InkShelf2Colors.Border, RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = InkShelf2Colors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF),
                color = InkShelf2Colors.Muted,
                fontSize = 11.sp,
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = InkShelf2Colors.AccentLight)
    }
}

@Composable
private fun InkShelf2ColorEditor(
    label: String,
    initial: Color,
    onChange: (Color) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val initialHsv = remember(label) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember(label) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(label) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(label) { mutableFloatStateOf(initialHsv[2]) }
    fun computed(h: Float, s: Float, v: Float) =
        Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))
    val swatch = computed(hue, saturation, brightness)

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Choose $label Colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(swatch),
                )
                Text("Hue", color = InkShelf2Colors.Text, fontSize = 12.sp)
                Slider(
                    value = hue, onValueChange = {
                        hue = it
                        onChange(computed(it, saturation, brightness))
                    }, valueRange = 0f..359f,
                )
                Text("Saturation", color = InkShelf2Colors.Text, fontSize = 12.sp)
                Slider(
                    value = saturation, onValueChange = {
                        saturation = it
                        onChange(computed(hue, it, brightness))
                    }, valueRange = 0f..1f,
                )
                Text("Brightness", color = InkShelf2Colors.Text, fontSize = 12.sp)
                Slider(
                    value = brightness, onValueChange = {
                        brightness = it
                        onChange(computed(hue, saturation, it))
                    }, valueRange = 0f..1f,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
