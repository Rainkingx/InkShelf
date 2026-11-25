package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import kotlin.math.max

/** Locally chosen splash images are private to this installation, never uploaded. */
internal object InkShelf2SplashArtwork {
    val slots = List(4) { index -> "Image ${index + 1}" }
    const val DOOM_RUNE_INDEX = 4
    const val LOGO_INDEX = 5
    val allSlots = slots + listOf("Doom rune (HD)", "Final InkShelf logo (HD)")

    private fun file(context: Context, index: Int): File {
        require(index in allSlots.indices)
        val name = when (index) {
            DOOM_RUNE_INDEX -> "doom_rune.png"
            LOGO_INDEX -> "final_logo.png"
            else -> "hero_${index}.png"
        }
        return File(context.filesDir, "splash_artwork/$name")
    }

    private val bundledNames = mapOf(
        0 to "01_iron_man.webp",
        1 to "02_superman.webp",
        2 to "03_spider_man.webp",
        3 to "04_flash.webp",
        LOGO_INDEX to "05_inkshelf_logo.webp",
    )

    private fun assetPath(index: Int): String? =
        bundledNames[index]?.let { "inkshelf/splash/$it" }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("inkshelf_splash_artwork", Context.MODE_PRIVATE)

    fun hasBundled(context: Context, index: Int): Boolean =
        assetPath(index)?.let { path -> runCatching {
            context.assets.open(path).use { true }
        }.getOrDefault(false) } ?: false

    // Upgrading to the built-in HD pack must not leave old blurry overrides active.
    // Keep those files intact; users can explicitly choose their saved artwork again.
    fun usesCustom(context: Context, index: Int): Boolean =
        has(context, index) && preferences(context)
            .getBoolean("custom_$index", !hasBundled(context, index))

    fun selectCustom(context: Context, index: Int, custom: Boolean) {
        preferences(context).edit().putBoolean("custom_$index", custom).apply()
    }

    private fun loadCustom(context: Context, index: Int): Bitmap? = runCatching {
        AtomicFile(file(context, index)).openRead().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    fun load(context: Context, index: Int): Bitmap? {
        if (usesCustom(context, index)) loadCustom(context, index)?.let { return it }
        return assetPath(index)?.let { path -> runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        }.getOrNull() }
    }

    fun has(context: Context, index: Int): Boolean = file(context, index).exists()

    fun previewFile(context: Context, index: Int): File? =
        file(context, index).takeIf { usesCustom(context, index) && it.isFile }

    fun bundledPreview(context: Context, index: Int): String? =
        assetPath(index)?.takeIf { hasBundled(context, index) }?.let { "file:///android_asset/$it" }

    fun dimensions(context: Context, index: Int): String? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (usesCustom(context, index)) {
            AtomicFile(file(context, index)).openRead().use { BitmapFactory.decodeStream(it, null, options) }
        } else {
            val path = assetPath(index) ?: return@runCatching null
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
        }
        if (options.outWidth > 0 && options.outHeight > 0)
            "${options.outWidth} × ${options.outHeight} px" else null
    }.getOrNull()

    fun reset(context: Context, index: Int) {
        AtomicFile(file(context, index)).delete()
        preferences(context).edit().remove("custom_$index").apply()
    }

    /** Decode bounds first, downsample large photos and preserve transparent artwork. */
    fun importImage(context: Context, index: Int, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        // Keep full-resolution logo artwork for modern high-density displays.
        // Sample only while the next step still retains the target resolution.
        val limit = if (index == LOGO_INDEX) 3072 else 1600
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= limit) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unreadable image")
        // Keep the aspect ratio and never enlarge smaller source images.
        val ratio = minOf(1f, limit.toFloat() / max(decoded.width, decoded.height))
        val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
        val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
        val normalized = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            Canvas(normalized).drawBitmap(decoded, null, RectF(0f, 0f, w.toFloat(), h.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val destination = file(context, index)
            check(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            val atomic = AtomicFile(destination)
            val output = atomic.startWrite()
            try {
                check(normalized.compress(Bitmap.CompressFormat.PNG, 100, output))
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        } finally {
            decoded.recycle()
            normalized.recycle()
        }
        selectCustom(context, index, true)
        true
    }.getOrDefault(false)
}
