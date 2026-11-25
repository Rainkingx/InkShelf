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
    data class Slot(val title: String, val reference: String)
    val slots = listOf(
        Slot("Iron Man — arc reactor", "https://propstoreauction.com/lot-details/index/catalog/489/lot/186352/654-Light-Up-Proof-That-Tony-Stark-Has-a-Heart-Arc-Reactor-Display-Replica-IRON-MAN-2008"),
        Slot("Batman — bat emblem", "https://commons.wikimedia.org/wiki/File:Batman_yellow_oval_logo_with_DC_copyright.svg"),
        Slot("Dredd — 2012 helmet", "https://propstore.com/product/dredd-2012/karl-urban-hero-costume/"),
        Slot("Spider-Man — Raimi emblem", "https://propstore.com/product/spider-man-2002/spider-man-suit-components-and-patterns/"),
    )

    private fun file(context: Context, index: Int): File {
        require(index in slots.indices)
        return File(context.filesDir, "splash_artwork/hero_${index}.png")
    }

    fun load(context: Context, index: Int): Bitmap? = runCatching {
        val source = AtomicFile(file(context, index))
        source.openRead().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    fun has(context: Context, index: Int): Boolean = file(context, index).exists()

    fun reset(context: Context, index: Int) {
        AtomicFile(file(context, index)).delete()
    }

    /** Decode bounds first, downsample large photos and preserve transparent artwork. */
    fun importImage(context: Context, index: Int, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unreadable image")
        val size = 768
        val normalized = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val ratio = minOf(size.toFloat() / decoded.width, size.toFloat() / decoded.height)
            val w = decoded.width * ratio
            val h = decoded.height * ratio
            Canvas(normalized).drawBitmap(decoded, null,
                RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f),
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
        true
    }.getOrDefault(false)
}
