package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.GamepadHoldLoop
import eu.kanade.tachiyomi.ui.reader.viewer.JOYSTICK_DEADZONE
import eu.kanade.tachiyomi.ui.reader.viewer.JOYSTICK_PAN_INTERVAL_MS
import eu.kanade.tachiyomi.ui.reader.viewer.JOYSTICK_PAN_STEP
import eu.kanade.tachiyomi.ui.reader.viewer.PAN_STEP
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.ZOOM_HOLD_INTERVAL_MS
import eu.kanade.tachiyomi.ui.reader.viewer.gamepadZoomRate
import eu.kanade.tachiyomi.ui.reader.viewer.isDpadHatMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of a [BaseViewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(
    val activity: ReaderActivity,
) : BaseViewer {
    val downloadManager: DownloadManager by injectLazy()

    val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = createPager()

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = PagerConfig(scope, this)

    /**
     * Adapter of the pager.
     */
    private val adapter = PagerViewerAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    /**
     * Viewer chapters to set when the pager enters idle mode. Otherwise, if the view was settling
     * or dragging, there'd be a noticeable and annoying jump.
     */
    private var awaitingIdleViewerChapters: ViewerChapters? = null

    /**
     * Whether the view pager is currently in idle mode. It sets the awaiting chapters if setting
     * this field to true.
     */
    private var isIdle = true
        set(value) {
            field = value
            if (value) {
                awaitingIdleViewerChapters?.let { viewerChapters ->
                    setChaptersDoubleShift(viewerChapters)
                    awaitingIdleViewerChapters = null
                    if (viewerChapters.currChapter.pages?.size == 1) {
                        adapter.nextTransition?.to?.let {
                            activity.requestPreloadChapter(it)
                        }
                    }
                }
            }
        }

    var hasMoved = false

    /**
     * InkShelf Smart Bubble V1.
     *
     * Kept local to the paged reader so the normal J2K reader remains untouched when disabled.
     * V1 is intentionally exposed only for the left-to-right pager from ReaderActivity.
     */
    var smartBubbleEnabled = false
        private set

    private var smartBubbleEnterForward = true

    // When Smart Bubble changes page, show the complete artwork first. The next navigation tap
    // starts guided dialogue on that page instead of immediately jumping into the first OCR target.
    private var smartBubbleAwaitingPageStartTap = false

    // After the final dialogue target, keep the current page fully visible until the reader taps
    // again. This protects artwork at the bottom/edges of a page from being skipped by an automatic
    // page turn. The following page also still opens in its normal full-page overview state.
    private var smartBubbleAwaitingPageEndTap = false

    /**
     * Variable used to hold the forward pos for reader activity shared transitions
     * Without this var landscapezoom wont work with activity transitions
     * */
    var heldForwardZoom: Pair<Int, Boolean>? = null

    /**
     * Last known left analog stick position, used by [startJoystickPanLoop] to keep panning
     * for as long as it's held away from center.
     */
    private var joystickX = 0f
    private var joystickY = 0f

    /**
     * Job for the loop that keeps panning while the joystick is held, see [startJoystickPanLoop].
     */
    private var joystickPanJob: Job? = null

    /**
     * Last known combined zoom rate from the L2/R2 triggers and the right stick's Y axis, in
     * [-1, 1] (negative zooms out, positive zooms in). Applied by [zoomLoop].
     */
    private var zoomRate = 0f

    /**
     * Keeps zooming for as long as [zoomRate] is non-zero, e.g. while a trigger is held.
     */
    private val zoomLoop = GamepadHoldLoop(scope, activity, ZOOM_HOLD_INTERVAL_MS, { zoomRate }, ::zoomBy)

    /**
     * Which dpad directions are currently held, used by [startDpadPanLoop] to combine
     * simultaneously-held directions into a single diagonal pan.
     */
    private var isDpadUpHeld = false
    private var isDpadDownHeld = false
    private var isDpadLeftHeld = false
    private var isDpadRightHeld = false

    private var dpadPanDebounceJob: Job? = null

    private var pagerListener =
        object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (pager.isRestoring) return
                val page = adapter.joinedItems.getOrNull(position)
                if (!activity.isScrollingThroughPagesOrChapters && page?.first !is ChapterTransition) {
                    activity.hideMenu()
                }
                onPageChange(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                isIdle = state == ViewPager.SCROLL_STATE_IDLE
                if (!hasMoved) {
                    hasMoved = !isIdle
                }
            }
        }

    init {
        pager.isVisible = false // Don't lay out the pager yet
        pager.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        pager.offscreenPageLimit = 1
        pager.id = R.id.reader_pager
        pager.adapter = adapter
        pager.addOnPageChangeListener(pagerListener)
        pager.tapListener = f@{ event ->
            val pos = PointF(event.x / pager.width, event.y / pager.height)
            val navigator = config.navigator
            when (navigator.getAction(pos)) {
                ViewerNavigation.NavigationRegion.MENU -> activity.toggleMenu()
                ViewerNavigation.NavigationRegion.NEXT -> moveToNext()
                ViewerNavigation.NavigationRegion.PREV -> moveToPrevious()
                ViewerNavigation.NavigationRegion.RIGHT -> moveRight()
                ViewerNavigation.NavigationRegion.LEFT -> moveLeft()
            }
        }
        pager.longTapListener = f@{
            if (activity.menuVisible || config.longTapEnabled) {
                val item = adapter.joinedItems.getOrNull(pager.currentItem)
                val firstPage = item?.first as? ReaderPage
                val secondPage = item?.second as? ReaderPage
                if (firstPage is ReaderPage) {
                    activity.onPageLongTap(firstPage, secondPage)
                    return@f true
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            activity.isScrollingThroughPagesOrChapters = true
            refreshAdapter()
            activity.isScrollingThroughPagesOrChapters = false
        }

        config.reloadChapterListener = {
            activity.reloadChapters(it)
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayForNewUser
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
        config.navigationModeInvertedListener = { activity.binding.navigationOverlay.showNavigationAgain() }
    }

    /**
     * Creates a new ViewPager.
     */
    abstract fun createPager(): Pager

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View = pager

    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Returns the PagerPageHolder for the provided page
     */
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? =
        pager.children
            .filterIsInstance(PagerPageHolder::class.java)
            .firstOrNull { it.item.first.index == page.index || it.item.second?.index == page.index }

    /**
     * Returns the [PagerPageHolder] of the page that's currently on screen, if any.
     */
    private fun currentPageHolder(): PagerPageHolder? = (currentPage as? ReaderPage)?.let { getPageHolder(it) }

    override fun isZoomedIn(): Boolean = currentPageHolder()?.isZoomedIn() ?: false

    fun setSmartBubbleEnabled(enabled: Boolean): Boolean {
        if (smartBubbleEnabled == enabled) return smartBubbleEnabled
        smartBubbleEnabled = enabled
        currentPageHolder()?.let { holder ->
            if (smartBubbleEnabled) {
                // Start with the artwork visible. One more tap begins guided reading.
                smartBubbleAwaitingPageStartTap = true
                smartBubbleAwaitingPageEndTap = false
                holder.prepareSmartBubble()
                holder.showSmartBubbleOverview()
                prewarmSmartBubbleNeighbor(holder.page, smartBubbleEnterForward)
            } else {
                smartBubbleAwaitingPageStartTap = false
                smartBubbleAwaitingPageEndTap = false
                holder.exitSmartBubble()
            }
        }
        return smartBubbleEnabled
    }

    fun toggleSmartBubble(): Boolean = setSmartBubbleEnabled(!smartBubbleEnabled)

    fun isCurrentSmartBubblePage(page: ReaderPage): Boolean =
        smartBubbleEnabled && (currentPage as? ReaderPage)?.isFromSamePage(page) == true

    fun smartBubbleForwardForCurrentPage(): Boolean = smartBubbleEnterForward

    fun shouldHoldSmartBubbleOverview(page: ReaderPage): Boolean =
        smartBubbleEnabled &&
            smartBubbleAwaitingPageStartTap &&
            (currentPage as? ReaderPage)?.isFromSamePage(page) == true

    /**
     * Consumes the first navigation tap after a page change. The page has deliberately been left
     * fully visible so the reader can take in the artwork before choosing to continue dialogue.
     */
    private fun startSmartBubbleFromOverview(forward: Boolean): Boolean {
        if (!smartBubbleEnabled || !smartBubbleAwaitingPageStartTap) return false
        val holder = currentPageHolder() ?: return false
        smartBubbleAwaitingPageStartTap = false
        smartBubbleAwaitingPageEndTap = false
        smartBubbleEnterForward = forward
        holder.enterSmartBubble(forward)
        prewarmSmartBubbleNeighbor(holder.page, forward)
        return true
    }

    /**
     * Starts OCR on the nearest already-attached page in the direction of travel. ViewPager keeps
     * neighbouring holders attached, so this usually finishes before the reader reaches them.
     */
    private fun prewarmSmartBubbleNeighbor(
        current: ReaderPage,
        forward: Boolean,
    ) {
        if (!smartBubbleEnabled) return
        val neighbors =
            pager.children
                .filterIsInstance(PagerPageHolder::class.java)
                .filter { it.page !== current && it.page.chapter == current.chapter }

        val neighbor =
            if (forward) {
                neighbors
                    .filter { it.page.index > current.index }
                    .minByOrNull { it.page.index }
            } else {
                neighbors
                    .filter { it.page.index < current.index }
                    .maxByOrNull { it.page.index }
            }
        neighbor?.prepareSmartBubble()
    }

    /**
     * Re-applies cutout insets on every currently attached page (the visible page plus any
     * preloaded neighbors), e.g. after entering/exiting multi-window mode, without reloading
     * pages or resetting their zoom/pan position.
     */
    fun refreshCutoutInsets() {
        pager.children
            .filterIsInstance(PagerPageHolder::class.java)
            .forEach { it.refreshCutoutInsets() }
    }

    override fun isAtEndOfReader(): Boolean = (currentPage as? ChapterTransition.Next)?.let { it.to == null } ?: false

    override fun zoomIn() {
        currentPageHolder()?.zoomIn()
    }

    override fun zoomOut() {
        currentPageHolder()?.zoomOut()
    }

    override fun pan(
        dxRatio: Float,
        dyRatio: Float,
    ) {
        currentPageHolder()?.panBy(dxRatio, dyRatio)
    }

    override fun zoomBy(rate: Float) {
        currentPageHolder()?.zoomBy(rate)
    }

    /**
     * Called when a new page (either a [ReaderPage] or [ChapterTransition]) is marked as active
     */
    fun onPageChange(position: Int) {
        val page = adapter.joinedItems.getOrNull(position)
        if (page != null && currentPage != page) {
            val pageF = page.first
            val allowPreload = checkAllowPreload(pageF as? ReaderPage)
            val forward =
                // if both pages have the same number, it's a split page with an InsertPage
                when {
                    // Use case happens on new chapter load
                    currentPage == pageF -> null
                    currentPage is ReaderPage && pageF is ReaderPage ->
                        if (pageF.number == (currentPage as ReaderPage).number) {
                            // the InsertPage is always the second in the reading direction
                            pageF is InsertPage
                        } else {
                            pageF.number > (currentPage as ReaderPage).number
                        }
                    currentPage is ChapterTransition.Prev && pageF is ReaderPage ->
                        (currentPage as ChapterTransition).from == pageF.chapter
                    currentPage is ChapterTransition.Next && pageF is ReaderPage ->
                        (currentPage as ChapterTransition).to == pageF.chapter
                    else -> true
                }
            currentPage = pageF
            when (pageF) {
                is ReaderPage -> {
                    onReaderPageSelected(pageF, allowPreload, page.second is ReaderPage, forward)
                }
                is ChapterTransition -> onTransitionSelected(pageF)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        // Allow preload for
        // 1. Going to next chapter from chapter transition
        // 2. Going between pages of same chapter
        // 3. Next chapter page
        return when (page.chapter) {
            (currentPage as? ChapterTransition.Next)?.to -> true
            (currentPage as? ReaderPage)?.chapter -> true
            adapter.nextTransition?.to -> true
            else -> false
        }
    }

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(
        page: ReaderPage,
        allowPreload: Boolean,
        hasExtraPage: Boolean,
        forward: Boolean?,
    ) {
        activity.onPageSelected(page, hasExtraPage)

        // Notify holder of page change
        val holder = getPageHolder(page)
        if (holder == null && forward != null && heldForwardZoom == null) {
            heldForwardZoom = page.index to forward
        } else {
            holder?.onPageSelected(forward)
        }

        smartBubbleEnterForward = forward != false
        if (smartBubbleEnabled) {
            // Page changes are a context beat: show the full page and wait for the user's next
            // navigation tap before entering the first/last dialogue target.
            smartBubbleAwaitingPageStartTap = true
            smartBubbleAwaitingPageEndTap = false
            holder?.prepareSmartBubble()
            holder?.showSmartBubbleOverview()
            prewarmSmartBubbleNeighbor(page, smartBubbleEnterForward)
        }

        val offset = if (hasExtraPage) 1 else 0
        val pages = page.chapter.pages ?: return
        if (hasExtraPage) {
            Timber.d("onReaderPageSelected: ${page.number}-${page.number + offset}/${pages.size}")
        } else {
            Timber.d("onReaderPageSelected: ${page.number}/${pages.size}")
        }
        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            Timber.d("Request preload next chapter because we're at page ${page.number} of ${pages.size}")
            adapter.nextTransition?.to?.let {
                activity.requestPreloadChapter(it)
            }
        }
    }

    /**
     * Called when a [ChapterTransition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        Timber.d("onTransitionSelected: $transition")
        val toChapter = transition.to
        if (toChapter != null) {
            Timber.d("Request preload destination chapter because we're on the transition")
            activity.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // No more chapters, show menu because the user is probably going to close the reader
            activity.showMenu()
        }
    }

    private fun getItem(
        position: Int,
        currentChapter: ReaderChapter?,
    ): Pair<Any, Any?>? {
        return adapter.joinedItems.firstOrNull {
            val readerPage = it.first as? ReaderPage ?: return@firstOrNull false
            readerPage.index == position && readerPage.chapter.chapter.id == currentChapter?.chapter?.id
        }
    }

    fun hasExtraPage(
        position: Int,
        currentChapter: ReaderChapter?,
    ): Boolean {
        val item = getItem(position, currentChapter) ?: return false
        return item.second is ReaderPage
    }

    fun setChaptersDoubleShift(chapters: ViewerChapters) {
        // Remove Listener since we're about to change the size of the items
        // If we don't the size change could put us on a new chapter
        pager.removeOnPageChangeListener(pagerListener)
        setChaptersInternal(chapters)
        if (!hasMoved) {
            activity.isScrollingThroughPagesOrChapters = true
            chapters.currChapter.pages?.let { pages ->
                moveToPage(pages[chapters.currChapter.requestedPage], false)
            }
            activity.isScrollingThroughPagesOrChapters = false
        }
        pager.addOnPageChangeListener(pagerListener)
        // Since we removed the listener while shifting, call page change to update the ui
        onPageChange(pager.currentItem)
    }

    fun updateShifting(page: ReaderPage? = null) {
        adapter.pageToShift = page ?: adapter.joinedItems[pager.currentItem].first as? ReaderPage
    }

    fun getShiftedPage(): ReaderPage? = adapter.pageToShift

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        if (isIdle) {
            setChaptersDoubleShift(chapters)
        } else {
            awaitingIdleViewerChapters = chapters
        }
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        Timber.d("setChaptersInternal")
        val forceTransition =
            config.alwaysShowChapterTransition ||
                adapter.joinedItems
                    .getOrNull(
                        pager
                            .currentItem,
                    )?.first is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set
        if (pager.isGone) {
            Timber.d("Pager first layout")
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[chapters.currChapter.requestedPage])
            pager.isVisible = true
        }
        activity.invalidateOptionsMenu()
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(
        page: ReaderPage,
        animated: Boolean,
    ) {
        Timber.d("moveToPage ${page.number}")
        val position =
            adapter.joinedItems.indexOfFirst {
                it.first == page ||
                    it.second == page ||
                    (
                        config.splitPages &&
                            it.first is ReaderPage &&
                            (it.first as? ReaderPage)?.isFromSamePage(page) == true &&
                            (it.first as? ReaderPage)?.firstHalf != false
                    )
            }
        if (position != -1) {
            val currentPosition = pager.currentItem
            pager.setCurrentItem(position, animated)
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
                onPageChange(position)
            } else {
                // Call this since with double shift onPageChange wont get called (it shouldn't)
                // Instead just update the page count in ui
                val joinedItem = adapter.joinedItems.firstOrNull { it.first == page || it.second == page }
                activity.onPageSelected(
                    joinedItem?.first as? ReaderPage ?: page,
                    joinedItem?.second is ReaderPage,
                )
            }
        } else {
            Timber.d("Page $page not found in adapter")
        }
    }

    override fun moveToNext() {
        moveRight()
    }

    override fun moveToPrevious() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    protected open fun moveRight() {
        if (startSmartBubbleFromOverview(true)) return
        if (smartBubbleEnabled) {
            // The first tap after the final dialogue target only reveals the complete current page.
            // A second tap turns the page. This avoids skipping important artwork after the last
            // speech bubble/caption.
            if (smartBubbleAwaitingPageEndTap) {
                smartBubbleAwaitingPageEndTap = false
                if (pager.currentItem != adapter.count - 1) {
                    hasMoved = true
                    pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
                }
                return
            }

            when (currentPageHolder()?.smartBubbleNext()) {
                SmartBubbleNavigationResult.HANDLED -> return
                SmartBubbleNavigationResult.PAGE_COMPLETE -> {
                    smartBubbleAwaitingPageEndTap = true
                    currentPageHolder()?.showSmartBubbleOverview()
                    return
                }
                SmartBubbleNavigationResult.UNAVAILABLE, null -> Unit
            }
        }
        if (pager.currentItem != adapter.count - 1) {
            hasMoved = true
            val holder = (currentPage as? ReaderPage)?.let { getPageHolder(it) }
            if (holder != null && config.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        if (startSmartBubbleFromOverview(false)) return
        if (smartBubbleEnabled) {
            // If the user backs up while viewing the end-of-page artwork, return to the final
            // dialogue target instead of unexpectedly leaving the page.
            if (smartBubbleAwaitingPageEndTap) {
                smartBubbleAwaitingPageEndTap = false
                currentPageHolder()?.enterSmartBubble(false)
                return
            }

            when (currentPageHolder()?.smartBubblePrevious()) {
                SmartBubbleNavigationResult.HANDLED -> return
                SmartBubbleNavigationResult.PAGE_COMPLETE -> {
                    if (pager.currentItem != 0) {
                        hasMoved = true
                        pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
                    }
                    return
                }
                SmartBubbleNavigationResult.UNAVAILABLE, null -> Unit
            }
        }
        if (pager.currentItem != 0) {
            hasMoved = true
            val holder = (currentPage as? ReaderPage)?.let { getPageHolder(it) }
            if (holder != null && config.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
        }
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        moveToNext()
    }

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed.
     */
    private fun refreshAdapter() {
        val currentItem = pager.currentItem
        pager.adapter = adapter
        pager.setCurrentItem(currentItem, false)
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        // Page-turning/scrolling should only fire once per physical press - repeatCount == 0
        // excludes the auto-repeated ACTION_DOWN events a held key/button generates, matching
        // the old ACTION_UP-based firing (which has no repeat concept, only a single release).
        // Zoom/pan is allowed to repeat while held instead, since continuously zooming/panning
        // is the point, so those branches key off [isDown] alone.
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isInitialDown = isDown && event.repeatCount == 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isInitialDown) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isInitialDown) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }
            // While the menu is open, let the dpad/arrow keys drive normal Android focus
            // navigation between the menu's buttons instead of turning/panning pages. While
            // zoomed in, holding a direction pans via startDpadPanLoop rather than firing pan()
            // directly here, so simultaneously held directions combine into a diagonal pan
            // instead of relying on (unreliable, single-key) OS key-repeat for each axis.
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (activity.menuVisible) return false
                if (!isDown) {
                    isDpadRightHeld = false
                } else if (isZoomedIn()) {
                    isDpadRightHeld = true
                    if (isInitialDown && this !is VerticalPagerViewer && currentPageHolder()?.canPanRight() == false) {
                        moveRight()
                    } else {
                        startDpadPanLoop(event.keyCode, event.repeatCount > 0)
                    }
                } else if (isInitialDown) {
                    moveRight()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (activity.menuVisible) return false
                if (!isDown) {
                    isDpadLeftHeld = false
                } else if (isZoomedIn()) {
                    isDpadLeftHeld = true
                    if (isInitialDown && this !is VerticalPagerViewer && currentPageHolder()?.canPanLeft() == false) {
                        moveLeft()
                    } else {
                        startDpadPanLoop(event.keyCode, event.repeatCount > 0)
                    }
                } else if (isInitialDown) {
                    moveLeft()
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (activity.menuVisible) return false
                if (!isDown) {
                    isDpadDownHeld = false
                } else if (isZoomedIn()) {
                    isDpadDownHeld = true
                    if (isInitialDown && this is VerticalPagerViewer && currentPageHolder()?.canPanDown() == false) {
                        moveDown()
                    } else {
                        startDpadPanLoop(event.keyCode, event.repeatCount > 0)
                    }
                } else if (isInitialDown) {
                    moveDown()
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (activity.menuVisible) return false
                if (!isDown) {
                    isDpadUpHeld = false
                } else if (isZoomedIn()) {
                    isDpadUpHeld = true
                    if (isInitialDown && this is VerticalPagerViewer && currentPageHolder()?.canPanUp() == false) {
                        moveUp()
                    } else {
                        startDpadPanLoop(event.keyCode, event.repeatCount > 0)
                    }
                } else if (isInitialDown) {
                    moveUp()
                }
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isInitialDown) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isInitialDown) moveUp()

            // Gamepad shoulder buttons seek pages left/right, matching the dpad's spatial mapping.
            KeyEvent.KEYCODE_BUTTON_L1 -> if (isInitialDown) pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            KeyEvent.KEYCODE_BUTTON_R1 -> if (isInitialDown) pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)

            // Gamepad X/Y zoom the current page in/out, repeating while held.
            KeyEvent.KEYCODE_BUTTON_Y -> if (isDown) zoomIn()
            KeyEvent.KEYCODE_BUTTON_X -> if (isDown) zoomOut()
            else -> return false
        }
        return true
    }

    fun splitDoublePages(currentPage: ReaderPage) {
        adapter.splitDoublePages(currentPage)
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            if (event.isDpadHatMotion()) return false

            joystickX = event.getAxisValue(MotionEvent.AXIS_X)
            joystickY = event.getAxisValue(MotionEvent.AXIS_Y)
            val deflected = abs(joystickX) > JOYSTICK_DEADZONE || abs(joystickY) > JOYSTICK_DEADZONE
            if (deflected && !activity.menuVisible && isZoomedIn()) {
                startJoystickPanLoop()
            } else {
                joystickPanJob?.cancel()
            }

            zoomRate = event.gamepadZoomRate()
            zoomLoop.update()

            return (deflected && !activity.menuVisible && isZoomedIn()) || zoomRate != 0f
        }
        return false
    }

    /**
     * The joystick only sends a [MotionEvent] when its axes change, but panning should continue
     * for as long as the stick is held away from center. This starts a loop that keeps panning
     * using the last known [joystickX]/[joystickY] values until it's released, re-centered, the
     * menu opens, or the page is no longer zoomed in.
     */
    private fun startJoystickPanLoop() {
        if (joystickPanJob?.isActive == true) return
        joystickPanJob =
            scope.launch {
                while (isActive) {
                    val x = joystickX
                    val y = joystickY
                    if (activity.menuVisible ||
                        !isZoomedIn() ||
                        (abs(x) <= JOYSTICK_DEADZONE && abs(y) <= JOYSTICK_DEADZONE)
                    ) {
                        break
                    }
                    pan(x * JOYSTICK_PAN_STEP, y * JOYSTICK_PAN_STEP)
                    delay(JOYSTICK_PAN_INTERVAL_MS.milliseconds)
                }
            }
    }

    /**
     * Starts/continues panning for a held dpad key, combining every currently-held direction
     * into a single diagonal [pan] call rather than one call per axis - two separate calls for
     * the same tick would each read the page's center before the other's animation had visually
     * applied, fighting each other instead of composing.
     *
     * A repeat (native OS key-repeat, once it kicks in) always pans immediately from the current
     * held state. A key's very *first* press only does that immediately if some other direction
     * is already held - i.e. it's joining an existing hold, so it should contribute to the
     * diagonal right away. Otherwise, it's presumed to be a clean, isolated press, and waits for
     * the next UI frame instead of panning immediately: two real key presses meant as one
     * diagonal input are rarely perfectly simultaneous, so this gives the second one a chance to
     * also register and be included, rather than firing a single-axis pan for the first key
     * alone before the second key's event has even arrived. A frame (rather than a guessed
     * delay) is used since that's however long the *next* dispatch actually takes to reach us,
     * with no risk of firing before it or waiting longer than necessary.
     */
    private fun startDpadPanLoop(
        keyCode: Int,
        isRepeating: Boolean,
    ) {
        if (activity.menuVisible || !isZoomedIn()) return
        val otherDirectionHeld =
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> isDpadLeftHeld || isDpadUpHeld || isDpadDownHeld
                KeyEvent.KEYCODE_DPAD_LEFT -> isDpadRightHeld || isDpadUpHeld || isDpadDownHeld
                KeyEvent.KEYCODE_DPAD_UP -> isDpadDownHeld || isDpadLeftHeld || isDpadRightHeld
                KeyEvent.KEYCODE_DPAD_DOWN -> isDpadUpHeld || isDpadLeftHeld || isDpadRightHeld
                else -> false
            }
        if (isRepeating || otherDirectionHeld) {
            dpadPanDebounceJob?.cancel()
            panFromHeldDpadDirections()
        } else {
            // Cancels itself instead of a stray leftover firing later, e.g. a repeat coming in
            // and panning immediately (above) before this frame arrives.
            dpadPanDebounceJob =
                scope.launch {
                    awaitFrame()
                    panFromHeldDpadDirections()
                }
        }
    }

    private fun panFromHeldDpadDirections() {
        if (activity.menuVisible || !isZoomedIn()) return
        val dx = (if (isDpadRightHeld) PAN_STEP else 0f) - (if (isDpadLeftHeld) PAN_STEP else 0f)
        val dy = (if (isDpadDownHeld) PAN_STEP else 0f) - (if (isDpadUpHeld) PAN_STEP else 0f)
        if (dx == 0f && dy == 0f) return
        pan(dx, dy)
    }

    fun hideMenuIfVisible(item: Any) {
        val currentItem = adapter.joinedItems.getOrNull(pager.currentItem)
        if (item == currentItem && isIdle) {
            activity.hideMenu()
        }
    }
}
