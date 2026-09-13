package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import com.google.android.material.card.MaterialCardView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.UnreadDownloadBadgeBinding
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.view.makeShapeCorners

/**
 * Colors used while binding library items. Every attr lookup goes through a new TypedArray, so
 * adapters hold one of these for all of their holders instead of resolving them on each bind.
 */
class LibraryColors(
    context: Context,
) {
    val unread = context.getResourceColor(R.attr.colorPrimary)
    val onUnread = context.getResourceColor(R.attr.colorOnPrimary)
    val download = context.getResourceColor(R.attr.colorTertiary)
    val onDownload = context.getResourceColor(R.attr.colorOnTertiary)
    val duplicate = context.getResourceColor(R.attr.colorSecondary)
    val background = context.getResourceColor(R.attr.background)
    val onBackground = context.getResourceColor(R.attr.colorOnBackground)
    val totalChapters = context.contextCompatColor(R.color.total_badge)
    val onTotalChapters = context.contextCompatColor(R.color.total_badge_text)
    val searchHighlight = ColorUtils.setAlphaComponent(unread, 75)
}

class LibraryBadge
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : MaterialCardView(context, attrs) {
        private lateinit var binding: UnreadDownloadBadgeBinding
        val ogRadius = radius
        val roundedRadius = 8.7f.spToPx

        /** Paints every part of the badge, see [LibraryBadgeDrawable]. */
        private val badgeDrawable = LibraryBadgeDrawable()
        private val partColors = IntArray(LibraryBadgeDrawable.MAX_PARTS)
        private val partStarts = FloatArray(LibraryBadgeDrawable.MAX_PARTS)

        /** Set by adapters that already have one, otherwise resolved on first use. */
        var libraryColors: LibraryColors? = null
        private val colors: LibraryColors
            get() = libraryColors ?: LibraryColors(context).also { libraryColors = it }

        /** State of the last [setUnreadDownload], to skip rebuilding an identical badge. */
        private var hasAppliedState = false
        private var lastUnread = 0
        private var lastDownloads = 0
        private var lastShowTotalChapters = false
        private var lastLang: String? = null
        private var lastChangeShape = false

        /** Which parts were showing the last time [applyShape] ran, -1 when it never has. */
        private var lastShapeKey = -1
        private var lastFlagRes = 0

        /** Fill behind the unread count, which doubles as the in library/duplicate label. */
        private var unreadFill = 0

        /** Where the flag meets its neighbour, and that neighbour's fill; 0 when there's no flag. */
        private var flagDividerX = 0f
        private var flagDividerColor = 0

        private val isRtl: Boolean
            get() = layoutDirection == LAYOUT_DIRECTION_RTL

        companion object {
            // resources.getIdentifier() is a slow string-based lookup; the lang->drawable mapping
            // never changes at runtime, so cache it once instead of resolving it on every bind.
            private val flagResCache = HashMap<String, Int>()
        }

        override fun onFinishInflate() {
            super.onFinishInflate()
            binding = UnreadDownloadBadgeBinding.bind(this)
            binding.badgeContent.background = badgeDrawable
            badgeDrawable.setDensity(resources.displayMetrics.density)
            shapeAppearanceModel = makeShapeCorners(ogRadius, ogRadius)
            badgeDrawable.setCorners(ogRadius, ogRadius, roundEnd = false, isRtl = isRtl, uniform = true)
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)
            // Where one part ends and the next begins is only known once they've been laid out
            updateParts()
        }

        override fun setVisibility(visibility: Int) {
            super.setVisibility(visibility)
            // Anyone hiding/showing the badge directly invalidates the state cached below
            hasAppliedState = false
        }

        fun setUnreadDownload(
            unread: Int,
            downloads: Int,
            showTotalChapters: Boolean,
            lang: String?,
            changeShape: Boolean,
        ) {
            if (hasAppliedState &&
                unread == lastUnread &&
                downloads == lastDownloads &&
                showTotalChapters == lastShowTotalChapters &&
                lang == lastLang &&
                changeShape == lastChangeShape
            ) {
                return
            }

            // Update the unread count and its visibility.

            val colors = colors
            unreadFill =
                if (showTotalChapters) {
                    colors.totalChapters
                } else {
                    colors.unread
                }

            with(binding.unreadText) {
                isVisible = unread > 0 || unread == -1 || showTotalChapters
                if (!isVisible) {
                    return@with
                }
                text = if (unread == -1) "." else unread.toString()
                setTextColor(
                    // hide the badge text when preference is only show badge
                    when {
                        unread == -1 && !showTotalChapters -> unreadFill
                        showTotalChapters -> colors.onTotalChapters
                        else -> colors.onUnread
                    },
                )
            }

            // Update the download count or local status and its visibility.
            with(binding.downloadText) {
                isVisible = downloads == -2 || downloads > 0
                if (!isVisible) {
                    return@with
                }
                text =
                    if (downloads == -2) {
                        resources.getString(R.string.local)
                    } else {
                        downloads.toString()
                    }
                setTextColor(colors.onDownload)
            }

            with(binding.langImage) {
                isVisible = !lang.isNullOrBlank()
                if (!lang.isNullOrBlank()) {
                    val flagId = resolveFlagRes(lang).takeIf { it != 0 }
                    if (flagId != null) {
                        if (lastFlagRes != flagId) {
                            lastFlagRes = flagId
                            setImageResource(flagId)
                        }
                    } else {
                        isVisible = false
                    }
                }
            }

            val unreadVisible = binding.unreadText.isVisible
            val downloadVisible = binding.downloadText.isVisible
            val langVisible = binding.langImage.isVisible
            val visibleCount =
                (if (unreadVisible) 1 else 0) + (if (downloadVisible) 1 else 0) + (if (langVisible) 1 else 0)
            val hasUnreadDot = unreadVisible && unread == -1

            // Only which parts are showing decides the shapes, and that rarely differs from one
            // item to the next, while redoing it means new paths for the badge and its outline
            val shapeKey =
                (if (unreadVisible) 1 else 0) or
                    (if (downloadVisible) 1 shl 1 else 0) or
                    (if (langVisible) 1 shl 2 else 0) or
                    (if (hasUnreadDot) 1 shl 3 else 0) or
                    (if (changeShape) 1 shl 4 else 0)
            if (shapeKey != lastShapeKey) {
                lastShapeKey = shapeKey
                applyShape(visibleCount, hasUnreadDot, changeShape, langVisible)
            }

            // Show the badge card if unread or downloads exists
            isVisible = visibleCount > 0

            // Leave room for the divider slant between two parts
            val dividerBeforeUnread = unreadVisible && visibleCount > 1
            val dividerBeforeDownload = downloadVisible && langVisible
            binding.downloadText.updatePaddingRelative(
                start = if (dividerBeforeDownload) 2.dpToPx else 5.dpToPx,
                end = if (dividerBeforeUnread) 8.dpToPx else 5.dpToPx,
            )
            binding.unreadText.updatePaddingRelative(start = if (dividerBeforeUnread) 2.dpToPx else 5.dpToPx)

            updateParts()

            // Set last so the visibility changes above don't clear it
            hasAppliedState = true
            lastUnread = unread
            lastDownloads = downloads
            lastShowTotalChapters = showTotalChapters
            lastLang = lang
            lastChangeShape = changeShape
        }

        /**
         * Hands [badgeDrawable] the fill and starting edge of every visible part, in the order they
         * ended up on screen.
         */
        private fun updateParts() {
            if (!this::binding.isInitialized) return
            val content = binding.badgeContent
            val indices = if (isRtl) content.childCount - 1 downTo 0 else 0 until content.childCount
            var count = 0
            var flagIndex = -1
            for (i in indices) {
                val child = content.getChildAt(i) ?: continue
                if (!child.isVisible) continue
                if (child.id == R.id.lang_image) {
                    flagIndex = count
                }
                partColors[count] = fillOf(child)
                partStarts[count] = child.left.toFloat()
                count++
            }
            badgeDrawable.setParts(partColors, partStarts, count)
            // The flag covers the drawable, so the cut into it has to be drawn back on top
            val neighbor =
                when {
                    count < 2 || flagIndex == -1 -> -1
                    flagIndex == 0 -> 1
                    flagIndex == count - 1 -> flagIndex - 1
                    else -> -1
                }
            if (neighbor == -1) {
                flagDividerColor = 0
            } else {
                flagDividerColor = partColors[neighbor]
                flagDividerX = partStarts[maxOf(flagIndex, neighbor)]
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (flagDividerColor == 0) return
            val content = binding.badgeContent
            val saveCount = canvas.save()
            canvas.translate(content.left.toFloat(), content.top.toFloat())
            badgeDrawable.drawDividerOver(canvas, flagDividerX, flagDividerColor)
            canvas.restoreToCount(saveCount)
        }

        @ColorInt
        private fun fillOf(part: View): Int =
            when (part.id) {
                R.id.lang_image -> colors.background
                R.id.download_text -> colors.download
                else -> unreadFill
            }

        /**
         * Rounds the ends of the badge, its outline, and the flag that draws its own pixels. Only
         * called when which parts are showing changes, so none of it is per bind work.
         */
        private fun applyShape(
            visibleCount: Int,
            hasUnreadDot: Boolean,
            changeShape: Boolean,
            langVisible: Boolean,
        ) {
            binding.unreadText.updateLayoutParams { width = LayoutParams.WRAP_CONTENT }
            if (!changeShape) {
                shapeAppearanceModel = shapeAppearanceModel.withCornerSize(ogRadius)
                badgeDrawable.setCorners(ogRadius, ogRadius, roundEnd = false, isRtl = isRtl, uniform = true)
                return
            }
            if (visibleCount == 1 && hasUnreadDot) {
                binding.unreadText.updateLayoutParams { width = (roundedRadius * 2).toInt() }
                shapeAppearanceModel = shapeAppearanceModel.withCornerSize(roundedRadius)
                badgeDrawable.setCorners(roundedRadius, roundedRadius, roundEnd = true, isRtl = isRtl, uniform = true)
                return
            }
            val endRadius = if (hasUnreadDot) roundedRadius else ogRadius
            shapeAppearanceModel = makeShapeCorners(ogRadius, endRadius, hasUnreadDot)
            badgeDrawable.setCorners(ogRadius, endRadius, hasUnreadDot, isRtl)
            if (hasUnreadDot) {
                binding.unreadText.updateLayoutParams { width = (roundedRadius * 1.25f).toInt() }
            }
            if (langVisible) {
                // The flag draws its own pixels, so the badge's fill can't round it for us
                val onlyPart = visibleCount == 1
                binding.langImage.shapeAppearanceModel =
                    makeShapeCorners(
                        topStart = ogRadius,
                        bottomEnd = if (onlyPart) endRadius else 0f,
                        roundEnd = onlyPart && hasUnreadDot,
                    )
            }
        }

        fun setChapters(chapters: Int?) {
            setUnreadDownload(chapters ?: 0, 0, chapters != null, null, true)
        }

        /** Returns the flag drawable res id for [lang], or 0 if none exists. */
        private fun resolveFlagRes(lang: String): Int =
            flagResCache.getOrPut(lang) {
                resources
                    .getIdentifier(
                        "ic_flag_${lang.replace("-", "_")}",
                        "drawable",
                        context.packageName,
                    ).takeIf { it != 0 }
                    ?: if (lang.contains("-")) {
                        resources.getIdentifier(
                            "ic_flag_${lang.split("-").first()}",
                            "drawable",
                            context.packageName,
                        )
                    } else {
                        0
                    }
            }

        fun setInLibrary(inLibrary: Boolean) {
            setLabel(inLibrary, R.string.in_library, colors.unread)
        }

        fun setDuplicateInLibrary(duplicateInLibrary: Boolean) {
            setLabel(duplicateInLibrary, R.string.duplicate_in_library, colors.duplicate)
        }

        private fun setLabel(
            show: Boolean,
            textRes: Int,
            @ColorInt fill: Int,
        ) {
            // The shape is set here rather than by [applyShape]
            lastShapeKey = -1
            this.isVisible = show
            unreadFill = fill
            binding.langImage.isVisible = false
            binding.downloadText.isVisible = false
            binding.unreadText.isVisible = show
            binding.unreadText.updatePaddingRelative(start = 5.dpToPx, end = 5.dpToPx)
            binding.unreadText.updateLayoutParams { width = LayoutParams.WRAP_CONTENT }
            binding.unreadText.setTextColor(colors.onUnread)
            binding.unreadText.text = resources.getText(textRes)
            // Same diagonal corner pattern as the rest of the badge family, not a plain pill
            shapeAppearanceModel = makeShapeCorners(ogRadius, ogRadius)
            badgeDrawable.setCorners(ogRadius, ogRadius, roundEnd = false, isRtl = isRtl, uniform = false)
            updateParts()
        }
    }
