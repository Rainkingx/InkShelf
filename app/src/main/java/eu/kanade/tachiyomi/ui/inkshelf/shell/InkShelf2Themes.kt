package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
) {
    val isLight: Boolean get() = background.luminance() > 0.35f
    val text: Color get() = if (isLight) Color(0xFF24212B) else Color(0xFFFBF8FF)
    val muted: Color get() = if (isLight) Color(0xFF514B5C) else Color(0xFFBCB1C7)
    val onAccent: Color get() = if (isLight) Color.White else Color(0xFF08060D)
}

internal data class InkShelf2Preset(
    val id: String,
    val title: String,
    val description: String,
    val colors: InkShelf2Palette,
)

internal data class InkShelf2EventSkin(
    val metal: Color, val metalLight: Color, val gold: Color,
    val text: Color, val muted: Color, val energy: Color, val pulseTitle: String,
)

internal object InkShelf2Themes {
    const val DOOM_ID = "doom_latveria"
    const val SYSTEM_ID = "system"
    const val DEFAULT_LIGHT_ID = "spider_gwen"
    private val doomSkin = InkShelf2EventSkin(
        Color(0xFF20252B), Color(0xFF353C43), Color(0xFFC2A66D),
        Color(0xFFE5E8EB), Color(0xFFB4BDC4), Color(0xFF36E88A), "The Latverian Times",
    )
    private const val PREFS = "inkshelf_2_theme_v1"
    private const val SELECTED = "selected"
    private const val DOOM_ACTIVATED = "doom_december_2026_auto_activated"
    private const val DOOM_START = "2026-12-01"
    private const val DOOM_END = "2026-12-31"

