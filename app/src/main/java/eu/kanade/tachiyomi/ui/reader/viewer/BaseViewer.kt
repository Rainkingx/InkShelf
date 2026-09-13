package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters

/**
 * Interface for implementing a viewer.
 */
interface BaseViewer {
    /**
     * Returns the view this viewer uses.
     */
    fun getView(): View

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    fun destroy() {}

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    fun setChapters(chapters: ViewerChapters)

    /**
     * Tells this viewer to move to the given [page].
     */
    fun moveToPage(
        page: ReaderPage,
        animated: Boolean = true,
    )

    /**
     * Moves to the next page.
     */
    fun moveToNext()

    /**
     * Moves to the previous page.
     */
    fun moveToPrevious()

    /**
     * Returns whether the current page is zoomed in past its default scale. Used to decide
     * whether a dpad/analog stick input should pan the page or turn to the next/previous one.
     */
    fun isZoomedIn(): Boolean = false

    /**
     * Zooms the current page in, e.g. from a gamepad or keyboard press. No-op if unsupported.
     */
    fun zoomIn() {}

    /**
     * Zooms the current page out, e.g. from a gamepad or keyboard press. No-op if unsupported.
     */
    fun zoomOut() {}

    /**
     * Pans the current page by [dxRatio]/[dyRatio] of its width/height, if zoomed in. No-op if
     * unsupported.
     */
    fun pan(
        dxRatio: Float,
        dyRatio: Float,
    ) {}

    /**
     * Applies one non-animated zoom step scaled by [rate] (-1 = fastest zoom out, 1 = fastest
     * zoom in). Meant to be called repeatedly (e.g. every frame) while a zoom input is held,
     * such as the L2/R2 trigger axes. No-op if unsupported.
     */
    fun zoomBy(rate: Float) {}

    /**
     * Returns whether the current page is the "next chapter" transition with no chapter beyond
     * it, i.e. the end of the manga. This page opens the menu automatically, so a controller's
     * back/B button should perform a regular back press instead of just closing the menu.
     */
    fun isAtEndOfReader(): Boolean = false

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    fun handleGenericMotionEvent(event: MotionEvent): Boolean
}
