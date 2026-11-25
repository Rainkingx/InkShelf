package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.PathParser
import eu.kanade.tachiyomi.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Original comic-inspired emblems, rendered once as layered metallic art.
 * Startup uses only local resources; no network or reader dependencies.
 */
internal class InkShelf2CinematicSplash(
    context: Context,
    private val ready: () -> Boolean,
    private val startedAt: Long,
) : View(context) {
    companion object {
        private const val PREFS = "inkshelf_cinematic_startup"
        private const val ENABLED = "enabled"
        private const val DURATION = "duration_seconds"
        const val MIN_DURATION_SECONDS = 3
        const val MAX_DURATION_SECONDS = 7
        private const val DEFAULT_DURATION_SECONDS = 5
        private const val BEAT = 510L
        private const val REVEAL = 2040L
        private const val FINISH = 3000L
        private var playedInProcess = false

        fun enabled(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, true)

        fun setEnabled(context: Context, value: Boolean) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED, value).apply()
        }

        fun durationSeconds(context: Context): Int =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(DURATION, DEFAULT_DURATION_SECONDS)
                .coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)

        fun setDurationSeconds(context: Context, seconds: Int) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(DURATION, seconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)).apply()
        }

        fun claimColdStart(): Boolean {
            if (playedInProcess) return false
            playedInProcess = true
            return true
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logo = InkShelf2SplashArtwork.load(context, InkShelf2SplashArtwork.LOGO_INDEX)
        ?: BitmapFactory.decodeResource(resources, R.drawable.inkshelf_cinematic_logo)
    private val colors = intArrayOf(0xFF6CE1FF.toInt(), 0xFFFFC35D.toInt(),
        0xFFE76E62.toInt(), 0xFFF44465.toInt(), 0xFFBD73FF.toInt())
    // Theme restoration happens before this View is created in InkShelf2Activity.
    private val doomRune = if (InkShelf2Themes.selectedId == InkShelf2Themes.DOOM_ID)
        InkShelf2SplashArtwork.load(context, InkShelf2SplashArtwork.DOOM_RUNE_INDEX)
            ?: BitmapFactory.decodeResource(resources, R.drawable.inkshelf_doom_rune)
    else null
    private val doomLogoFilter = ColorMatrixColorFilter(floatArrayOf(
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0.35f, 0.65f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
    private val emblems by lazy {
        Array(4) { index -> InkShelf2SplashArtwork.load(context, index) ?: renderEmblem(index) }
    }
    private val durationMillis = durationSeconds(context) * 1000L
    private var firstFrame = 0L
    private var finished = false
    private var fadeStarted = 0L

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "InkShelf opening animation. Tap to skip."
        setBackgroundColor(if (doomRune != null) Color.rgb(3, 10, 7) else Color.rgb(5, 4, 9))
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

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun tint(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (finished) return
        val now = SystemClock.elapsedRealtime()
        if (firstFrame == 0L) firstFrame = now
        val realElapsed = (now - firstFrame).coerceAtLeast(0L)
        // Keep every emblem, transition and the logo reveal in the same
        // proportions at each speed. Readiness timeout stays in real time.
        val elapsed = min(realElapsed, durationMillis) * FINISH / durationMillis
        if (realElapsed >= durationMillis && (ready() || now - startedAt >= 8000L)) {
            if (fadeStarted == 0L) fadeStarted = now
            val fade = ((now - fadeStarted) / 180f).coerceIn(0f, 1f)
            alpha = 1f - fade
            if (fade >= 1f) {
                post { dismiss() }
                return
            }
        }

        doomRune?.let { rune ->
            drawDoom(canvas, rune, elapsed)
            scheduleFrame(elapsed)
            return
        }

        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height) * 0.42f
        val step = (elapsed / BEAT).toInt().coerceAtMost(4)
        val color = colors[step]
        val ambient = if (step < 4) 40 else 64
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, (size * 1.75f).coerceAtLeast(1f),
            intArrayOf(tint(color, ambient), tint(color, 11), Color.TRANSPARENT),
            floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Deterministic, slow-moving sparks instead of full-screen flashes.
        val particleTime = min(elapsed, FINISH).toFloat() / 1000f
        for (i in 0 until 34) {
            val angle = i * 2.399963 + particleTime * 0.14
            val distance = size * (0.62f + ((i * 37) % 100) / 100f * 1.30f)
            val x = cx + cos(angle).toFloat() * distance
            val y = cy + sin(angle).toFloat() * distance * 0.86f
            paint.color = tint(color, 22 + (i * 19) % 98)
            canvas.drawCircle(x, y, (0.7f + i % 3) * resources.displayMetrics.density, paint)
        }

        if (step < 4) {
            val fraction = (elapsed % BEAT) / BEAT.toFloat()
            val blend = ease(fraction / 0.28f)
            if (step > 0) drawUpright(canvas, emblems[step - 1], cx, cy, size * 1.03f, 1f - blend)
            drawUpright(canvas, emblems[step], cx, cy, size * (0.96f + 0.07f * ease(fraction)), blend)
        } else {
            val outgoing = 1f - ease((elapsed - REVEAL) / 360f)
            if (outgoing > 0f) drawUpright(canvas, emblems[3], cx, cy, size * 1.03f, outgoing)
            val reveal = ease((elapsed - REVEAL) / 410f)
            val w = min(width * 0.99f, height * 0.86f * logo.width / logo.height) * (0.96f + 0.04f * reveal)
            val h = w * logo.height / logo.width
            val bounds = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            paint.color = Color.WHITE
            paint.alpha = (255 * reveal).toInt()
            canvas.drawBitmap(logo, null, bounds, paint)
            paint.alpha = 255
            val sweep = ((elapsed - REVEAL - 270L) / 540f).coerceIn(0f, 1f)
            if (sweep > 0f && sweep < 1f) {
                val x = bounds.left - w * 0.25f + w * 1.5f * sweep
                canvas.save()
                canvas.clipRect(bounds)
                paint.shader = LinearGradient(x - w * 0.09f, cy, x + w * 0.09f, cy,
                    intArrayOf(Color.TRANSPARENT, tint(0xFFD8B5FF.toInt(), 32), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, paint)
                paint.shader = null
                canvas.restore()
            }
        }
        scheduleFrame(elapsed)
    }

    private fun drawUpright(canvas: Canvas, image: Bitmap, cx: Float, cy: Float, half: Float, opacity: Float) {
        if (opacity <= 0f) return
        val ratio = min(half * 2f / image.width, half * 2f / image.height)
        val w = image.width * ratio
        val h = image.height * ratio
        paint.color = Color.WHITE
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(image, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), paint)
        paint.alpha = 255
    }

    private fun scheduleFrame(elapsed: Long) {
        // Hold the final artwork, without repeating the sequence, while Home loads.
        if (elapsed < FINISH || fadeStarted != 0L) postInvalidateOnAnimation()
        else postInvalidateDelayed(60L)
    }

    private fun drawDoom(canvas: Canvas, rune: Bitmap, elapsed: Long) {
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val green = 0xFF36E88A.toInt()
        val phase = elapsed.toFloat() / FINISH
        val appear = ease(elapsed / 620f)
        val transition = ease((elapsed - 1680L) / 650f)
        val pulse = (0.5 + 0.5 * sin(phase * 2.0 * PI)).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = 255
        paint.shader = RadialGradient(cx, cy, size * 0.88f,
            intArrayOf(tint(green, (26 + 30 * pulse).toInt()), tint(green, 9), Color.TRANSPARENT),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Sparse emerald motes drift around the rune and its final reveal.
        for (i in 0 until 24) {
            val angle = i * 2.399963 + phase * 0.55
            val distance = size * (0.30f + ((i * 37) % 100) / 100f * 0.46f)
            paint.color = tint(green, (appear * (20 + (i * 13) % 75)).toInt())
            canvas.drawCircle(cx + cos(angle).toFloat() * distance,
                cy + sin(angle).toFloat() * distance * 0.95f,
                (0.5f + i % 2) * resources.displayMetrics.density, paint)
        }

        if (transition < 1f) {
            // Keep the rune upright: gentle push-in followed by a dissolve.
            val half = size * 0.47f * (0.95f + 0.05f * appear + 0.025f * transition)
            drawUpright(canvas, rune, cx, cy, half, appear * (1f - transition))
        }

        if (transition > 0f) {
            val w = min(width * 0.97f, height * 0.86f * logo.width / logo.height) * (0.96f + 0.04f * transition)
            val h = w * logo.height / logo.width
            paint.color = Color.WHITE
            paint.alpha = (255 * transition).toInt()
            // Preserve neutral steel and recolour the purple lighting emerald.
            paint.colorFilter = doomLogoFilter
            canvas.drawBitmap(logo, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paint)
            paint.colorFilter = null
            paint.alpha = 255
        }
    }

    private fun renderEmblem(index: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(512f, 512f)
        canvas.scale(4.64f, 4.64f)
        when (index) {
            0 -> reactor(canvas)
            1 -> bat(canvas)
            2 -> helmet(canvas)
            3 -> spider(canvas)
        }
        return bitmap
    }

    private fun shape(data: String): Path = requireNotNull(PathParser.createPathFromPathData(data))

    private fun solid(canvas: Canvas, path: Path, color: Int) {
        art.reset()
        art.isAntiAlias = true
        art.color = color
        canvas.drawPath(path, art)
    }

    private fun metal(canvas: Canvas, path: Path, top: Int, middle: Int, bottom: Int, edge: Int) {
        art.reset()
        art.isAntiAlias = true
        // Offset extrusion and a bevel around the reflective face.
        canvas.save()
        canvas.translate(0f, 4f)
        art.color = 0xFF08090F.toInt()
        art.style = Paint.Style.FILL_AND_STROKE
        art.strokeWidth = 3.5f
        canvas.drawPath(path, art)
        canvas.restore()
        art.style = Paint.Style.FILL
        art.shader = LinearGradient(-55f, -85f, 48f, 85f,
            intArrayOf(top, middle, top, bottom, middle, bottom),
            floatArrayOf(0f, 0.29f, 0.43f, 0.56f, 0.76f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, art)
        art.shader = null
        art.style = Paint.Style.STROKE
        art.strokeWidth = 1.35f
        art.strokeJoin = Paint.Join.ROUND
        art.color = edge
        canvas.drawPath(path, art)
        // Fine directional brushed lines clipped to the metal face.
        canvas.save()
        canvas.clipPath(path)
        art.strokeWidth = 0.22f
        for (i in -90..90 step 3) {
            art.color = if (i % 2 == 0) 0x16FFFFFF else 0x18000000
            canvas.drawLine(-105f, i.toFloat(), 105f, i - 16f, art)
        }
        canvas.restore()
        art.style = Paint.Style.FILL
    }

    private fun ring(canvas: Canvas, radius: Float, width: Float, color: Int) {
        art.reset()
        art.isAntiAlias = true
        art.color = color
        art.style = Paint.Style.STROKE
        art.strokeWidth = width
        canvas.drawCircle(0f, 0f, radius, art)
        art.style = Paint.Style.FILL
    }

    private fun disk(canvas: Canvas, radius: Float, colors: IntArray) {
        art.reset()
        art.isAntiAlias = true
        art.shader = RadialGradient(-radius * 0.20f, -radius * 0.22f, radius * 1.45f,
            colors, null, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, radius, art)
        art.shader = null
    }

    private fun reactor(canvas: Canvas) {
        disk(canvas, 88f, intArrayOf(0xFFA7D9E6.toInt(), 0xFF32465A.toInt(), 0xFF101924.toInt()))
        ring(canvas, 85f, 2.5f, 0xFFB9F4FF.toInt())
        ring(canvas, 76f, 5f, 0xFF091520.toInt())
        disk(canvas, 65f, intArrayOf(0xFF567E94.toInt(), 0xFF172734.toInt(), 0xFF0A111D.toInt()))
        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f)
            val segment = shape("M-10,-73 L10,-73 L15,-52 L-15,-52 Z")
            metal(canvas, segment, 0xFFE0F6FF.toInt(), 0xFF627C93.toInt(),
                0xFF203144.toInt(), 0xFF86D6ED.toInt())
            solid(canvas, shape("M-5,-68 L5,-68 L7,-56 L-7,-56 Z"), 0xFF8AEAFF.toInt())
            canvas.restore()
        }
        ring(canvas, 48f, 5f, 0xFF46BBEA.toInt())
        ring(canvas, 41f, 1.5f, 0xFFD1FAFF.toInt())
        disk(canvas, 37f, intArrayOf(0xFFF4FDFF.toInt(), 0xFF8BEAFF.toInt(), 0xFF1678B6.toInt()))
        val triangle = shape("M0,-31 L29,20 Q30,24 25,24 L-25,24 Q-30,24 -29,20 Z")
        metal(canvas, triangle, 0xFFF6FFFF.toInt(), 0xFF91E6FF.toInt(), 0xFF3F89A9.toInt(), Color.WHITE)
        solid(canvas, shape("M0,-18 L16,12 L-16,12 Z"), 0xFFF5FFFF.toInt())
    }

    private fun bat(canvas: Canvas) {
        val body = shape("M-100,-36 Q-70,-33 -39,-49 Q-37,-19 -15,-14 L-11,-42 L0,-30 L11,-42 L15,-14 Q37,-19 39,-49 Q70,-33 100,-36 Q83,-9 76,34 Q54,11 37,38 Q19,19 0,56 Q-19,19 -37,38 Q-54,11 -76,34 Q-83,-9 -100,-36 Z")
        metal(canvas, body, 0xFFD5D9DF.toInt(), 0xFF343944.toInt(),
            0xFF0C1018.toInt(), 0xFFC69A56.toInt())
        val inner = shape("M-81,-27 Q-56,-28 -43,-36 Q-33,-13 -17,-8 L-10,-18 L0,-10 L10,-18 L17,-8 Q33,-13 43,-36 Q56,-28 81,-27 Q71,-8 66,13 Q49,4 35,24 Q17,15 0,37 Q-17,15 -35,24 Q-49,4 -66,13 Q-71,-8 -81,-27 Z")
        metal(canvas, inner, 0xFF555F6B.toInt(), 0xFF1B222D.toInt(), 0xFF090C13.toInt(), 0xFF717781.toInt())
    }

    private fun helmet(canvas: Canvas) {
        val outer = shape("M-75,-25 Q-73,-70 -45,-84 Q0,-99 45,-84 Q73,-70 75,-25 L70,30 Q60,52 39,70 L23,82 L-23,82 L-39,70 Q-60,52 -70,30 Z")
        metal(canvas, outer, 0xFFCCD5E0.toInt(), 0xFF4B586B.toInt(), 0xFF111722.toInt(), 0xFFD7DBE4.toInt())
        val brow = shape("M-67,-30 Q-62,-66 -35,-75 L-16,-66 L16,-66 L35,-75 Q62,-66 67,-30 L47,-14 L0,-28 L-47,-14 Z")
        metal(canvas, brow, 0xFFFFD0AC.toInt(), 0xFFB73E39.toInt(), 0xFF45151E.toInt(), 0xFFE98D75.toInt())
        val visor = shape("M-65,-23 Q-39,-35 0,-24 Q39,-35 65,-23 L54,10 L28,25 L0,2 L-28,25 L-54,10 Z")
        solid(canvas, visor, 0xFF050910.toInt())
        metal(canvas, shape("M-57,-20 L-25,-27 L0,-17 L25,-27 L57,-20 L48,-15 L25,-19 L0,-8 L-25,-19 L-48,-15 Z"),
            0xFFBDC4CE.toInt(), 0xFF3A4352.toInt(), 0xFF141C28.toInt(), 0xFF929AA5.toInt())
        val jaw = shape("M-45,18 L-24,34 L0,22 L24,34 L45,18 L35,56 L19,72 L-19,72 L-35,56 Z")
        metal(canvas, jaw, 0xFFE4E8EE.toInt(), 0xFF86929F.toInt(), 0xFF303C4A.toInt(), 0xFFADB9C5.toInt())
        solid(canvas, shape("M-21,45 L-10,42 L0,45 L10,42 L21,45 L12,50 L-12,50 Z"), 0xFF26303C.toInt())
        val crest = shape("M0,-83 L8,-70 L24,-73 L19,-60 L32,-51 L15,-48 L11,-33 L0,-42 L-11,-33 L-15,-48 L-32,-51 L-19,-60 L-24,-73 L-8,-70 Z")
        metal(canvas, crest, 0xFFFFEAC0.toInt(), 0xFFC19A47.toInt(), 0xFF674017.toInt(), 0xFFFFD783.toInt())
    }

    private fun spider(canvas: Canvas) {
        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.scale(side.toFloat(), 1f)
            val legs = arrayOf(
                "M8,-26 L31,-54 L56,-82 L63,-71 L42,-42 L19,-12 Z",
                "M13,-13 L49,-39 L81,-53 L85,-42 L58,-27 L23,3 Z",
                "M16,3 L56,-1 L90,17 L85,29 L54,13 L22,18 Z",
                "M13,20 L47,39 L65,79 L54,84 L37,51 L12,37 Z"
            )
            for (leg in legs) metal(canvas, shape(leg), 0xFFFFB0A9.toInt(), 0xFFC43652.toInt(),
                0xFF4A1226.toInt(), 0xFFF48289.toInt())
            canvas.restore()
        }
        metal(canvas, shape("M0,-49 Q21,-45 18,-24 L11,-9 Q35,12 22,40 L0,65 L-22,40 Q-35,12 -11,-9 L-18,-24 Q-21,-45 0,-49 Z"),
            0xFFFFCDC0.toInt(), 0xFFE04B64.toInt(), 0xFF510E2B.toInt(), 0xFFF6979D.toInt())
        metal(canvas, shape("M0,-34 L8,-20 L0,-7 L-8,-20 Z M0,6 L13,29 L0,48 L-13,29 Z"),
            0xFFFFD9D2.toInt(), 0xFFAD213F.toInt(), 0xFF310F1F.toInt(), 0xFFF9A5AA.toInt())
    }
}
