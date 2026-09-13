package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Suppresses the framework's default "scroll to fully reveal the focused child" behavior. This
 * recycler already keeps itself scrolled to the position it wants (see
 * [ReaderChapterSheet.focusCurrentChapter]/`onStateChanged`/`refreshList`), so letting a mere
 * focus change - e.g. from [ReaderChapterSheet.focusCurrentChapter] - trigger another scroll on
 * top of that just fights it, overscrolling no matter how long you wait before requesting focus.
 */
class ChapterRecyclerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RecyclerView(context, attrs) {
        override fun requestChildRectangleOnScreen(
            child: View,
            rect: Rect,
            immediate: Boolean,
        ): Boolean = false
    }
