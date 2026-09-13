package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isEmpty
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.pow

/**
 * A [RecyclerView] that shrinks, fades, and pulls each cover toward the front (start) slot the
 * farther it scrolls from it, drawing the one closest to front on top. Transforms and draw order
 * are recomputed synchronously in [dispatchDraw] on every draw pass (not from scroll/attach
 * listeners), so a child is never rendered with a stale transform from wherever it was recycled.
 */
class MangaStackRecyclerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RecyclerView(context, attrs) {
        private var drawOrder = IntArray(0)
        private var initialSnapChecked = false
        private var pitch = 0f

        /** When false, the stack is a static row - there's nothing to scroll to. */
        var isScrollingEnabled = true

        override fun canScrollHorizontally(direction: Int) = isScrollingEnabled && super.canScrollHorizontally(direction)

        init {
            isChildrenDrawingOrderEnabled = true
        }

        override fun dispatchDraw(canvas: Canvas) {
            pitch = (getChildAt(0)?.width?.toFloat()?.takeIf { it > 0f } ?: 0f) * (1f - STACK_OVERLAP_FRACTION)
            applyStackTransforms()
            ensureInitialSnap()
            super.dispatchDraw(canvas)
        }

        /**
         * A one-time verify-and-correct pass for the first real draw: scrollToPositionWithOffset
         * requested during dialog setup is a pending instruction the LayoutManager only applies
         * on its next layout, and a dialog settling into its final size can go through more than
         * one of those - so it can get consumed against a not-yet-final width and leave the
         * front cover not actually flush until the user's first manual scroll. Checked here
         * instead, once sizing is guaranteed final, and corrected with a direct scrollBy of the
         * measured gap (scrollToPositionWithOffset's own "offset" didn't land flush either, since
         * it interacts with each item's negative overlap margin).
         */
        private fun ensureInitialSnap() {
            if (initialSnapChecked || isEmpty()) return
            initialSnapChecked = true

            var closestDelta = Int.MAX_VALUE
            for (i in 0 until childCount) {
                val delta = getChildAt(i).left - paddingStart
                if (abs(delta) < abs(closestDelta)) closestDelta = delta
            }
            if (closestDelta == 0 || closestDelta == Int.MAX_VALUE) return
            post { scrollBy(closestDelta, 0) }
        }

        override fun getChildDrawingOrder(
            childCount: Int,
            i: Int,
        ): Int {
            if (i == 0) drawOrder = computeDrawOrder(childCount)
            return drawOrder.getOrElse(i) { i }
        }

        private fun applyStackTransforms() {
            if (pitch <= 0f) return

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                // Capped since PeekingLinearLayoutManager prefetches items well beyond the
                // viewport, and the unbounded translationX pull below could otherwise yank a
                // still off-screen one back into view.
                val magnitude = magnitudeOf(child).coerceAtMost(MAGNITUDE_CAP)
                val offset = (child.left - paddingStart) / pitch
                val scale = STACK_SCALE_STEP.pow(magnitude)
                child.scaleX = scale
                child.scaleY = scale
                // Pull each cover toward the front more the deeper into the stack it is, so
                // overlap grows progressively instead of staying a fixed amount.
                val direction = if (offset >= 0f) 1f else -1f
                child.translationX = -direction * STACK_OVERLAP_GROWTH * magnitude * magnitude * pitch
                val innerAlpha = (1f - magnitude * STACK_FADE_STEP).coerceAtLeast(STACK_MIN_ALPHA)
                (child as? ViewGroup)?.getChildAt(0)?.alpha = innerAlpha
                // Fading just the inner image to 0 still leaves the card's own opaque background
                // as a solid blob, so ride the card's alpha through that same final
                // [0, CARD_FADE_WINDOW] span instead - opaque for the whole visible cascade (no
                // blending between overlapping cards), only fading right at the very end.
                child.alpha = (innerAlpha / CARD_FADE_WINDOW).coerceIn(0f, 1f)
            }
        }

        private fun computeDrawOrder(childCount: Int): IntArray {
            if (pitch <= 0f || childCount == 0) return IntArray(childCount) { it }
            return (0 until childCount)
                .sortedByDescending { index -> magnitudeOf(getChildAt(index)) }
                .toIntArray()
        }

        private fun magnitudeOf(child: View?): Float {
            child ?: return Float.MAX_VALUE
            return abs((child.left - paddingStart) / pitch)
        }

        companion object {
            private const val STACK_SCALE_STEP = 0.9f
            private const val STACK_OVERLAP_GROWTH = 0.12f
            private const val STACK_FADE_STEP = 0.25f

            // 0f rather than a partial floor: STACK_FADE_STEP * MAGNITUDE_CAP == 1f, so alpha
            // hits exactly 0 at the cap, hiding prefetched items
            private const val STACK_MIN_ALPHA = 0f
            private const val MAGNITUDE_CAP = 4f

            /** The inner image's own alpha span, [0, this], over which the card fades out too. */
            private const val CARD_FADE_WINDOW = 0.25f
        }
    }
