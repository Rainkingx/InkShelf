package eu.kanade.tachiyomi.widget

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * An [E2EBottomSheetDialog] with a header or button row that should stay flush with the real
 * bottom edge of the window as the sheet is dragged/resized, instead of assuming its position
 * from independently-tracked heights (which don't always compose cleanly with the window's own
 * bounds, e.g. in desktop windowing mode).
 */
@Suppress("LeakingThis")
abstract class StickyFooterBottomSheetDialog<VB : ViewBinding>(
    private val activity: Activity,
) : E2EBottomSheetDialog<VB>(activity) {
    protected abstract val stickyFooterView: View

    init {
        updateStickyFooterPosition()
        setOnShowListener { updateStickyFooterPosition() }
        sheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(
                    bottomSheet: View,
                    slideOffset: Float,
                ) = updateStickyFooterPosition()

                override fun onStateChanged(
                    bottomSheet: View,
                    newState: Int,
                ) = updateStickyFooterPosition()
            },
        )
        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateStickyFooterPosition() }
        stickyFooterView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        setOnDismissListener {
            stickyFooterView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        }
    }

    protected fun updateStickyFooterPosition() {
        val footerLoc = IntArray(2)
        stickyFooterView.getLocationOnScreen(footerLoc)
        val naturalBottom = footerLoc[1] - stickyFooterView.translationY + stickyFooterView.height

        val decorLoc = IntArray(2)
        activity.window.decorView.getLocationOnScreen(decorLoc)
        val decorBottom = decorLoc[1] + activity.window.decorView.height

        stickyFooterView.translationY = decorBottom - naturalBottom
    }
}
