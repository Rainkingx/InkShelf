package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * InkShelf 2.0 appearance is independent from the legacy J2K theme.
 * The selected palette is snapshot state so ALL Compose destinations update immediately.
 * Only colours change: comic engines, screen layouts, extensions and reader are untouched.
 */
internal data class InkShelf2Palette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val accent: Color,
    val highlight: Color,
    val border: Color,
)

internal data class InkShelf2Preset(
    val id: String,
    val title: String,
    val description: String,
    val colors: InkShelf2Palette,
)

internal object InkShelf2Themes {
    private const val PREFS = "inkshelf_2_theme_v1"
    private const val SELECTED = "selected"
    private const val CUSTOM_PRIMARY = "custom_primary"
    private const val CUSTOM_HIGHLIGHT = "custom_highlight"
    private const val CUSTOM_BACKGROUND = "custom_background"

    private fun preset(
        id: String,
        title: String,
        description: String,
        bg: Long,
        surface: Long,
        raised: Long,
        primary: Long,
        highlight: Long,
        border: Long,
    ) = InkShelf2Preset(
        id, title, description,
        InkShelf2Palette(
            Color(bg), Color(surface), Color(raised),
            Color(primary), Color(highlight), Color(border),
        ),
    )

    val presets = listOf(
        preset(
            "original", "InkShelf Original", "Signature purple & black",
            0xFF08060DL, 0xFF150E21L, 0xFF21132DL, 0xFFB72AF2L, 0xFFE169F7L, 0xFF744686L,
        ),
        preset(
            "iron_man", "Iron Man", "Armour red & gold",
            0xFF140A0CL, 0xFF231114L, 0xFF35191BL, 0xFFE24A43L, 0xFFF8C642L, 0xFF87512DL,
        ),
        preset(
            "x_men", "X-Men", "Mutant yellow & blue",
            0xFF080F20L, 0xFF111D36L, 0xFF192B4DL, 0xFF377CF4L, 0xFFFFCF31L, 0xFF566D9EL,
        ),
        preset(
            "hulk", "Hulk", "Gamma green & purple",
            0xFF100A19L, 0xFF1B1026L, 0xFF28153AL, 0xFF6BCD47L, 0xFFBE83EDL, 0xFF5D4580L,
        ),
        preset(
            "batman", "Batman", "Gotham black & gold",
            0xFF08090DL, 0xFF15171DL, 0xFF23252EL, 0xFFE2B93FL, 0xFFFFDC61L, 0xFF6D6450L,
        ),
        preset(
            "spider_man", "Spider-Man", "Web-slinger red & blue",
            0xFF090F20L, 0xFF151B32L, 0xFF1D2B49L, 0xFFE84B51L, 0xFF5394FFL, 0xFF6B527FL,
        ),
        preset(
            "amoled", "AMOLED Black", "Pure black & electric violet",
            0xFF000000L, 0xFF0D0A12L, 0xFF17101EL, 0xFFAE48F4L, 0xFFE68AFFL, 0xFF513762L,
        ),
    )

    val original: InkShelf2Palette get() = presets.first().colors

    var current by mutableStateOf(original)
        private set

    var selectedId by mutableStateOf("original")
        private set

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun customColor(context: Context, key: String, fallback: Color): Color {
        val stored = preferences(context).getInt(key, fallback.toArgb())
        return Color(stored)
    }

    fun savedCustom(context: Context): InkShelf2Palette = custom(
        customColor(context, CUSTOM_PRIMARY, original.accent),
        customColor(context, CUSTOM_HIGHLIGHT, original.highlight),
        customColor(context, CUSTOM_BACKGROUND, original.background),
    )

    fun custom(primary: Color, secondary: Color, bg: Color): InkShelf2Palette =
        InkShelf2Palette(
            background = bg,
            surface = lerp(bg, primary, 0.11f).copy(alpha = 1f),
            surfaceRaised = lerp(bg, primary, 0.19f).copy(alpha = 1f),
            accent = primary,
            highlight = secondary,
            border = lerp(bg, secondary, 0.48f).copy(alpha = 1f),
        )

    fun restore(context: Context) {
        val requested = preferences(context).getString(SELECTED, "original").orEmpty()
        val chosen = if (requested == "custom") savedCustom(context)
            else presets.firstOrNull { it.id == requested }?.colors ?: original
        selectedId = if (requested == "custom" || presets.any { it.id == requested }) requested else "original"
        current = chosen
    }

    fun select(context: Context, id: String) {
        val chosen = presets.firstOrNull { it.id == id } ?: return
        preferences(context).edit().putString(SELECTED, chosen.id).apply()
        selectedId = chosen.id
        current = chosen.colors
    }

    /** Unsaved editor changes affect only the running Compose preview. */
    fun previewCustom(primary: Color, secondary: Color, background: Color) {
        current = custom(primary, secondary, background)
    }

    fun saveCustom(context: Context, primary: Color, secondary: Color, background: Color) {
        preferences(context).edit()
            .putInt(CUSTOM_PRIMARY, primary.toArgb())
            .putInt(CUSTOM_HIGHLIGHT, secondary.toArgb())
            .putInt(CUSTOM_BACKGROUND, background.toArgb())
            .putString(SELECTED, "custom")
            .apply()
        selectedId = "custom"
        current = custom(primary, secondary, background)
    }
}
