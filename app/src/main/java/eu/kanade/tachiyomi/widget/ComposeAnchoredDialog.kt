package eu.kanade.tachiyomi.widget

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.captionBar
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.compose.AppComposeTheme
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.end
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.start
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat

/**
 * A [Dialog] that hosts Compose content anchored below [anchor] and pinned to the end (right, in
 * LTR) of the screen - the OverflowDialog/Discord-style dropdown look, rather than a bottom sheet.
 *
 * The vertical position is measured from [anchor]'s actual on-screen location at construction
 * time rather than assumed from a fixed toolbar height, since the search bar it lives in can move
 * (e.g. collapsing toolbar behavior) - unlike [OverflowDialog], which anchors to a location that's
 * always the same relative to the window.
 */
class ComposeAnchoredDialog(
    private val activity: MainActivity,
    private val anchor: View,
) : Dialog(activity, R.style.SearchDialogTheme) {
    private val composeView = ComposeView(activity)

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

        val touchOutside =
            View(activity).apply {
                setOnClickListener { dismiss() }
            }

        val anchorLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)

        val insets = activity.cachedSystemInsets
        val root =
            FrameLayout(activity).apply {
                addView(
                    touchOutside,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                addView(
                    composeView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.TOP or Gravity.END
                        topMargin = anchorLocation[1] + anchor.height + 2.dpToPx -
                            (
                                activity.window.decorView.rootWindowInsetsCompat
                                    ?.getInsets(captionBar())
                                    ?.top ?: 0
                            )
                        marginEnd = 14.dpToPx + insets.end(context.resources.isLTR)
                        marginStart = 14.dpToPx + insets.start(context.resources.isLTR)
                        bottomMargin = 4.dpToPx + insets.bottom
                    },
                )
            }

        root.doOnApplyWindowInsetsCompat { _, insets, _ ->
            composeView.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = 4.dpToPx + insets.ignoredSystemInsets.bottom
            }
        }

        // Compose's window-level recomposer looks up these owners starting from the dialog's
        // actual content root (root), not from composeView itself - setting them only on
        // composeView (a descendant) leaves that lookup unable to find them.
        root.setViewTreeLifecycleOwner(activity)
        root.setViewTreeViewModelStoreOwner(activity)
        root.setViewTreeSavedStateRegistryOwner(activity)

        setContentView(root)
        window?.let { window ->
            window.decorView.fitsSystemWindows = false
            val wic = WindowInsetsControllerCompat(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            wic.isAppearanceLightStatusBars = false
            wic.isAppearanceLightNavigationBars = false
        }
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            val halfScreenWidth = LocalWindowInfo.current.containerDpSize.width * 0.66f
            val width = halfScreenWidth.coerceIn(220.dp, 480.dp)

            AppComposeTheme {
                Surface(
                    modifier = Modifier.width(width),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    content()
                }
            }
        }
    }
}
