package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.ContinueReadingCardBinding
import kotlin.math.roundToInt

/** Binds one modern Continue Reading card without duplicating progress/resume logic. */
internal fun ContinueReadingCardBinding.bindContinueReadingEntry(
    entry: ContinueReadingEntry,
    controller: LibraryController?,
) {
    val manga = entry.manga
    val chapter = entry.chapter

    val pageTotal = chapter.last_page_read + chapter.pages_left
    val pageProgress =
        if (entry.resumesPartialChapter && pageTotal > 0) {
            (((chapter.last_page_read + 1) * 100f) / pageTotal)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            null
        }

    val totalChapters = manga.totalChapters
    val seriesProgress =
        if (totalChapters > 0) {
            ((manga.read * 100f) / totalChapters)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            0
        }
    val progressValue = pageProgress ?: seriesProgress

    coverThumbnail.loadManga(manga)
    title.text = manga.title
    progressLabel.text = chapter.name
    progress.progress = progressValue
    progressRing.progress = progressValue
    percent.text = root.context.getString(R.string.modern_library_percent, progressValue)

    root.setOnClickListener {
        controller?.resumeHistoryFromLibrary(entry)
    }
    root.setOnLongClickListener {
        controller?.showContinueReadingMenu(entry)
        true
    }
}
