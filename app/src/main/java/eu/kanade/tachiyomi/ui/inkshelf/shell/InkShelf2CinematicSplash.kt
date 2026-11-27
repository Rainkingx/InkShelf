package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import kotlin.math.min
import kotlin.math.sin

/** One ARC opening, animated at the display refresh rate. No GIF or network. */
internal class InkShelf2CinematicSplash(
    context: Context,
    private val ready: () -> Boolean,
    private val startedAt: Long,
) : View(context) {
    companion object {
        private const val PREFS = "inkshelf_cinematic_startup"
        private const val ENABLED = "enabled"
        private const val DURATION = 2600L
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
    private val backdrop = 0xFF08040E.toInt()
    private val violet = 0xFFB740FF.toInt()
    private val emblem = ContextCompat.getDrawable(context, R.drawable.arc_emblem)?.mutate()
    private val bold = ResourcesCompat.getFont(context, R.font.oxanium_extrabold)
    private val regular = ResourcesCompat.getFont(context, R.font.oxanium_regular)
    // Full-resolution approved artwork can be bundled at this path. A locally
    // imported ARC image overrides it; previous InkShelf slots stay untouched.
    private val artwork = InkShelf2SplashArtwork.load(context, InkShelf2SplashArtwork.ARC_INDEX)
    private var firstFrame = 0L
    private var fadeStarted = 0L
    private var finished = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "ARC opening animation. Tap to skip."
        setBackgroundColor(backdrop)
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

    private fun tint(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (finished || width == 0 || height == 0) return
        val now = SystemClock.elapsedRealtime()
        if (firstFrame == 0L) firstFrame = now
        val elapsed = (now - firstFrame).coerceAtLeast(0L)
        val phase = (elapsed / DURATION.toFloat()).coerceIn(0f, 1f)
        val pulse = sin(phase * Math.PI).toFloat()
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val scale = min(w, h * 0.65f)
        canvas.drawColor(backdrop)
        artwork?.let { image ->
            // Fit instead of cropping: preserve ARC and the full tagline on tablets.
            val ratio = min(w / image.width, h / image.height)
            val iw = image.width * ratio
            val ih = image.height * ratio
            paint.color = Color.WHITE
            paint.alpha = 255
            canvas.drawBitmap(image, null, RectF(cx-iw/2, cy-ih/2, cx+iw/2, cy+ih/2), paint)
        } ?: drawNativeArtwork(canvas, w, h, cx, cy, scale, pulse)

        // Restrained glow and a continuously moving highlight, not a frame sequence.
        paint.color = Color.WHITE
        paint.shader = RadialGradient(cx, cy - scale * 0.16f, scale * 0.61f,
            intArrayOf(tint(violet, (12 * pulse).toInt()), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        if (phase in 0.30f..0.78f) {
            val x = cx - scale * 0.65f + scale * 1.30f * ((phase - 0.30f) / 0.48f)
            paint.shader = LinearGradient(x - scale * 0.07f, 0f, x + scale * 0.07f, 0f,
                intArrayOf(Color.TRANSPARENT, tint(0xFFF0C9FF.toInt(), 18), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(cx-scale*0.37f, cy-scale*0.10f, cx+scale*0.37f, cy+scale*0.18f, paint)
            paint.shader = null
        }

        if (elapsed >= DURATION && (ready() || now - startedAt >= 8000L)) {
            if (fadeStarted == 0L) fadeStarted = now
            val fade = ((now - fadeStarted) / 200f).coerceIn(0f, 1f)
            alpha = 1f - fade
            if (fade >= 1f) {
                post { dismiss() }
                return
            }
        }
        if (elapsed < DURATION || fadeStarted != 0L) postInvalidateOnAnimation()
        else postInvalidateDelayed(60L)
    }

    private fun drawNativeArtwork(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, scale: Float, pulse: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = RadialGradient(cx, cy-scale*0.15f, scale*0.84f,
            intArrayOf(tint(violet, (48+24*pulse).toInt()), backdrop, backdrop),
            floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.006f
        paint.color = tint(violet, 190)
        canvas.drawArc(RectF(-w*0.30f, -h*0.25f, w*1.40f, h*0.38f), 180f, 140f, false, paint)
        canvas.drawArc(RectF(-w*0.40f, h*0.64f, w*1.30f, h*1.25f), 0f, 170f, false, paint)
        paint.style = Paint.Style.FILL
        val half = scale*0.31f
        val iconY = cy-scale*0.31f
        emblem?.apply {
            setBounds((cx-half).toInt(), (iconY-half).toInt(), (cx+half).toInt(), (iconY+half).toInt())
            draw(canvas)
        }
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = bold
        paint.textSize = scale*0.27f
        paint.shader = LinearGradient(0f, cy-scale*0.08f, 0f, cy+scale*0.17f,
            intArrayOf(Color.WHITE, 0xFFCC9BFF.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawText("ARC", cx, cy+scale*0.17f, paint)
        paint.shader = null
        paint.color = 0xFFE7DDF4.toInt()
        paint.typeface = regular
        paint.textSize = scale*0.051f
        canvas.drawText("Archive. Read. Collect.", cx, cy+scale*0.26f, paint)
        paint.shader = LinearGradient(cx-scale*0.28f, 0f, cx+scale*0.28f, 0f,
            intArrayOf(Color.TRANSPARENT, violet, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(cx-scale*0.28f, cy+scale*0.30f, cx+scale*0.28f, cy+scale*0.304f, paint)
        paint.shader = null
    }
}
