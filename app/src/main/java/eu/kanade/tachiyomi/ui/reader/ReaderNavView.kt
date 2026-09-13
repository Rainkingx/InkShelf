package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.RequiresApi

class ReaderNavView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        override fun canScrollVertically(direction: Int): Boolean = true

        override fun shouldDelayChildPressedState(): Boolean = true

        @RequiresApi(Build.VERSION_CODES.S)
        override fun getScrollCaptureHint(): Int = SCROLL_CAPTURE_HINT_EXCLUDE
    }
