package eu.kanade.tachiyomi.widget

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.util.compose.AppComposeTheme

/**
 * A [BottomSheetDialog] that hosts Compose content instead of an inflated/bound XML layout.
 *
 * Content is wrapped in [AppComposeTheme], which reads [activity]'s currently active theme
 * attributes, so it automatically matches whichever of the app's dynamic Material3 themes is
 * selected.
 *
 * The dialog's window is a separate view hierarchy from the activity's, so [ComposeView] can't
 * find a [androidx.lifecycle.LifecycleOwner]/[androidx.lifecycle.ViewModelStoreOwner]/
 * [androidx.savedstate.SavedStateRegistryOwner] on its own the way it would inside the activity's
 * own content view - those are propagated from [activity] explicitly below.
 */
class ComposeBottomSheetDialog(
    private val activity: ComponentActivity,
) : BottomSheetDialog(activity) {
    private val composeView = ComposeView(activity)

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.setViewTreeLifecycleOwner(activity)
        composeView.setViewTreeViewModelStoreOwner(activity)
        composeView.setViewTreeSavedStateRegistryOwner(activity)

        setContentView(composeView)

        val activityWic = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        window?.let { window ->
            val wic = WindowInsetsControllerCompat(window, composeView)
            wic.isAppearanceLightNavigationBars = activityWic.isAppearanceLightStatusBars
            wic.isAppearanceLightStatusBars = false
        }
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            AppComposeTheme {
                // Establishes LocalContentColor for descendants; without it, any Text with no
                // explicit color falls back to Compose's hardcoded black default instead of the
                // theme's onSurface, which only happens to read fine in light mode.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    content()
                }
            }
        }
    }
}
