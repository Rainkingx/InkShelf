package eu.kanade.tachiyomi.util.system

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

/** Shifts this color's hue to match [hueOf]'s hue, keeping this color's own saturation, lightness, and alpha */
@ColorInt
fun Int.hueShiftedTo(
    @ColorInt hueOf: Int?,
): Int {
    hueOf ?: return this
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    val hueHsl = FloatArray(3)
    ColorUtils.colorToHSL(hueOf, hueHsl)
    hsl[0] = hueHsl[0]
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(this))
}
