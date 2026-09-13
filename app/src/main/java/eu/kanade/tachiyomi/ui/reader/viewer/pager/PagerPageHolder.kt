package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBackgroundColor
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderErrorView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig.ZoomType
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.ImageUtil.isPagePadded
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.bottomCutoutInset
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.topCutoutInset
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    val viewer: PagerViewer,
    val page: ReaderPage,
    private var extraPage: ReaderPage? = null,
) : ReaderPageImageView(viewer.activity),
    ViewPagerAdapter.PositionableView {
    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page to extraPage

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressBar = createProgressBar()

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorView? = null

    /**
     * Job for loading the page.
     */
    private var loadJob: Job? = null

    /**
     * Job for status changes of the page.
     */
    private var statusJob: Job? = null

    /**
     * Job for progress changes of the page.
     */
    private var progressJob: Job? = null

    /**
     * Job for loading the page.
     */
    private var extraLoadJob: Job? = null

    /**
     * Job for status changes of the page.
     */
    private var extraStatusJob: Job? = null

    /**
     * Job for progress changes of the page.
     */
    private var extraProgressJob: Job? = null

    /**
     * Job used to read the header of the image. This is needed in order to instantiate
     * the appropriate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderJob: Job? = null

    private var status = Page.State.READY
    private var extraStatus = Page.State.READY
    private var progress: Int = 0
    private var extraProgress: Int = 0

    private var scope = MainScope()

    private var smartBubbleRegions: List<RectF>? = null
    private var smartBubbleIndex = -1
    private var smartBubbleScanJob: Job? = null
    private var smartBubblePendingDirection: Int? = null
    private var smartBubbleLastFocusedCenter: PointF? = null
    private var smartBubbleTransitionGeneration = 0
    private var smartBubbleTransitionInProgress = false

    init {
        addView(progressBar)
        if (viewer.config.hingeGapSize > 0) {
            progressBar.updateLayoutParams<MarginLayoutParams> {
                marginStart = ((context.resources.displayMetrics.widthPixels) / 2 + viewer.config.hingeGapSize) / 2
            }
        }
        launchLoadJob()
        setBackgroundColor(
            when (val theme = viewer.config.readerTheme) {
                ReaderBackgroundColor.SMART_THEME.prefValue -> Color.TRANSPARENT
                else -> ThemeUtil.readerBackgroundColor(theme)
            },
        )
        progressBar.foregroundTintList =
            ColorStateList.valueOf(
                context.getResourceColor(
                    if (isInvertedFromTheme()) {
                        R.attr.colorPrimaryInverse
                    } else {
                        R.attr.colorPrimary
                    },
                ),
            )
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        (pageView as? SubsamplingScaleImageView)?.apply {
            if (this@PagerPageHolder.extraPage == null &&
                this@PagerPageHolder.page.longPage == null &&
                sHeight < sWidth
            ) {
                this@PagerPageHolder.page.longPage = true
            }
        }
        onImageDecoded()

        if (viewer.smartBubbleEnabled) {
            if (viewer.isCurrentSmartBubblePage(page)) {
                prepareSmartBubble()
                if (viewer.shouldHoldSmartBubbleOverview(page)) {
                    showSmartBubbleOverview()
                } else {
                    enterSmartBubble(viewer.smartBubbleForwardForCurrentPage())
                }
            } else {
                // Pre-analyse attached neighbour pages so OCR is ready when the reader gets there.
                prepareSmartBubble()
            }
        }
    }

    override fun onNeedsLandscapeZoom() {
        (pageView as? SubsamplingScaleImageView)?.apply {
            if (viewer.heldForwardZoom?.first == page.index) {
                landscapeZoom(viewer.heldForwardZoom?.second)
                viewer.heldForwardZoom = null
            } else if (isVisibleOnScreen()) {
                landscapeZoom(true)
            }
        }
    }

    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.hideMenuIfVisible(item)
    }

    override fun onImageLoadError() {
        super.onImageLoadError()
        onImageDecodeError()
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelProgressJob(1)
        cancelLoadJob(1)
        cancelProgressJob(2)
        cancelLoadJob(2)
        cancelReadImageHeader()
        smartBubbleScanJob?.cancel()
        smartBubbleScanJob = null
        (pageView as? SubsamplingScaleImageView)?.setOnImageEventListener(null)
    }

    /**
     * Starts loading the page and processing changes to the page's status.
     *
     * @see processStatus
     */
    private fun launchLoadJob() {
        loadJob?.cancel()
        statusJob?.cancel()

        val loader = page.chapter.pageLoader ?: return
        loadJob =
            scope.launch {
                loader.loadPage(page)
            }
        statusJob =
            scope.launch {
                page.statusFlow.collectLatest { processStatus(it) }
            }
        val extraPage = extraPage ?: return
        extraLoadJob =
            scope.launch {
                loader.loadPage(extraPage)
            }
        extraStatusJob =
            scope.launch {
                extraPage.statusFlow.collectLatest { processStatus2(it) }
            }
    }

    private fun launchProgressJob() {
        progressJob?.cancel()
        progressJob =
            scope.launch {
                page.progressFlow.collectLatest { value ->
                    progress = value
                    if (extraPage == null) {
                        setProgress(progress)
                    } else {
                        setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
                    }
                }
            }
    }

    private fun launchProgressJob2() {
        val extraPage = extraPage ?: return
        extraProgressJob?.cancel()
        extraProgressJob =
            scope.launch {
                extraPage.progressFlow.collectLatest { value ->
                    extraProgress = value
                    setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
                }
            }
    }

    fun onPageSelected(forward: Boolean?) {
        (pageView as? SubsamplingScaleImageView)?.apply {
            if (isReady) {
                landscapeZoom(forward)
            } else {
                forward ?: return@apply
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(imageConfig)
                            landscapeZoom(forward)
                            this@PagerPageHolder.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageDecodeError()
                        }
                    },
                )
            }
        }
    }

    /**
     * Starts Smart Bubble on this page. V1 intentionally skips double/split pages; those keep
     * the normal reader behaviour rather than guessing against the wrong source geometry.
     */
    /** Pre-analyse this page without changing its zoom/pan position. */
    fun prepareSmartBubble() {
        if (!supportsSmartBubble()) return
        ensureSmartBubbleRegions()
    }

    /**
     * Shows the complete page without selecting a dialogue target. Used after a page turn so the
     * reader gets an intentional artwork/context beat before the next tap resumes Smart Bubble.
     */
    fun showSmartBubbleOverview() {
        if (!supportsSmartBubble()) return
        smartBubblePendingDirection = null
        smartBubbleIndex = -1
        smartBubbleLastFocusedCenter = null
        smartBubbleTransitionGeneration++
        smartBubbleTransitionInProgress = false
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (!view.isReady || view.sWidth <= 0 || view.sHeight <= 0) return
        val center = PointF(view.sWidth / 2f, view.sHeight / 2f)
        view
            .animateScaleAndCenter(view.minScale, center)
            ?.withDuration(SMART_BUBBLE_PAGE_OVERVIEW_MS)
            ?.withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
            ?.withInterruptible(true)
            ?.start()
    }

    fun enterSmartBubble(forward: Boolean) {
        if (!supportsSmartBubble()) return
        smartBubblePendingDirection = if (forward) 1 else -1
        ensureSmartBubbleRegions()
        smartBubbleRegions?.takeIf { it.isNotEmpty() }?.let { regions ->
            smartBubbleIndex = if (forward) 0 else regions.lastIndex
            focusSmartBubble(smartBubbleIndex)
            smartBubblePendingDirection = null
        }
    }

    fun exitSmartBubble() {
        smartBubblePendingDirection = null
        smartBubbleIndex = -1
        smartBubbleLastFocusedCenter = null
        smartBubbleTransitionGeneration++
        smartBubbleTransitionInProgress = false
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (!view.isReady) return
        val center = PointF(view.sWidth / 2f, view.sHeight / 2f)
        view
            .animateScaleAndCenter(view.minScale, center)
            ?.withDuration(SMART_BUBBLE_ANIMATION_MS)
            ?.withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
            ?.withInterruptible(true)
            ?.start()
    }

    fun smartBubbleNext(): SmartBubbleNavigationResult {
        if (!supportsSmartBubble()) return SmartBubbleNavigationResult.UNAVAILABLE
        if (smartBubbleTransitionInProgress) return SmartBubbleNavigationResult.HANDLED
        val regions = smartBubbleRegions
        if (regions == null) {
            smartBubblePendingDirection = 1
            ensureSmartBubbleRegions()
            return SmartBubbleNavigationResult.HANDLED
        }
        if (regions.isEmpty()) return SmartBubbleNavigationResult.PAGE_COMPLETE

        // If the reader has manually panned away from the last Smart Bubble target, resume from
        // the bubble nearest the current viewport instead of blindly continuing the old index.
        // This lets someone pan back to the top/right (or anywhere else) and naturally restart
        // from that part of the page.
        if (reanchorSmartBubbleToViewport(regions)) return SmartBubbleNavigationResult.HANDLED

        val target = if (smartBubbleIndex < 0) 0 else smartBubbleIndex + 1
        if (target > regions.lastIndex) return SmartBubbleNavigationResult.PAGE_COMPLETE

        smartBubbleIndex = target
        transitionSmartBubble(target)
        return SmartBubbleNavigationResult.HANDLED
    }

    fun smartBubblePrevious(): SmartBubbleNavigationResult {
        if (!supportsSmartBubble()) return SmartBubbleNavigationResult.UNAVAILABLE
        if (smartBubbleTransitionInProgress) return SmartBubbleNavigationResult.HANDLED
        val regions = smartBubbleRegions
        if (regions == null) {
            smartBubblePendingDirection = -1
            ensureSmartBubbleRegions()
            return SmartBubbleNavigationResult.HANDLED
        }
        if (regions.isEmpty()) return SmartBubbleNavigationResult.PAGE_COMPLETE

        if (reanchorSmartBubbleToViewport(regions)) return SmartBubbleNavigationResult.HANDLED

        val target = if (smartBubbleIndex < 0) regions.lastIndex else smartBubbleIndex - 1
        if (target < 0) return SmartBubbleNavigationResult.PAGE_COMPLETE

        smartBubbleIndex = target
        transitionSmartBubble(target)
        return SmartBubbleNavigationResult.HANDLED
    }

    /**
     * Gives the reader a very brief full-page context beat between dialogue stops, then eases into
     * the next target. This keeps the artwork/panel geography visible instead of making Smart Bubble
     * feel like a sequence of disconnected OCR crops.
     */
    private fun transitionSmartBubble(index: Int) {
        val view = pageView as? SubsamplingScaleImageView
        if (view == null || !view.isReady || view.sWidth <= 0 || view.sHeight <= 0) {
            focusSmartBubble(index)
            return
        }

        val generation = ++smartBubbleTransitionGeneration
        smartBubbleTransitionInProgress = true
        val pageCenter = PointF(view.sWidth / 2f, view.sHeight / 2f)

        view
            .animateScaleAndCenter(view.minScale, pageCenter)
            ?.withDuration(SMART_BUBBLE_OVERVIEW_OUT_MS)
            ?.withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
            ?.withInterruptible(true)
            ?.start()

        view.postDelayed(
            {
                if (
                    generation != smartBubbleTransitionGeneration ||
                    !viewer.smartBubbleEnabled ||
                    !viewer.isCurrentSmartBubblePage(page)
                ) {
                    smartBubbleTransitionInProgress = false
                    return@postDelayed
                }

                focusSmartBubble(index)
                view.postDelayed(
                    {
                        if (generation == smartBubbleTransitionGeneration) {
                            smartBubbleTransitionInProgress = false
                        }
                    },
                    SMART_BUBBLE_ANIMATION_MS,
                )
            },
            SMART_BUBBLE_OVERVIEW_OUT_MS + SMART_BUBBLE_OVERVIEW_HOLD_MS,
        )
    }

    /**
     * Re-syncs Smart Bubble with where the reader has manually moved the page.
     *
     * Smart Bubble animations center the viewport on a detected region. If the current viewport is
     * now clearly closer to a different region, that means the user has panned/zoomed away from the
     * previous target. The next navigation tap first adopts that nearby bubble, then subsequent taps
     * continue through the normal reading order.
     */
    private fun reanchorSmartBubbleToViewport(regions: List<RectF>): Boolean {
        val view = pageView as? SubsamplingScaleImageView ?: return false
        if (!view.isReady || view.sWidth <= 0 || view.sHeight <= 0) return false

        val sourceCenter = view.center ?: return false
        val pointX = (sourceCenter.x / view.sWidth).coerceIn(0f, 1f)
        val pointY = (sourceCenter.y / view.sHeight).coerceIn(0f, 1f)

        // V1.3 treats a meaningful manual pan as an explicit request to resume from that part of
        // the page. This is more reliable than comparing the viewport only with the current
        // bubble, which could fail when several balloons are close together.
        val lastFocused = smartBubbleLastFocusedCenter ?: return false
        val movedX = pointX - lastFocused.x
        val movedY = pointY - lastFocused.y
        val movedSquared = movedX * movedX + movedY * movedY
        if (movedSquared < SMART_BUBBLE_REANCHOR_MOVEMENT_SQUARED) return false

        val nearestIndex =
            regions.indices.minByOrNull { index ->
                distanceToRectSquared(pointX, pointY, regions[index])
            } ?: return false

        // Re-focus the closest bubble to where the user moved the page. The next tap then continues
        // normally from this new anchor, including jumping back to the first/top bubble.
        smartBubbleIndex = nearestIndex
        focusSmartBubble(nearestIndex)
        return true
    }

    private fun distanceToRectSquared(
        x: Float,
        y: Float,
        rect: RectF,
    ): Float {
        val dx =
            when {
                x < rect.left -> rect.left - x
                x > rect.right -> x - rect.right
                else -> 0f
            }
        val dy =
            when {
                y < rect.top -> rect.top - y
                y > rect.bottom -> y - rect.bottom
                else -> 0f
            }
        return dx * dx + dy * dy
    }

    private fun supportsSmartBubble(): Boolean =
        extraPage == null &&
            page.firstHalf == null &&
            page.stream != null &&
            pageView !is androidx.appcompat.widget.AppCompatImageView

    private fun ensureSmartBubbleRegions() {
        if (smartBubbleRegions != null || smartBubbleScanJob?.isActive == true) return
        // A neighbour holder may be attached before its stream finishes loading. Do not cache
        // that transient state as an empty result; onImageLoaded() will retry the pre-analysis.
        val streamProvider = page.stream ?: return

        smartBubbleScanJob =
            scope.launch(IO) {
                val regions =
                    runCatching {
                        SmartBubbleAnalyzer.analyze(streamProvider)
                    }.getOrElse {
                        Timber.w(it, "Smart Bubble scan failed for page ${page.index}")
                        emptyList()
                    }

                withContext(Dispatchers.Main) {
                    smartBubbleRegions = regions
                    smartBubbleScanJob = null

                    if (!viewer.isCurrentSmartBubblePage(page) || regions.isEmpty()) {
                        return@withContext
                    }

                    val direction = smartBubblePendingDirection ?: return@withContext
                    smartBubblePendingDirection = null
                    smartBubbleIndex = if (direction > 0) 0 else regions.lastIndex
                    focusSmartBubble(smartBubbleIndex)
                }
            }
    }

    private fun focusSmartBubble(index: Int) {
        val region = smartBubbleRegions?.getOrNull(index) ?: return
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (!view.isReady || view.sWidth <= 0 || view.sHeight <= 0 || view.width <= 0 || view.height <= 0) {
            smartBubblePendingDirection = if (index <= 0) 1 else -1
            return
        }

        val sourceWidth = view.sWidth.toFloat()
        val sourceHeight = view.sHeight.toFloat()
        val regionWidth = (region.width() * sourceWidth).coerceAtLeast(1f)
        val regionHeight = (region.height() * sourceHeight).coerceAtLeast(1f)
        val fitWidth = view.width * SMART_BUBBLE_WIDTH_FILL / regionWidth
        val fitHeight = view.height * SMART_BUBBLE_HEIGHT_FILL / regionHeight
        val upperScale = min(view.maxScale, view.minScale * SMART_BUBBLE_MAX_ZOOM)
        val lowerScale = min(upperScale, view.minScale * SMART_BUBBLE_MIN_ZOOM)
        val targetScale = min(fitWidth, fitHeight).coerceIn(lowerScale, upperScale)

        // Keep the whole target comfortably inside the viewport instead of blindly centring it.
        // This particularly matters for the first balloon near the top edge, where centring the
        // detected text block can otherwise crop the top of the speech balloon.
        val viewportSourceWidth = view.width / targetScale
        val viewportSourceHeight = view.height / targetScale
        val halfViewportWidth = viewportSourceWidth / 2f
        val halfViewportHeight = viewportSourceHeight / 2f
        val marginX = viewportSourceWidth * SMART_BUBBLE_CONTEXT_MARGIN_X
        val marginY = viewportSourceHeight * SMART_BUBBLE_CONTEXT_MARGIN_Y

        val regionLeft = region.left * sourceWidth
        val regionRight = region.right * sourceWidth
        val regionTop = region.top * sourceHeight
        val regionBottom = region.bottom * sourceHeight

        var centerX = region.centerX() * sourceWidth
        var centerY = region.centerY() * sourceHeight

        val minCenterX = regionRight + marginX - halfViewportWidth
        val maxCenterX = regionLeft - marginX + halfViewportWidth
        if (minCenterX <= maxCenterX) centerX = centerX.coerceIn(minCenterX, maxCenterX)

        val minCenterY = regionBottom + marginY - halfViewportHeight
        val maxCenterY = regionTop - marginY + halfViewportHeight
        if (minCenterY <= maxCenterY) centerY = centerY.coerceIn(minCenterY, maxCenterY)

        // Explicit edge clamping makes the first/last target show the page edge rather than
        // letting the detected text block pull the viewport beyond it.
        centerX = centerX.coerceIn(halfViewportWidth, (sourceWidth - halfViewportWidth).coerceAtLeast(halfViewportWidth))
        centerY = centerY.coerceIn(halfViewportHeight, (sourceHeight - halfViewportHeight).coerceAtLeast(halfViewportHeight))

        if (index == 0 && region.top <= SMART_BUBBLE_TOP_EDGE_REGION_THRESHOLD) {
            centerY = halfViewportHeight.coerceAtMost(sourceHeight / 2f)
        }

        val center = PointF(centerX, centerY)
        smartBubbleLastFocusedCenter =
            PointF(
                (centerX / sourceWidth).coerceIn(0f, 1f),
                (centerY / sourceHeight).coerceIn(0f, 1f),
            )

        view
            .animateScaleAndCenter(targetScale, center)
            ?.withDuration(SMART_BUBBLE_ANIMATION_MS)
            ?.withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
            ?.withInterruptible(true)
            ?.start()
    }

    /** Check if the image can be panned to the left */
    fun canPanLeft(): Boolean = canPan { it.left }

    /** Check if the image can be panned to the right */
    fun canPanRight(): Boolean = canPan { it.right }

    /** Check if the image can be panned upward */
    fun canPanUp(): Boolean = canPan { it.top }

    /** Check if the image can be panned downward */
    fun canPanDown(): Boolean = canPan { it.bottom }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 0.01f
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Whether the image is currently zoomed in past its default scale.
     */
    fun isZoomedIn(): Boolean {
        val view = pageView as? SubsamplingScaleImageView ?: return false
        return view.scale > view.minScale + 0.01f
    }

    /**
     * Re-applies cutout insets, e.g. after entering/exiting multi-window mode, without
     * reloading the page or resetting its zoom/pan position.
     */
    fun refreshCutoutInsets() {
        if (status != Page.State.READY) return
        refreshCutoutInsets(imageConfig)
    }

    /**
     * Zooms the image in by one step, e.g. from a gamepad or keyboard press.
     */
    fun zoomIn() = animateZoomBy(ZOOM_STEP)

    /**
     * Zooms the image out by one step, e.g. from a gamepad or keyboard press.
     */
    fun zoomOut() = animateZoomBy(1 / ZOOM_STEP)

    private fun animateZoomBy(factor: Float) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            val target = (view.scale * factor).coerceIn(view.minScale, view.maxScale)
            view
                .animateScale(target)!!
                .withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
                .withDuration(200)
                .withInterruptible(true)
                .start()
        }
    }

    /**
     * Applies one non-animated zoom step scaled by [rate] (-1 = fastest zoom out, 1 = fastest
     * zoom in). Meant to be called repeatedly (e.g. every frame) while a zoom input is held,
     * such as the L2/R2 trigger axes or right stick.
     */
    fun zoomBy(rate: Float) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            val center = view.center ?: return
            val factor = 1f + (ZOOM_HOLD_FACTOR - 1f) * rate.coerceIn(-1f, 1f)
            val target = (view.scale * factor).coerceIn(view.minScale, view.maxScale)
            view.setScaleAndCenter(target, center)
        }
    }

    /**
     * Pans the image by a fraction of its width/height, used for gamepad/keyboard panning
     * while the image is zoomed in.
     */
    fun panBy(
        dxRatio: Float,
        dyRatio: Float,
    ) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            if (view.scale <= view.minScale) return
            val center = view.center ?: return
            val target =
                PointF(
                    center.x + view.width * dxRatio / view.scale,
                    center.y + view.height * dyRatio / view.scale,
                )
            view
                .animateCenter(target)!!
                .withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
                .withDuration(100)
                .withInterruptible(true)
                .start()
        }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            val target = fn(view.center ?: return, view)
            view
                .animateCenter(target)!!
                .withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean?) {
        forward ?: return
        if (viewer.config.landscapeZoom &&
            viewer.config.imageScaleType == SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler.postDelayed(
                {
                    val point =
                        when (viewer.config.imageZoomType) {
                            ZoomType.Left -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                            ZoomType.Right -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                            ZoomType.Center -> center.also { it?.y = 0F }
                        }

                    val rootInsets = viewer.activity.window.decorView.rootWindowInsets
                    val topInsets =
                        if (viewer.activity.isInMultiWindowMode) {
                            0f
                        } else {
                            rootInsets?.topCutoutInset()?.toFloat() ?: 0f
                        }
                    val bottomInsets =
                        if (viewer.activity.isInMultiWindowMode) {
                            0f
                        } else {
                            rootInsets?.bottomCutoutInset()?.toFloat() ?: 0f
                        }
                    val targetScale = (height.toFloat() - topInsets - bottomInsets) / sHeight.toFloat()
                    animateScaleAndCenter(min(targetScale, minScale * 2), point)!!
                        .withDuration(500)
                        .withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
                        .withInterruptible(true)
                        .start()
                },
                500,
            )
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Page.State) {
        when (status) {
            Page.State.QUEUE -> setQueued()
            Page.State.LOAD_PAGE -> setLoading()
            Page.State.DOWNLOAD_IMAGE -> {
                launchProgressJob()
                setDownloading()
            }
            Page.State.READY -> {
                if (extraStatus == Page.State.READY || extraPage == null) {
                    setImage()
                }
                cancelProgressJob(1)
            }
            Page.State.ERROR -> {
                setError()
                cancelProgressJob(1)
            }
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus2(status: Page.State) {
        when (status) {
            Page.State.QUEUE -> setQueued()
            Page.State.LOAD_PAGE -> setLoading()
            Page.State.DOWNLOAD_IMAGE -> {
                launchProgressJob2()
                setDownloading()
            }
            Page.State.READY -> {
                if (this.status == Page.State.READY) {
                    setImage()
                }
                cancelProgressJob(2)
            }
            Page.State.ERROR -> {
                setError()
                cancelProgressJob(2)
            }
        }
    }

    /**
     * Cancels loading the page and processing changes to the page's status.
     */
    private fun cancelLoadJob(page: Int) {
        if (page == 1) {
            loadJob?.cancel()
            loadJob = null
            statusJob?.cancel()
            statusJob = null
        } else {
            extraLoadJob?.cancel()
            extraLoadJob = null
            extraStatusJob?.cancel()
            extraStatusJob = null
        }
    }

    private fun cancelProgressJob(page: Int) {
        (if (page == 1) progressJob else extraProgressJob)?.cancel()
        if (page == 1) {
            progressJob = null
        } else {
            extraProgressJob = null
        }
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun cancelReadImageHeader() {
        readImageHeaderJob?.cancel()
        readImageHeaderJob = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressBar.isVisible = true
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressBar.isVisible = true
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        progressBar.isVisible = true
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressBar.isVisible = true
        if (extraPage == null) {
            setProgress(100)
        } else {
            setProgress(95)
        }
        errorLayout?.isVisible = false

        cancelReadImageHeader()

        readImageHeaderJob =
            scope.launchIO {
                val streamFn = page.stream ?: return@launchIO
                val streamFn2 = extraPage?.stream

                var openStream: InputStream? = null
                try {
                    val stream = streamFn().buffered(16)

                    val stream2 = streamFn2?.invoke()?.buffered(16)
                    openStream = this@PagerPageHolder.mergeOrSplitPages(stream, stream2)
                    val isAnimated =
                        ImageUtil.isAnimatedAndSupported(stream) ||
                            (stream2?.let { ImageUtil.isAnimatedAndSupported(stream2) } ?: false)
                    withUIContext {
                        val bgColor = ReaderBackgroundColor.fromPreference(viewer.config.readerTheme)
                        if (!isAnimated) {
                            if (bgColor.isSmartColor) {
                                val bgType = getBGType(viewer.config.readerTheme, context)
                                if (page.bg != null && page.bgType == bgType) {
                                    setImage(openStream, false, imageConfig)
                                    pageView?.background = page.bg
                                }
                                // if the user switches to automatic when pages are already cached, the bg needs to be loaded
                                else {
                                    val bytesArray = openStream.readBytes()
                                    val bytesStream = bytesArray.inputStream()
                                    setImage(bytesStream, false, imageConfig)
                                    closeStreams(bytesStream)

                                    try {
                                        pageView?.background = setBG(bytesArray)
                                    } catch (e: Exception) {
                                        Timber.e(e.localizedMessage)
                                        pageView?.background = ColorDrawable(Color.WHITE)
                                    } finally {
                                        page.bg = pageView?.background
                                        page.bgType = bgType
                                    }
                                }
                            } else {
                                setImage(openStream, false, imageConfig)
                            }
                        } else {
                            setImage(openStream, true, imageConfig)
                            if (bgColor.isSmartColor && page.bg != null) {
                                pageView?.background = page.bg
                            }
                        }
                    }
                } catch (_: Exception) {
                    try {
                        openStream?.let { closeStreams(it) }
                    } catch (_: Exception) {
                    }
                }
            }
    }

    private val imageConfig: Config
        get() =
            Config(
                zoomDuration = viewer.config.doubleTapAnimDuration,
                minimumScaleType = viewer.config.imageScaleType,
                cropBorders = viewer.config.imageCropBorders,
                zoomStartPosition = viewer.config.imageZoomType,
                landscapeZoom = viewer.config.landscapeZoom,
                insetInfo =
                    InsetInfo(
                        cutoutBehavior = viewer.config.cutoutBehavior,
                        topCutoutInset =
                            viewer.activity.window.decorView.rootWindowInsets
                                ?.topCutoutInset()
                                ?.toFloat() ?: 0f,
                        bottomCutoutInset =
                            viewer.activity.window.decorView.rootWindowInsets
                                ?.bottomCutoutInset()
                                ?.toFloat() ?: 0f,
                        isFullscreen =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                viewer.config.isFullscreen &&
                                !viewer.activity.isInMultiWindowMode,
                        insets = viewer.activity.window.decorView.rootWindowInsets,
                    ),
                hingeGapSize = viewer.config.hingeGapSize,
            )

    private suspend fun setBG(bytesArray: ByteArray): Drawable =
        withContext(Default) {
            val preferences by injectLazy<PreferencesHelper>()
            ImageUtil.autoSetBackground(
                BitmapFactory.decodeByteArray(
                    bytesArray,
                    0,
                    bytesArray.size,
                ),
                preferences.readerTheme().get() == 2,
                context,
            )
        }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressBar.isVisible = false
        showErrorLayout(false)
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressBar.isVisible = false
    }

    /**
     * Called when an image fails to decode.
     */
    private fun onImageDecodeError() {
        progressBar.isVisible = false
        showErrorLayout(true)
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressBar(): CircularProgressIndicator =
        CircularProgressIndicator(context).apply {
            isIndeterminate = false
            setWavelength(10.dpToPx)
            progress = 10
            waveAmplitude = 1.dpToPx
            waveSpeed = 20
            val size = 48.dpToPx
            layoutParams =
                LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
        }

    private fun isInvertedFromTheme(): Boolean =
        when (backgroundColor) {
            Color.WHITE -> context.isInNightMode()
            Color.BLACK -> !context.isInNightMode()
            else -> false
        }

    private fun showErrorLayout(withOpenInWebView: Boolean): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true).root
            errorLayout?.viewer = viewer
            errorLayout?.binding?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }
        val imageUrl =
            if (withOpenInWebView) {
                page.imageUrl
            } else {
                viewer.activity.viewModel.getChapterUrl(page.chapter.chapter)
            }
        return errorLayout!!.configureView(imageUrl)
    }

    private suspend fun mergeOrSplitPages(
        imageStream: InputStream,
        imageStream2: InputStream?,
    ): InputStream {
        if (ImageUtil.isAnimatedAndSupported(imageStream)) {
            withContext(Dispatchers.IO) { imageStream.reset() }
            if (page.longPage == null) {
                page.longPage = true
                if (viewer.config.splitPages || imageStream2 != null) {
                    splitDoublePages()
                }
            }
            scope.launchUI { setProgress(100) }
            return imageStream
        }
        if (page.longPage == true && viewer.config.splitPages) {
            val imageBytes = imageStream.readBytes()
            val imageBitmap =
                try {
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                } catch (e: Exception) {
                    closeStreams(imageStream)
                    Timber.e("Cannot split page ${e.message}")
                    return imageBytes.inputStream()
                }
            val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
            return ImageUtil.splitBitmap(imageBitmap, (page.firstHalf == false).xor(!isLTR)) {
                scope.launchUI {
                    if (it == 100) {
                        setProgress(100)
                    } else {
                        setProgress(it)
                    }
                }
            }
        }
        if (imageStream2 == null) {
            if (viewer.config.splitPages && page.longPage == null) {
                val imageBytes = imageStream.readBytes()
                val imageBitmap =
                    try {
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    } catch (e: Exception) {
                        closeStreams(imageStream)
                        page.longPage = true
                        splitDoublePages()
                        Timber.e("Cannot split page ${e.message}")
                        return imageBytes.inputStream()
                    }
                val height = imageBitmap.height
                val width = imageBitmap.width
                return if (height < width) {
                    closeStreams(imageStream)
                    page.longPage = true
                    splitDoublePages()
                    val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
                    return ImageUtil.splitBitmap(imageBitmap, !isLTR) {
                        scope.launchUI {
                            if (it == 100) {
                                setProgress(100)
                            } else {
                                setProgress(it)
                            }
                        }
                    }
                } else {
                    page.longPage = false
                    imageBytes.inputStream()
                }
            }
            return supportHingeIfThere(imageStream)
        }
        if (page.fullPage == true) return supportHingeIfThere(imageStream)
        val imageBytes = imageStream.readBytes()
        val imageBitmap =
            try {
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                closeStreams(imageStream, imageStream2)
                page.fullPage = true
                splitDoublePages()
                Timber.e("Cannot combine pages ${e.message}")
                return supportHingeIfThere(imageBytes.inputStream())
            }
        scope.launchUI { setProgress(96) }
        val height = imageBitmap.height
        val width = imageBitmap.width

        val imageBytes2 by lazy { imageStream2.readBytes() }
        val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)

        val pages = page.chapter.pages
        if (height < width) {
            if (extraPage?.index == 1) {
                setExtraPageBitmap(imageBytes2, isLTR)
            }
            closeStreams(imageStream, imageStream2)
            val oldValue = page.fullPage
            page.fullPage = true
            delayPageUpdate {
                val thirdPageIsStart = pages?.getOrNull(2)?.isStartPage == true
                val extraPageIsEnd = extraPage?.isEndPage == true
                if (page.index == 0 &&
                    (
                        (viewer.config.shiftDoublePage && !thirdPageIsStart) ||
                            extraPage?.isEndPage == true
                    ) &&
                    oldValue != true
                ) {
                    viewer.activity.shiftDoublePages(extraPageIsEnd || thirdPageIsStart, extraPage)
                } else {
                    viewer.splitDoublePages(page)
                }
                extraPage = null
            }
            return supportHingeIfThere(imageBytes.inputStream())
        }
        val isNotEndPage: ReaderPage.() -> Boolean =
            { isEndPage != true || (page.endPageConfidence ?: 0) > (endPageConfidence ?: 0) }
        var earlyImageBitmap2: Bitmap? = null
        val isFirstPageNotEnd by lazy { pages?.get(0)?.let { it.isNotEndPage() } != false }
        val isThirdPageNotEnd by lazy { pages?.getOrNull(2)?.let { it.isNotEndPage() } == true }
        val shouldShiftAnyway = !viewer.activity.manuallyShiftedPages && page.endPageConfidence == 3
        if (page.index <= 2 && page.isEndPage == null && page.fullPage == null) {
            page.endPageConfidence = imageBitmap.isPagePadded(rightSide = !isLTR)
            if (extraPage?.index == 1 && extraPage?.isEndPage == null) {
                earlyImageBitmap2 = setExtraPageBitmap(imageBytes2, isLTR)
            }
            if (page.index == 1 &&
                page.isEndPage == true &&
                viewer.config.shiftDoublePage &&
                (isFirstPageNotEnd || isThirdPageNotEnd)
            ) {
                shiftDoublePages(false)
                return supportHingeIfThere(imageBytes.inputStream())
            } else if (page.isEndPage == true &&
                when (page.index) {
                    // 3rd page shouldn't shift if the 1st page is a spread
                    2 -> pages?.get(0)?.fullPage != true
                    // 2nd page shouldn't shift if the 1st page is more likely an end page
                    1 -> isFirstPageNotEnd
                    // 1st page shouldn't shift if the 2nd page is definitely an end page
                    0 -> extraPage?.endPageConfidence != 3 || page.endPageConfidence == 3
                    else -> false
                }
            ) {
                shiftDoublePages(true)
                extraPage = null
                return supportHingeIfThere(imageBytes.inputStream())
            }
        } else if (shouldShiftAnyway && (page.index == 0 || page.index == 2)) {
            // if for some reason the first page should be by itself but its not, fix that
            shiftDoublePages(true)
            extraPage = null
            return supportHingeIfThere(imageBytes.inputStream())
        } else if (shouldShiftAnyway &&
            page.index == 1 &&
            viewer.config.shiftDoublePage &&
            (isFirstPageNotEnd && isThirdPageNotEnd)
        ) {
            shiftDoublePages(false)
            return supportHingeIfThere(imageBytes.inputStream())
        }

        val imageBitmap2 =
            earlyImageBitmap2 ?: try {
                BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)
            } catch (e: Exception) {
                closeStreams(imageStream, imageStream2)
                extraPage?.fullPage = true
                page.isolatedPage = true
                splitDoublePages()
                Timber.e("Cannot combine pages ${e.message}")
                return supportHingeIfThere(imageBytes.inputStream())
            }
        scope.launchUI { setProgress(97) }
        val height2 = imageBitmap2.height
        val width2 = imageBitmap2.width

        if (height2 < width2) {
            closeStreams(imageStream, imageStream2)
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            return supportHingeIfThere(imageBytes.inputStream())
        }
        val bg = ThemeUtil.readerBackgroundColor(viewer.config.readerTheme)
        closeStreams(imageStream, imageStream2)
        extraPage?.let { extraPage ->
            val shouldSubShiftAnyway =
                !viewer.activity.manuallyShiftedPages &&
                    extraPage.isStartPage == true &&
                    extraPage.endPageConfidence == 0
            if (extraPage.index <= 2 &&
                extraPage.endPageConfidence != 3 &&
                extraPage.isStartPage == null &&
                extraPage.fullPage == null
            ) {
                extraPage.startPageConfidence = imageBitmap2.isPagePadded(rightSide = isLTR)
                if (extraPage.isStartPage == true) {
                    if (extraPage.endPageConfidence != null) {
                        extraPage.endPageConfidence = 0
                    }
                    shiftDoublePages(page.index == 0 || pages?.get(0)?.fullPage == true)
                    this.extraPage = null
                    return supportHingeIfThere(imageBytes.inputStream())
                }
            } else if (shouldSubShiftAnyway && page.index == 1 && !viewer.config.shiftDoublePage) {
                shiftDoublePages(true)
                return supportHingeIfThere(imageBytes.inputStream())
            }
        }
        // If page has been removed in another thread, don't show it
        if (extraPage == null) {
            return supportHingeIfThere(imageBytes.inputStream())
        }
        return ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg, viewer.config.hingeGapSize, context) {
            scope.launchUI {
                if (it == 100) {
                    setProgress(100)
                } else {
                    setProgress(it)
                }
            }
        }
    }

    fun setProgress(progress: Int) {
        val scaledProgress = 85 * progress / 100 + 10
        progressBar.setProgress(scaledProgress, true)
    }

    private fun setExtraPageBitmap(
        imageBytes2: ByteArray,
        isLTR: Boolean,
    ): Bitmap? {
        val earlyImageBitmap2 =
            try {
                BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)
            } catch (_: Exception) {
                return null
            }
        val paddedPageConfidence = earlyImageBitmap2.isPagePadded(rightSide = !isLTR)
        if (paddedPageConfidence == 3) {
            extraPage?.endPageConfidence = paddedPageConfidence
        }
        return earlyImageBitmap2
    }

    private suspend fun supportHingeIfThere(imageStream: InputStream): InputStream {
        if (viewer.config.hingeGapSize > 0 && !ImageUtil.isAnimatedAndSupported(imageStream)) {
            val imageBytes = imageStream.readBytes()
            val imageBitmap =
                try {
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                } catch (e: Exception) {
                    closeStreams(imageStream)
                    val wasNotFullPage = page.fullPage != true
                    page.fullPage = true
                    if (wasNotFullPage) {
                        splitDoublePages()
                    }
                    return imageBytes.inputStream()
                }
            val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
            val bg = ThemeUtil.readerBackgroundColor(viewer.config.readerTheme)
            return ImageUtil.padSingleImage(
                imageBitmap = imageBitmap,
                isLTR = isLTR,
                atBeginning = if (viewer.config.doublePages) page.index == 0 else null,
                background = bg,
                hingeGap = viewer.config.hingeGapSize,
                context = context,
            )
        }
        return imageStream
    }

    private suspend fun closeStreams(
        stream1: InputStream?,
        stream2: InputStream? = null,
    ) {
        withContext(Dispatchers.IO) {
            stream1?.close()
            stream2?.close()
        }
    }

    private fun shiftDoublePages(shift: Boolean) {
        delayPageUpdate { viewer.activity.shiftDoublePages(shift, page) }
    }

    private fun splitDoublePages() {
        delayPageUpdate { viewer.splitDoublePages(page) }
    }

    private fun delayPageUpdate(callback: () -> Unit) {
        scope.launchUI {
            callback()
            if (extraPage?.fullPage == true || page.fullPage == true) {
                extraPage = null
            }
        }
    }

    private fun getBGType(
        readerTheme: Int,
        context: Context,
    ): Int =
        if (ReaderBackgroundColor.fromPreference(readerTheme) == ReaderBackgroundColor.SMART_THEME) {
            if (context.isInNightMode()) 2 else 1
        } else {
            0 + (context.resources.configuration?.orientation ?: 0) * 10
        } + item.hashCode()
}

private const val ZOOM_STEP = 1.25f

private const val SMART_BUBBLE_ANIMATION_MS = 280L
private const val SMART_BUBBLE_OVERVIEW_OUT_MS = 180L
private const val SMART_BUBBLE_PAGE_OVERVIEW_MS = 220L
private const val SMART_BUBBLE_OVERVIEW_HOLD_MS = 180L
private const val SMART_BUBBLE_WIDTH_FILL = 0.58f
private const val SMART_BUBBLE_HEIGHT_FILL = 0.48f
private const val SMART_BUBBLE_MIN_ZOOM = 1.03f
private const val SMART_BUBBLE_MAX_ZOOM = 2.25f
private const val SMART_BUBBLE_CONTEXT_MARGIN_X = 0.08f
private const val SMART_BUBBLE_CONTEXT_MARGIN_Y = 0.10f
private const val SMART_BUBBLE_TOP_EDGE_REGION_THRESHOLD = 0.18f
private const val SMART_BUBBLE_REANCHOR_MOVEMENT_SQUARED = 0.0025f

/** Per-tick scale multiplier at full rate for [PagerPageHolder.zoomBy]'s held zoom. */
private const val ZOOM_HOLD_FACTOR = 1.035f
