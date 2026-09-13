package eu.kanade.tachiyomi.util.view

import android.content.res.ColorStateList
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Background/text colors for genre/tag chips: [defaultBackground]/[defaultForeground] for tags
 * that came from the source (an accent-tinted blend of [R.attr.background] and [R.attr.colorPrimary]),
 * [customBackground]/[customForeground] (colorPrimary/colorOnPrimary) for tags the user added locally.
 */
class TagChipColors(
    val defaultBackground: Int,
    val defaultForeground: Int,
    val customBackground: Int,
    val customForeground: Int,
)

fun View.tagChipColors(
    dark: Boolean,
    amoled: Boolean,
): TagChipColors {
    val bgArray = FloatArray(3)
    val accentArray = FloatArray(3)
    ColorUtils.colorToHSL(context.getResourceColor(R.attr.background), bgArray)
    ColorUtils.colorToHSL(context.getResourceColor(R.attr.colorPrimary), accentArray)

    val defaultBackground =
        ColorUtils.setAlphaComponent(
            ColorUtils.HSLToColor(
                floatArrayOf(
                    bgArray[0],
                    bgArray[1],
                    when {
                        amoled && dark -> 0.1f
                        dark -> 0.225f
                        else -> 0.85f
                    },
                ),
            ),
            199,
        )
    val defaultForeground =
        ColorUtils.HSLToColor(
            floatArrayOf(accentArray[0], accentArray[1], if (dark) 0.945f else 0.175f),
        )

    return TagChipColors(
        defaultBackground = defaultBackground,
        defaultForeground = defaultForeground,
        customBackground = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary),
        customForeground = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary),
    )
}

fun Chip.applyTagColors(
    colors: TagChipColors,
    isCustom: Boolean,
) {
    val background = if (isCustom) colors.customBackground else colors.defaultBackground
    val foreground = if (isCustom) colors.customForeground else colors.defaultForeground
    chipBackgroundColor = ColorStateList.valueOf(background)
    setTextColor(foreground)
    closeIconTint = ColorStateList.valueOf(foreground)
}
