package eu.kanade.tachiyomi.ui.reader

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.inkshelf.shell.InkShelf2Colors

/**
 * A visual bridge for the established Android reader widgets. No changes to
 * layout IDs, listeners, page rendering, brightness or color-filter values.
 */
internal object InkShelfReaderChrome {
    val textColor: Int get() = InkShelf2Colors.Text.toArgb()
    val accentColor: Int get() = InkShelf2Colors.AccentLight.toArgb()
    val mutedColor: Int get() = InkShelf2Colors.Muted.toArgb()
    val toolbarColor: Int get() = ColorUtils.setAlphaComponent(InkShelf2Colors.Background.toArgb(), 232)

    private fun tint(color: Int) = ColorStateList.valueOf(color)

    private fun controlColors(normal: Int = textColor) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(ColorUtils.setAlphaComponent(mutedColor, 110), accentColor, normal),
    )

    fun surface(view: View, translucent: Boolean = false) {
        val color = InkShelf2Colors.Surface.toArgb()
        ViewCompat.setBackgroundTintList(view, tint(if (translucent) ColorUtils.setAlphaComponent(color, 235) else color))
    }

    fun font(view: TextView) {
        val bold = view.typeface?.isBold == true
        val family = ResourcesCompat.getFont(view.context, if (bold) R.font.oxanium_semibold else R.font.oxanium_regular)
        if (family != null) view.setTypeface(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun walk(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) walk(root.getChildAt(i), action)
        }
    }

    fun typography(root: View) {
        walk(root) { if (it is TextView) font(it) }
    }

    fun slider(slider: Slider) {
        slider.trackActiveTintList = tint(accentColor)
        slider.trackInactiveTintList = tint(InkShelf2Colors.Border.toArgb())
        slider.thumbTintList = tint(accentColor)
    }

    fun navigation(root: View) {
        walk(root) { view ->
            when (view) {
                is MaterialButton -> view.iconTint = controlColors()
                is ImageView -> view.imageTintList = controlColors()
                is Slider -> slider(view)
                is TextView -> {
                    font(view)
                    view.setTextColor(controlColors())
                }
            }
        }
    }

    fun settings(root: View, preserveFilterColors: Boolean = false) {
        walk(root) { view ->
            if (view is TextView) font(view)
            when (view) {
                is MaterialSwitch -> {
                    view.thumbTintList = controlColors(mutedColor)
                    view.trackTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(InkShelf2Colors.Border.toArgb(), InkShelf2Colors.SurfaceRaised.toArgb()),
                    )
                    view.setTextColor(controlColors())
                }
                is MaterialButton -> if (!preserveFilterColors) {
                    view.setTextColor(controlColors())
                    view.iconTint = controlColors()
                    view.strokeColor = tint(InkShelf2Colors.Border.toArgb())
                }
                is Slider -> if (!preserveFilterColors) slider(view)
                is TextView -> if (!preserveFilterColors) {
                    val oldPrimary = MaterialColors.getColor(view, R.attr.colorPrimary, accentColor)
                    view.setTextColor(controlColors(if (view.currentTextColor == oldPrimary) accentColor else textColor))
                }
            }
        }
    }

    fun tabs(tabs: TabLayout) {
        tabs.setTabTextColors(mutedColor, accentColor)
        tabs.setSelectedTabIndicatorColor(accentColor)
        typography(tabs)
    }
}
