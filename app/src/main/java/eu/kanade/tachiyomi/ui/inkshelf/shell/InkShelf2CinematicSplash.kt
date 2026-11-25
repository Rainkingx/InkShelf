package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Decorative cold-start sequence, independent of the reader and shell data. */
internal class InkShelf2CinematicSplash(
    context: Context,
    private val ready: () -> Boolean,
    private val startedAt: Long,
) : View(context) {
    companion object {
        private const val PREFS = "inkshelf_cinematic_startup"
        private const val ENABLED = "enabled"
        private var playedInProcess = false

        fun enabled(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, true)

        fun setEnabled(context: Context, value: Boolean) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED, value).apply()
        }

        fun claimColdStart(): Boolean {
            if (playedInProcess) return false
            playedInProcess = true
            return true
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val logo = BitmapFactory.decodeResource(resources, R.drawable.inkshelf_cinematic_logo)
    private var firstFrame = 0L
    private var finished = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "InkShelf opening animation. Tap to skip."
        setBackgroundColor(Color.rgb(5, 4, 9))
    }

    override fun performClick(): Boolean {
        super.performClick()
        dismiss()
        return true
    }

    fun dismiss() {
        if (finished) return
        finished = true
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (finished) return
        val now = SystemClock.elapsedRealtime()
        if (firstFrame == 0L) firstFrame = now
        val elapsed = now - firstFrame
        val complete = elapsed >= 2800L && (ready() || now - startedAt >= 8000L)
        if (complete) {
            post { dismiss() }
            return
        }
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height) * 0.30f
        val step = (elapsed / 430L).toInt()
        if (step < 5) {
            val fraction = (elapsed % 430L) / 430f
            val color = intArrayOf(
                0xFF66DCFF.toInt(), 0xFFFFC749.toInt(), 0xFFEF5656.toInt(),
                0xFF4B9DFF.toInt(), 0xFFB72AF2.toInt(),
            )[step]
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(cx, cy, size * 1.65f,
                intArrayOf(withAlpha(color, 60), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, size * 1.65f, paint)
            paint.shader = null
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(size / 100f, size / 100f)
            // Flip each silhouette edge-on, then reveal the next.
            canvas.scale(abs(cos(PI * fraction)).toFloat().coerceAtLeast(0.045f), 1f)
            paint.color = color
            paint.strokeWidth = 3.5f
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(0f, 0f, 91f, paint)
            canvas.drawArc(RectF(-98f, -98f, 98f, 98f), fraction * 360f, 92f, false, paint)
            when (step) {
                0 -> reactor(canvas, color)
                1 -> bat(canvas, color)
                2 -> helmet(canvas, color)
                3 -> spider(canvas, color)
                else -> shield(canvas, color)
            }
            canvas.restore()
        } else {
            val progress = ((elapsed - 2150L) / 380f).coerceIn(0f, 1f)
            val w = min(width * 0.96f, height * 1.30f) * (0.90f + 0.10f * progress)
            val h = w * logo.height / logo.width
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = (255 * progress).toInt()
            canvas.drawBitmap(logo, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paint)
            paint.alpha = 255
        }
        postInvalidateOnAnimation()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun path(canvas: Canvas, color: Int, vararg points: Float) {
        val shape = Path().apply {
            moveTo(points[0], points[1])
            var i = 2
            while (i < points.size) {
                lineTo(points[i], points[i + 1])
                i += 2
            }
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(shape, paint)
    }

    private fun reactor(canvas: Canvas, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        canvas.drawCircle(0f, 0f, 57f, paint)
        canvas.drawCircle(0f, 0f, 34f, paint)
        for (i in 0 until 8) {
            val a = i * PI / 4
            canvas.drawLine((cos(a) * 61).toFloat(), (sin(a) * 61).toFloat(),
                (cos(a) * 73).toFloat(), (sin(a) * 73).toFloat(), paint)
        }
        path(canvas, Color.WHITE, 0f, -26f, 25f, 17f, -25f, 17f)
    }

    private fun bat(canvas: Canvas, color: Int) {
        path(canvas, color, -78f, -22f, -54f, -14f, -30f, -38f, -18f, -12f,
            -10f, -20f, -7f, -38f, 0f, -27f, 7f, -38f, 10f, -20f,
            18f, -12f, 30f, -38f, 54f, -14f, 78f, -22f, 58f, 30f,
            37f, 15f, 16f, 40f, 0f, 24f, -16f, 40f, -37f, 15f, -58f, 30f)
    }

    private fun helmet(canvas: Canvas, color: Int) {
        path(canvas, color, -56f, -31f, -36f, -63f, 36f, -63f, 56f, -31f,
            51f, 37f, 27f, 51f, -27f, 51f, -51f, 37f)
        path(canvas, 0xFF12131B.toInt(), -47f, -25f, 47f, -25f, 33f, 6f,
            15f, 12f, 0f, -3f, -15f, 12f, -33f, 6f)
        path(canvas, 0xFFC4C9D0.toInt(), -27f, 23f, 27f, 23f, 17f, 46f, -17f, 46f)
        path(canvas, 0xFFFFCA54.toInt(), 0f, -55f, 7f, -41f, 22f, -39f,
            11f, -29f, 13f, -15f, 0f, -22f, -13f, -15f, -11f, -29f, -22f, -39f, -7f, -41f)
    }

    private fun spider(canvas: Canvas, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawOval(RectF(-12f, -33f, 12f, -6f), paint)
        canvas.drawOval(RectF(-19f, -8f, 19f, 39f), paint)
        paint.strokeWidth = 6f
        paint.strokeCap = Paint.Cap.ROUND
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until 4) {
                val y = -19f + 13f * i
                val outer = -63f + 42f * i
                canvas.drawLine(side * 11f, y, side * (38f + i * 5f), outer * 0.45f, paint)
                canvas.drawLine(side * (38f + i * 5f), outer * 0.45f, side * 63f, outer, paint)
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun shield(canvas: Canvas, color: Int) {
        path(canvas, color, -63f, -40f, 0f, -65f, 63f, -40f, 54f, 20f, 0f, 62f, -54f, 20f)
        path(canvas, 0xFF181025.toInt(), -45f, -29f, 0f, -47f, 45f, -29f, 37f, 13f, 0f, 42f, -37f, 13f)
        path(canvas, Color.WHITE, -28f, -18f, 0f, -7f, 28f, -18f, 28f, 16f,
            0f, 29f, -28f, 16f)
        paint.color = 0xFF181025.toInt()
        paint.strokeWidth = 3f
        canvas.drawLine(0f, -7f, 0f, 29f, paint)
    }
}
