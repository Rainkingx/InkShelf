package eu.kanade.tachiyomi.util.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.widget.TextView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.dpToPxEnd
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.hueShiftedTo
import eu.kanade.tachiyomi.util.system.isHighTextContrastEnabled
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class ChapterUtil {
    companion object {
        private val decimalFormat =
            DecimalFormat(
                "#.###",
                DecimalFormatSymbols()
                    .apply { decimalSeparator = '.' },
            )

        fun relativeDate(chapter: Chapter): String? =
            when (chapter.date_upload > 0) {
                true -> chapter.date_upload.timeSpanFromNow
                false -> null
            }

        fun setTextViewForChapter(
            textView: TextView,
            chapter: Chapter,
            showBookmark: Boolean = true,
            hideStatus: Boolean = false,
            accent: Int? = null,
        ) {
            val context = textView.context
            textView.setTextColor(chapterColor(context, chapter, hideStatus).hueShiftedTo(accent))
            val showReadIndicator = !hideStatus && chapter.read && context.isHighTextContrastEnabled()
            textView.paintFlags =
                if (showReadIndicator) {
                    textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            if (!hideStatus && showBookmark) {
                setBookmark(textView, chapter, showReadIndicator)
            }
        }

        private fun setBookmark(
            textView: TextView,
            chapter: Chapter,
            showReadIndicator: Boolean,
        ) {
            val context = textView.context
            val bookmarkDrawable =
                if (chapter.bookmark) {
                    VectorDrawableCompat
                        .create(textView.resources, R.drawable.ic_bookmark_24dp, context.theme)
                        ?.also {
                            it.setBounds(0, 0, textView.textSize.toInt(), textView.textSize.toInt())
                            it.setTintList(ColorStateList.valueOf(bookmarkedColor(context)))
                        }
                } else {
                    null
                }
            val readDrawable =
                if (showReadIndicator) {
                    VectorDrawableCompat
                        .create(textView.resources, R.drawable.ic_eye_24dp, context.theme)
                        ?.also {
                            it.setBounds(0, 0, textView.textSize.toInt(), textView.textSize.toInt())
                            it.setTintList(ColorStateList.valueOf(readColor(context)))
                        }
                } else {
                    null
                }

            textView.setCompoundDrawablesRelative(bookmarkDrawable, null, readDrawable, null)

            if (bookmarkDrawable != null || readDrawable != null) {
                textView.compoundDrawablePadding = 3.dpToPx
            }
            textView.translationX =
                if (bookmarkDrawable != null) (-2f).dpToPxEnd(textView.resources) else 0f
        }

        /** Re-tints just the bookmark compound drawable set by [setBookmark], leaving any other compound drawable (e.g. the read indicator) untouched. */
        fun tintBookmarkDrawable(
            textView: TextView,
            tint: Int,
        ) {
            // The bookmark is always the start drawable; the read indicator (if any) is the end one.
            textView.compoundDrawablesRelative[0]?.setTintList(ColorStateList.valueOf(tint))
        }

        fun chapterColor(
            context: Context,
            chapter: Chapter,
            hideStatus: Boolean = false,
        ): Int =
            when {
                hideStatus -> unreadColor(context)
                chapter.read -> readColor(context)
                else -> unreadColor(context)
            }

        fun readColor(
            context: Context,
            chapter: Chapter,
        ): Int =
            when {
                chapter.read -> readColor(context)
                else -> unreadColor(context)
            }

        fun bookmarkColor(
            context: Context,
            chapter: Chapter,
        ): Int =
            when {
                chapter.bookmark -> bookmarkedColor(context)
                else -> readColor(context)
            }

        private fun readColor(context: Context): Int = context.contextCompatColor(R.color.read_chapter)

        private fun unreadColor(context: Context): Int = context.getResourceColor(R.attr.colorOnBackground)

        private fun bookmarkedColor(context: Context): Int = context.getResourceColor(R.attr.colorPrimary)

        private val volumeRegex = Regex("""(vol|volume)\.? *([0-9]+)?""", RegexOption.IGNORE_CASE)
        private val seasonRegex = Regex("""(Season |S)([0-9]+)?""")

        fun getGroupNumber(chapter: Chapter): Int? {
            val groups = volumeRegex.find(chapter.name)?.groups
            if (groups != null) return groups[2]?.value?.toIntOrNull()
            val seasonGroups = seasonRegex.find(chapter.name)?.groups
            if (seasonGroups != null) return seasonGroups[2]?.value?.toIntOrNull()
            return null
        }

        private fun getVolumeNumber(chapter: Chapter): Int? {
            val groups = volumeRegex.find(chapter.name)?.groups
            if (groups != null) return groups[2]?.value?.toIntOrNull()
            return null
        }

        private fun getSeasonNumber(chapter: Chapter): Int? {
            val groups = seasonRegex.find(chapter.name)?.groups
            if (groups != null) return groups[2]?.value?.toIntOrNull()
            return null
        }

        fun hasMultipleVolumes(chapters: List<Chapter>): Boolean {
            val volumeSet = mutableSetOf<Int>()
            chapters.forEach {
                val volNum = getVolumeNumber(it)
                if (volNum != null) {
                    volumeSet.add(volNum)
                    if (volumeSet.size >= 2) return true
                }
            }
            return false
        }

        fun hasMultipleSeasons(chapters: List<Chapter>): Boolean {
            val volumeSet = mutableSetOf<Int>()
            chapters.forEach {
                val volNum = getSeasonNumber(it)
                if (volNum != null) {
                    volumeSet.add(volNum)
                    if (volumeSet.size >= 2) return true
                }
            }
            return false
        }

        fun hasTensOfChapters(chapters: List<ChapterItem>): Boolean = chapters.size > 20

        const val scanlatorSeparator = " & "

        fun getScanlators(scanlators: String?): List<String> {
            if (scanlators.isNullOrBlank()) return emptyList()
            return scanlators.split(scanlatorSeparator).distinct()
        }

        fun getScanlatorString(scanlators: Set<String>): String = scanlators.toList().sorted().joinToString(scanlatorSeparator)

        fun Chapter.preferredChapterName(
            context: Context,
            manga: Manga,
            preferences: PreferencesHelper,
        ): String =
            if (manga.hideChapterTitle(preferences) && isRecognizedNumber) {
                val number = decimalFormat.format(chapter_number.toDouble())
                context.getString(R.string.chapter_, number)
            } else {
                name
            }
    }
}