    private fun todayUtc(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    var limitedThemesVisible by mutableStateOf(false)
        private set

    // One automatic activation per installation during December. Later user
    // selections remain respected, and the edition stays available afterwards.
    fun refreshScheduledEvent(context: Context) {
        val today = todayUtc()
        limitedThemesVisible = today >= DOOM_START
        val prefs = preferences(context)
        if (today < DOOM_START || today > DOOM_END || prefs.getBoolean(DOOM_ACTIVATED, false)) return
        // An explicit system choice must not be overridden by the seasonal event.
        if (prefs.getString(SELECTED, "original") == SYSTEM_ID) {
            prefs.edit().putBoolean(DOOM_ACTIVATED, true).apply()
            return
        }
        val doom = presets.first { it.id == DOOM_ID }
        prefs.edit().putString(SELECTED, DOOM_ID).putBoolean(DOOM_ACTIVATED, true).apply()
        selectedId = DOOM_ID
        current = doom.colors
    }
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
            Color(bg.toInt()), Color(surface.toInt()), Color(raised.toInt()),
            Color(primary.toInt()), Color(highlight.toInt()), Color(border.toInt()),
        ),
    )

    val presets = listOf(
        preset(
            "original", "InkShelf Original", "Signature purple & black",
            0xFF08060D, 0xFF150E21, 0xFF21132D, 0xFFB72AF2, 0xFFE169F7, 0xFF744686,
        ),
        preset(
            "iron_man", "Iron Man", "Armour red & gold",
            0xFF140A0C, 0xFF231114, 0xFF35191B, 0xFFE24A43, 0xFFF8C642, 0xFF87512D,
        ),
        preset(
            "x_men", "X-Men", "Mutant yellow & blue",
            0xFF080F20, 0xFF111D36, 0xFF192B4D, 0xFF377CF4, 0xFFFFCF31, 0xFF566D9E,
        ),
        preset(
            "hulk", "Hulk", "Gamma green & purple",
            0xFF100A19, 0xFF1B1026, 0xFF28153A, 0xFF6BCD47, 0xFFBE83ED, 0xFF5D4580,
        ),
        preset(
            "batman", "Batman", "Gotham black & gold",
            0xFF08090D, 0xFF15171D, 0xFF23252E, 0xFFE2B93F, 0xFFFFDC61, 0xFF6D6450,
        ),
        preset(
            "spider_man", "Spider-Man", "Web-slinger red & blue",
            0xFF090F20, 0xFF151B32, 0xFF1D2B49, 0xFFE84B51, 0xFF5394FF, 0xFF6B527F,
        ),
        preset(
            DOOM_ID, "Doom — Reign of Latveria", "December special · Emerald runes & cinematic opening",
            0xFF090C10, 0xFF1A2025, 0xFF30373F, 0xFF37CB7B, 0xFF80F3B3, 0xFF68747D,
        ),
        preset(
            "amoled", "AMOLED Black", "Pure black & electric violet",
            0xFF000000, 0xFF0D0A12, 0xFF17101E, 0xFFAE48F4, 0xFFE68AFF, 0xFF513762,
        ),
        preset(
            "spider_gwen", "Spider-Gwen", "White, pink & turquoise",
            0xFFFFFFFF, 0xFFFFF3F8, 0xFFF9E7F0, 0xFFC21869, 0xFF007773, 0xFFE1B7CB,
        ),
        preset(
            "fantastic_four", "Fantastic Four", "Ice white & electric blue",
            0xFFFFFFFF, 0xFFF0F7FF, 0xFFE3EFFC, 0xFF1763B5, 0xFF2256A3, 0xFFB5CBE5,
        ),
        preset(
            "moon_knight", "Moon Knight", "Ivory, silver & charcoal",
            0xFFFAFAF5, 0xFFF2F2ED, 0xFFE4E6E8, 0xFF505D6B, 0xFF525C68, 0xFFC4C8CD,
        ),
        preset(
            "captain_america", "Captain America", "White, navy & crimson",
            0xFFFFFFFF, 0xFFF1F5FC, 0xFFE6ECF6, 0xFF193D80, 0xFFB82038, 0xFFB9C7E2,
        ),
        preset(
            "storm", "Storm", "Pearl white, gold & violet",
            0xFFFAF9F6, 0xFFF5F1FA, 0xFFECE6F4, 0xFF6841A5, 0xFF806000, 0xFFD1C4DF,
        ),
        preset(
            "she_hulk", "She-Hulk", "White, emerald & purple",
            0xFFFFFFFF, 0xFFF1F8F1, 0xFFE4F1E6, 0xFF23743D, 0xFF7836A5, 0xFFB8D4BE,
        ),
    )

    fun systemPalette(uiMode: Int): InkShelf2Palette =
        if (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) original
        else presets.first { it.id == DEFAULT_LIGHT_ID }.colors

    fun updateSystem(uiMode: Int) {
        if (selectedId == SYSTEM_ID) current = systemPalette(uiMode)
    }

    val original: InkShelf2Palette get() = presets.first().colors

    var current by mutableStateOf(original)
        private set

    var selectedId by mutableStateOf("original")
        private set

    val eventSkin: InkShelf2EventSkin?
        get() = if (selectedId == DOOM_ID && current == presets.first { it.id == DOOM_ID }.colors)
            doomSkin else null

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
        refreshScheduledEvent(context)
        val requested = preferences(context).getString(SELECTED, "original").orEmpty()
        val chosen = when (requested) {
            "custom" -> savedCustom(context)
            SYSTEM_ID -> systemPalette(context.resources.configuration.uiMode)
            else -> presets.firstOrNull { it.id == requested }?.colors ?: original
        }
        selectedId = if (requested == "custom" || requested == SYSTEM_ID || presets.any { it.id == requested }) requested else "original"
        current = chosen
    }

    fun hasSavedCustom(context: Context): Boolean =
        preferences(context).contains(CUSTOM_PRIMARY)

    fun select(context: Context, id: String) {
        if (id == DOOM_ID && !limitedThemesVisible) return
        if (id == SYSTEM_ID) {
            preferences(context).edit().putString(SELECTED, SYSTEM_ID).apply()
            selectedId = SYSTEM_ID
            current = systemPalette(context.resources.configuration.uiMode)
            return
        }
        if (id == "custom") {
            if (!hasSavedCustom(context)) return
            preferences(context).edit().putString(SELECTED, "custom").apply()
            selectedId = "custom"
            current = savedCustom(context)
            return
        }
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
