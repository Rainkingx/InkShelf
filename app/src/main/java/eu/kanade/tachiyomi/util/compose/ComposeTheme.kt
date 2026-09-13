package eu.kanade.tachiyomi.util.compose

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Builds a Compose [ColorScheme] from [this] context's currently active XML Material3 theme
 * attributes, so Compose content stays in sync with the app's dynamic theme (Lime, TokyoNight,
 * AMOLED, etc.) without depending on a separate (and, in the case of compose-theme-adapter-3,
 * deprecated/archived) theme-adapter library.
 */
fun Context.toComposeColorScheme(): ColorScheme {
    fun color(attr: Int) = Color(getResourceColor(attr))

    val surface = color(R.attr.colorSurface)
    val isLight = ColorUtils.calculateLuminance(surface.toArgb()) > 0.5
    val base = if (isLight) lightColorScheme() else darkColorScheme()

    return base.copy(
        primary = color(R.attr.colorPrimary),
        onPrimary = color(R.attr.colorOnPrimary),
        primaryContainer = color(R.attr.colorPrimaryContainer),
        onPrimaryContainer = color(R.attr.colorOnPrimaryContainer),
        inversePrimary = color(R.attr.colorPrimaryInverse),
        secondary = color(R.attr.colorSecondary),
        onSecondary = color(R.attr.colorOnSecondary),
        secondaryContainer = color(R.attr.colorSecondaryContainer),
        onSecondaryContainer = color(R.attr.colorOnSecondaryContainer),
        tertiary = color(R.attr.colorTertiary),
        onTertiary = color(R.attr.colorOnTertiary),
        tertiaryContainer = color(R.attr.colorTertiaryContainer),
        onTertiaryContainer = color(R.attr.colorOnTertiaryContainer),
        background = color(android.R.attr.colorBackground),
        onBackground = color(R.attr.colorOnBackground),
        surface = surface,
        onSurface = color(R.attr.colorOnSurface),
        surfaceVariant = color(R.attr.colorSurfaceVariant),
        onSurfaceVariant = color(R.attr.colorOnSurfaceVariant),
        surfaceContainer = color(R.attr.colorSurfaceContainer),
        surfaceContainerHigh = color(R.attr.colorSurfaceContainerHigh),
        surfaceContainerHighest = color(R.attr.colorSurfaceContainerHighest),
        surfaceContainerLow = color(R.attr.colorSurfaceContainerLow),
        surfaceContainerLowest = color(R.attr.colorSurfaceContainerLowest),
        surfaceDim = color(R.attr.colorSurfaceDim),
        surfaceBright = color(R.attr.colorSurfaceBright),
        outline = color(R.attr.colorOutline),
        outlineVariant = color(R.attr.colorOutlineVariant),
        error = color(R.attr.colorError),
        onError = color(R.attr.colorOnError),
        errorContainer = color(R.attr.colorErrorContainer),
        onErrorContainer = color(R.attr.colorOnErrorContainer),
    )
}

@Composable
fun AppComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) { context.toComposeColorScheme() }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
