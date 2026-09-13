package eu.kanade.tachiyomi.ui.library

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * Draws a whole library badge: its rounded ends, the fill behind each part, and the slanted divider
 * between them. Every part used to carry a shaped background of its own, with the dividers drawn as
 * two more views on top, so a badge cost several path draws where this costs one.
 */
class LibraryBadgeDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePath = Path()
    private val partPath = Path()
    private val rect = RectF()
    private val radii = FloatArray(8)

    private val partColors = IntArray(MAX_PARTS)
    private val partStarts = FloatArray(MAX_PARTS)
    private var partCount = 0

    private var startRadius = 0f
    private var endRadius = 0f
    private var roundEnd = false
    private var uniform = false
    private var isRtl = false
    private var outlineDirty = true
    private var alpha = 255

    private var dividerSlant = 0f
    private var smallCorner = 0f

    /**
     * The shared dpToPx/spToPx helpers read Resources.getSystem(), which always reflects the
     * phone's own default display - never whatever display this badge actually ends up drawn on
     * (an external monitor, say). So the density has to come from the badge's own window instead
     * of a process-wide constant. Returns whether it actually changed.
     */
    fun setDensity(density: Float): Boolean {
        val newDividerSlant = DIVIDER_SLANT_DP * density
        val newSmallCorner = SMALL_CORNER_DP * density
        if (newDividerSlant == dividerSlant && newSmallCorner == smallCorner) return false
        dividerSlant = newDividerSlant
        smallCorner = newSmallCorner
        outlineDirty = true
        invalidateSelf()
        return true
    }

    /**
     * Matches the shape [eu.kanade.tachiyomi.util.view.makeShapeCorners] gives the card around
     * this, so the badge's outline and its fill line up.
     *
     * @param uniform rounds every corner by [startRadius], the way a card's own radius would
     */
    fun setCorners(
        startRadius: Float,
        endRadius: Float,
        roundEnd: Boolean,
        isRtl: Boolean,
        uniform: Boolean = false,
    ) {
        if (this.startRadius == startRadius &&
            this.endRadius == endRadius &&
            this.roundEnd == roundEnd &&
            this.uniform == uniform &&
            this.isRtl == isRtl
        ) {
            return
        }
        this.startRadius = startRadius
        this.endRadius = endRadius
        this.roundEnd = roundEnd
        this.uniform = uniform
        this.isRtl = isRtl
        outlineDirty = true
        invalidateSelf()
    }

    /**
     * @param colors what each part is filled with, ordered left to right however the badge is laid out
     * @param starts where each part begins; the first one starts at the badge's own edge
     */
    fun setParts(
        colors: IntArray,
        starts: FloatArray,
        count: Int,
    ) {
        var changed = count != partCount
        for (i in 0 until count) {
            changed = changed || partColors[i] != colors[i] || partStarts[i] != starts[i]
            partColors[i] = colors[i]
            partStarts[i] = starts[i]
        }
        if (!changed) return
        partCount = count
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        outlineDirty = true
    }

    override fun draw(canvas: Canvas) {
        if (partCount == 0 || bounds.isEmpty) return
        if (outlineDirty) {
            buildOutline()
        }
        setPaintColor(partColors[0])
        canvas.drawPath(outlinePath, paint)
        if (partCount == 1) return
        // Later parts are drawn over the ones before them, so each only needs its leading edge
        val saveCount = canvas.save()
        canvas.clipPath(outlinePath)
        for (i in 1 until partCount) {
            setPaintColor(partColors[i])
            buildPart(partStarts[i])
            canvas.drawPath(partPath, paint)
        }
        canvas.restoreToCount(saveCount)
    }

    private fun buildOutline() {
        outlineDirty = false
        rect.set(bounds)
        if (uniform) {
            radii.fill(startRadius)
            outlinePath.rewind()
            outlinePath.addRoundRect(rect, radii, Path.Direction.CW)
            return
        }
        val small = if (startRadius > 0 || endRadius > 0) smallCorner else 0f
        val trailing =
            when {
                roundEnd -> endRadius
                endRadius > 0 -> small
                else -> 0f
            }
        val leadingOpposite = if (startRadius > 0) small else 0f
        // topLeft, topRight, bottomRight, bottomLeft
        val corners =
            if (isRtl) {
                floatArrayOf(trailing, startRadius, leadingOpposite, endRadius)
            } else {
                floatArrayOf(startRadius, trailing, endRadius, leadingOpposite)
            }
        for (i in corners.indices) {
            radii[i * 2] = corners[i]
            radii[i * 2 + 1] = corners[i]
        }
        outlinePath.rewind()
        outlinePath.addRoundRect(rect, radii, Path.Direction.CW)
    }

    /**
     * Repaints the sliver of a divider that laps onto the part before it, for parts that draw their
     * own pixels over this drawable. The flag is the only one, and it would otherwise square off a
     * cut that [buildPart] already made underneath it.
     */
    fun drawDividerOver(
        canvas: Canvas,
        boundary: Float,
        @ColorInt color: Int,
    ) {
        if (bounds.isEmpty) return
        val bounds = bounds
        val slant = if (isRtl) dividerSlant else -dividerSlant
        setPaintColor(color)
        partPath.rewind()
        partPath.moveTo(boundary, bounds.top.toFloat())
        partPath.lineTo(boundary, bounds.bottom.toFloat())
        partPath.lineTo(boundary + slant, bounds.bottom.toFloat())
        partPath.close()
        canvas.drawPath(partPath, paint)
    }

    /** The part from [start] rightwards, its leading edge slanted the way the old overlay was. */
    private fun buildPart(start: Float) {
        val bounds = bounds
        val slant = if (isRtl) dividerSlant else -dividerSlant
        partPath.rewind()
        partPath.moveTo(start, bounds.top.toFloat())
        partPath.lineTo(bounds.right.toFloat(), bounds.top.toFloat())
        partPath.lineTo(bounds.right.toFloat(), bounds.bottom.toFloat())
        partPath.lineTo(start + slant, bounds.bottom.toFloat())
        partPath.close()
    }

    /** Setting a color resets the paint's alpha, so the two are folded together here */
    private fun setPaintColor(
        @ColorInt color: Int,
    ) {
        paint.color = color
        if (alpha != 255) {
            paint.alpha = paint.alpha * alpha / 255
        }
    }

    override fun setAlpha(alpha: Int) {
        if (this.alpha != alpha) {
            this.alpha = alpha
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        const val MAX_PARTS = 3

        /** The old divider was a 10dp image with the slant across its trailing half */
        private const val DIVIDER_SLANT_DP = 5f
        private const val SMALL_CORNER_DP = 4f
    }
}
