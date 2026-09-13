package eu.kanade.tachiyomi.ui.base

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import androidx.core.view.ScrollingView
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Controller
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.setTextColorAlpha
import eu.kanade.tachiyomi.widget.StatefulNestedScrollView
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class
ExpandedAppBarLayout@JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : AppBarLayout(context, attrs) {
        var searchToolbar: FloatingToolbar? = null
        private var librarySearchBar: View? = null
        var cardFrame: LinearLayout? = null
        var toolbarFilterButton: MaterialButton? = null
        private var cardView: MaterialCardView? = null
        private var cardShadowAnimator: ValueAnimator? = null
        private var showingCardShadow = false
        var mainToolbar: CenteredToolbar? = null
        var bigTitleView: TextView? = null
        var bigSubtitleView: TextView? = null
        val preferences: PreferencesHelper by injectLazy()
        var bigView: View? = null
        var imageView: ImageView? = null
        var imageLayout: FrameLayout? = null
        private var tabsFrameLayout: FrameLayout? = null
        var mainActivity: MainActivity? = null

        // J2K frequently reapplies colorSurface to the AppBar while toolbars animate.
        // For the Modern Library screen we draw the gradient ourselves in onDraw(),
        // after any solid background but before child toolbars/tabs. This means theme
        // background changes can no longer cover the custom Library gradient.
        private var modernLibraryGradientDrawable: Drawable? = null
        private var inkShelfHeaderBackgroundDrawable: Drawable? = null
        var modernLibraryGradientEnabled: Boolean = false
            set(value) {
                field = value
                if (value && modernLibraryGradientDrawable == null) {
                    modernLibraryGradientDrawable =
                        ContextCompat.getDrawable(context, R.drawable.modern_library_theme_background)
                }
                setWillNotDraw(false)
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            if (modernLibraryGradientEnabled) {
                modernLibraryGradientDrawable?.let { drawable ->
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                }
            }
            if (mainToolbarSuppressed) {
                if (inkShelfHeaderBackgroundDrawable == null) {
                    inkShelfHeaderBackgroundDrawable =
                        ContextCompat.getDrawable(context, R.drawable.inkshelf_library_header_bg)
                }
                inkShelfHeaderBackgroundDrawable?.let { drawable ->
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                }
            }
            super.onDraw(canvas)
        }

        private var isExtraSmall = false
        val useLargeToolbar: Boolean
            get() = if (mainToolbarSuppressed) true else preferences.useLargeToolbar().get() && !isExtraSmall

        var compactSearchMode = false

        /**
         * InkShelf screens can provide their own header. In that mode the shared J2K compact
         * toolbar is removed from both the view hierarchy and all app-bar height calculations.
         * Other J2K screens keep the original toolbar unchanged.
         */
        var mainToolbarSuppressed = false
            private set

        fun setMainToolbarSuppressed(suppressed: Boolean) {
            if (mainToolbarSuppressed == suppressed) return
            mainToolbarSuppressed = suppressed
            if (suppressed) {
                mainToolbar?.isGone = true
                mainToolbar?.alpha = 0f
            } else if (toolbarMode != ToolbarState.SEARCH_ONLY) {
                mainToolbar?.alpha = 1f
                mainToolbar?.isVisible = true
            }
            requestLayout()
            invalidate()
        }

        private val effectiveMainToolbarHeight: Int
            get() = if (mainToolbarSuppressed) 0 else (mainToolbar?.height ?: attrToolbarHeight)

        /** Defines how the toolbar layout should be */
        private var toolbarMode = ToolbarState.EXPANDED
            set(value) {
                field = value
                if (value == ToolbarState.SEARCH_ONLY) {
                    mainToolbar?.isGone = true
                } else if (value == ToolbarState.COMPACT) {
                    if (mainToolbarSuppressed) {
                        mainToolbar?.isGone = true
                        mainToolbar?.alpha = 0f
                    } else {
                        mainToolbar?.alpha = 1f
                        mainToolbar?.isVisible = true
                    }
                }
                if (value != ToolbarState.EXPANDED) {
                    mainToolbar?.translationY = 0f
                    y = 0f
                }
            }
        var useTabsInPreLayout = false
        var yAnimator: ViewPropertyAnimator? = null

        /**
         * used to ignore updates to y
         *
         * use only on controller.onViewCreated that asynchronously loads the first set of items
         * and make false once the recycler has items
         */
        var lockYPos = false

        /** A value used to determine the offset needed for a recycler to land just under the smaller toolbar */
        val toolbarDistanceToTop: Int
            get() {
                val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
                return paddingTop - effectiveMainToolbarHeight - tabHeight
            }

        /** A value used to determine the offset needed for a appbar's y to show only the smaller toolbar */
        val yNeededForSmallToolbar: Int
            get() {
                if (toolbarMode != ToolbarState.EXPANDED) return 0
                val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
                return -preLayoutHeight + effectiveMainToolbarHeight + tabHeight
            }

        val attrToolbarHeight: Int =
            let {
                val attrsArray = intArrayOf(R.attr.mainActionBarSize)
                val array = it.context.obtainStyledAttributes(attrsArray)
                val height = array.getDimensionPixelSize(0, 0)
                array.recycle()
                height
            }

        val preLayoutHeight: Int
            get() =
                getEstimatedLayout(
                    cardFrame?.isVisible == true && toolbarMode == ToolbarState.EXPANDED,
                    useTabsInPreLayout,
                    toolbarMode == ToolbarState.EXPANDED,
                )

        private val preLayoutHeightWhileSearching: Int
            get() =
                getEstimatedLayout(
                    cardFrame?.isVisible == true && toolbarMode == ToolbarState.EXPANDED,
                    useTabsInPreLayout,
                    toolbarMode == ToolbarState.EXPANDED,
                    true,
                )

        private var dontFullyHideToolbar = false

        /** Small toolbar height + top system insets, same size as a collapsed appbar */
        private val compactAppBarHeight: Float
            get() {
                val appBarHeight = effectiveMainToolbarHeight
                return (appBarHeight + paddingTop).toFloat()
            }

        /** Used to restrain how far up the app bar can go up. Tablets stop at the smaller toolbar */
        private val minTabletHeight: Int
            get() {
                val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
                return if (context.isTablet() || (compactSearchMode && toolbarMode == ToolbarState.EXPANDED)) {
                    effectiveMainToolbarHeight + paddingTop + tabHeight
                } else {
                    0
                }
            }

        enum class ToolbarState {
            EXPANDED,
            COMPACT,
            SEARCH_ONLY,
        }

        fun setToolbarModeBy(
            controller: Controller?,
            useSmall: Boolean? = null,
        ) {
            toolbarMode =
                if (useSmall ?: !useLargeToolbar) {
                    when {
                        controller is FloatingSearchInterface && controller.showFloatingBar() -> {
                            ToolbarState.SEARCH_ONLY
                        }
                        else -> ToolbarState.COMPACT
                    }
                } else {
                    when (controller) {
                        is SmallToolbarInterface -> {
                            if (controller is FloatingSearchInterface && controller.showFloatingBar()) {
                                ToolbarState.SEARCH_ONLY
                            } else {
                                ToolbarState.COMPACT
                            }
                        }
                        else -> ToolbarState.EXPANDED
                    }
                }
            if (mainToolbarSuppressed) {
                mainToolbar?.isGone = true
                mainToolbar?.alpha = 0f
            }
            animateCardShadow(mainActivity?.currentToolbar == searchToolbar && tabsFrameLayout?.isVisible != true)
        }

        fun hideBigView(
            useSmall: Boolean,
            force: Boolean? = null,
            setTitleAlpha: Boolean = true,
        ) {
            val useSmallAnyway = force ?: (useSmall || !useLargeToolbar)
            bigView?.isGone = useSmallAnyway
            if (useSmallAnyway) {
                mainToolbar?.backgroundColor = null
                if (!setTitleAlpha) return
                mainToolbar?.toolbarTitle?.setTextColorAlpha(255)
            }
        }

        override fun onFinishInflate() {
            super.onFinishInflate()
            bigTitleView = findViewById(R.id.big_title)
            bigSubtitleView = findViewById(R.id.big_subtitle)
            searchToolbar = findViewById(R.id.search_toolbar)
            librarySearchBar = findViewById(R.id.library_search_bar)
            cardView = findViewById(R.id.card_view)
            mainToolbar = findViewById(R.id.toolbar)
            bigView = findViewById(R.id.big_toolbar)
            cardFrame = findViewById(R.id.card_frame)
            toolbarFilterButton = findViewById(R.id.toolbar_filter_button)
            tabsFrameLayout = findViewById(R.id.tabs_frame_layout)
            imageView = findViewById(R.id.big_icon)
            imageLayout = findViewById(R.id.big_icon_layout)
            shrinkAppBarIfNeeded(resources.configuration)
        }

        fun setTitle(
            title: CharSequence?,
            setBigTitle: Boolean,
        ) {
            if (setBigTitle) {
                bigTitleView?.text = title
            }
            mainToolbar?.title = title
        }

        override fun setTranslationY(translationY: Float) {
            if (lockYPos) return
            val realHeight = (preLayoutHeightWhileSearching + paddingTop).toFloat()
            val baseY =
                if (dontFullyHideToolbar && !useLargeToolbar) {
                    0f
                } else {
                    MathUtils.clamp(
                        translationY,
                        -realHeight + (if (context.isTablet()) minTabletHeight else 0),
                        if (compactSearchMode && toolbarMode == ToolbarState.EXPANDED) -realHeight + top + minTabletHeight else 0f,
                    )
                }

            // On the root InkShelf Library screen, collapse the cosmic artwork normally but
            // stop once the real outlined Library search field reaches the status-bar inset.
            // This keeps the complete search box (and the tabs beneath it) pinned while reading
            // farther down the Library. Other InkShelf roots are unaffected because this view
            // is only visible on the root Library screen.
            val pinnedLibrarySearchY =
                librarySearchBar
                    ?.takeIf { mainToolbarSuppressed && it.isVisible && it.height > 0 }
                    ?.let { search ->
                        (paddingTop - search.top)
                            .coerceAtMost(0)
                            .toFloat()
                    }

            val newY =
                if (pinnedLibrarySearchY != null) {
                    max(baseY, pinnedLibrarySearchY)
                } else {
                    baseY
                }

            super.setTranslationY(newY)

            // InkShelf's custom artwork/title live inside bigView.
            // J2K can leave bigView faded or hidden even after the outer
            // AppBar has returned to its fully-expanded y position.
            if (mainToolbarSuppressed && newY == 0f) {
                bigView?.isVisible = true
                bigView?.alpha = 1f
            }
        }

        fun getEstimatedLayout(
            includeSearchToolbar: Boolean,
            includeTabs: Boolean,
            includeLargeToolbar: Boolean,
            ignoreSearch: Boolean = false,
        ): Int {
            val hasLargeToolbar = includeLargeToolbar && useLargeToolbar && (!compactSearchMode || ignoreSearch)
            val appBarHeight =
                if (mainToolbarSuppressed) {
                    // No shared toolbar row. Reserve one row only while J2K's real search field is open.
                    if (includeSearchToolbar) attrToolbarHeight else 0
                } else {
                    attrToolbarHeight * (if (includeSearchToolbar && hasLargeToolbar) 2 else 1)
                }
            val widthMeasureSpec = MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST)
            val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            bigTitleView?.measure(widthMeasureSpec, heightMeasureSpec)
            bigSubtitleView?.measure(widthMeasureSpec, heightMeasureSpec)
            val subtitleHeight =
                if (bigSubtitleView?.isVisible == true) {
                    max(bigSubtitleView?.height ?: 0, bigSubtitleView?.measuredHeight ?: 0) +
                        (bigSubtitleView?.marginTop ?: 0)
                } else {
                    0
                }
            val textHeight =
                max(bigTitleView?.height ?: 0, bigTitleView?.measuredHeight ?: 0) +
                    (bigTitleView?.marginTop ?: 0) + subtitleHeight + (bigView?.paddingBottom ?: 0)
            return appBarHeight + (if (hasLargeToolbar) textHeight else 0) +
                if (includeTabs) 48.dpToPx else 0
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            shrinkAppBarIfNeeded(newConfig)
        }

        /**
         * For smaller devices, update the big view (with the large title) to be a smaller font and
         * less padding
         */
        private fun shrinkAppBarIfNeeded(config: Configuration?) {
            config ?: return
            dontFullyHideToolbar = config.smallestScreenWidthDp > 600
            val wasExtraSmall = isExtraSmall
            isExtraSmall = false
            if (config.screenHeightDp < 600) {
                val bigTitleView = bigTitleView ?: return
                isExtraSmall = config.screenWidthDp < 720
                if (isExtraSmall) {
                    if (isExtraSmall != wasExtraSmall) {
                        mainActivity?.refreshToolbarMode()
                    }
                    return
                }
                val attrs = intArrayOf(R.attr.textAppearanceHeadlineMedium)
                val ta = context.obtainStyledAttributes(attrs)
                val resId = ta.getResourceId(0, 0)
                ta.recycle()
                TextViewCompat.setTextAppearance(bigTitleView, resId)
                bigTitleView.setTextColor(context.getResourceColor(R.attr.actionBarTintColor))
                bigTitleView.updateLayoutParams<MarginLayoutParams> {
                    topMargin = 12.dpToPx
                }
                imageView?.updateLayoutParams<MarginLayoutParams> {
                    height = 48.dpToPx
                    width = 48.dpToPx
                }
                imageLayout?.updateLayoutParams<MarginLayoutParams> {
                    height = 48.dpToPx
                }
            }
            if (isExtraSmall != wasExtraSmall) {
                mainActivity?.refreshToolbarMode()
            }
        }

        /**
         * Update the views in appbar based on its current Y position
         *
         * @param recyclerOrNested used to determine how far it has scrolled down, if it has not scrolled
         * past the app bar's height, match the Y to the recyclerView's offset
         * @param cancelAnim if true, cancel the current snap animation
         */
        fun updateAppBarAfterY(
            scrollView: ScrollingView?,
            cancelAnim: Boolean = true,
        ) {
            if (cancelAnim) {
                yAnimator?.cancel()
            }
            if (lockYPos) return

            // InkShelf root screens must be fully expanded whenever their
            // scrolling content is genuinely at the top. RecyclerView can
            // report a non-zero pixel offset during its first layout even
            // though it cannot actually scroll upward.
            val scrollViewAsView = scrollView as? View
            if (mainToolbarSuppressed && scrollViewAsView?.canScrollVertically(-1) == false) {
                translationY = 0f
                bigView?.isVisible = true
                bigView?.alpha = 1f
                return
            }

            val offset = scrollView?.computeVerticalScrollOffset() ?: 0
            val bigHeight = bigView?.height ?: 0
            val realHeight = preLayoutHeightWhileSearching + paddingTop
            val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
            val shortH = if (toolbarMode != ToolbarState.EXPANDED || compactSearchMode) 0f else compactAppBarHeight
            val smallHeight = -realHeight + shortH + tabHeight
            val newY =
                when {
                    // for smaller devices, when search is active, we want to shrink the app bar and never
                    // extend it pass the compact state
                    toolbarMode == ToolbarState.EXPANDED && compactSearchMode -> {
                        MathUtils.clamp(
                            translationY,
                            -realHeight.toFloat() + top + if (context.isTablet()) minTabletHeight else 0,
                            -realHeight.toFloat() + top + minTabletHeight,
                        )
                    }
                    // for regular compact modes, no need to clamp, setTranslationY will take care of it
                    toolbarMode != ToolbarState.EXPANDED -> {
                        translationY
                    }
                    // if the recycler hasn't scrolled past the app bars height...
                    offset < realHeight - shortH - tabHeight -> {
                        -offset.toFloat()
                    }
                    else -> {
                        MathUtils.clamp(
                            translationY,
                            -realHeight.toFloat() + top + minTabletHeight,
                            max(
                                smallHeight,
                                if (offset > realHeight - shortH - tabHeight) {
                                    smallHeight
                                } else {
                                    min(
                                        -offset.toFloat(),
                                        0f,
                                    )
                                },
                            ) + top.toFloat(),
                        )
                    }
                }

            translationY = newY
            mainToolbar?.let { mainToolbar ->
                mainToolbar.translationY =
                    when {
                        toolbarMode != ToolbarState.EXPANDED -> 0f
                        -newY <= bigHeight -> max(-newY, 0f)
                        else -> bigHeight.toFloat()
                    }
            }
            if (toolbarMode != ToolbarState.EXPANDED || compactSearchMode) {
                if (compactSearchMode && toolbarMode == ToolbarState.EXPANDED) {
                    bigView?.alpha = 0f
                    mainToolbar?.alpha = 0f
                    cardFrame?.backgroundColor = null
                } else {
                    mainToolbar?.alpha = 1f
                }
                useSearchToolbarForMenu(compactSearchMode || offset > realHeight - shortH - tabHeight)
                return
            }
            // If toolbar is expanded, we want to fade out the big view, then later the main toolbar
            val alpha =
                (bigHeight + newY * 2) / (bigHeight) + 0.45f // (realHeight.toFloat() + newY * 5) / realHeight.toFloat() + .33f
            bigView?.alpha = MathUtils.clamp(if (alpha.isNaN()) 1f else alpha, 0f, 1f)
            val toolbarTextView = mainToolbar?.toolbarTitle ?: return
            toolbarTextView.setTextColorAlpha(
                (
                    MathUtils.clamp(
                        (1 - ((if (alpha.isNaN()) 1f else alpha) + 0.95f)) * 2,
                        0f,
                        1f,
                    ) * 255
                ).roundToInt(),
            )
            val mainToolbar = mainToolbar ?: return
            mainToolbar.alpha =
                MathUtils.clamp(
                    (mainToolbar.bottom + mainToolbar.translationY + y - paddingTop) / mainToolbar.height,
                    0f,
                    1f,
                )
            val mainActivity = mainActivity ?: return
            val useSearchToolbar = mainToolbar.alpha <= 0.025f
            val idle = RecyclerView.SCROLL_STATE_IDLE
            val state =
                when (scrollView) {
                    is RecyclerView -> scrollView.scrollState
                    is StatefulNestedScrollView -> if (scrollView.hasStopped) idle else RecyclerView.SCROLL_STATE_DRAGGING
                    else -> idle
                }
            if (if (useSearchToolbar) {
                    -y >= height || (state <= idle) || context.isTablet()
                } else {
                    mainActivity.currentToolbar == searchToolbar
                }
            ) {
                useSearchToolbarForMenu(useSearchToolbar)
            } else {
                animateCardShadow(mainActivity.currentToolbar == searchToolbar && tabsFrameLayout?.isVisible != true)
            }
        }

        /**
         * Snap Appbar to hide the entire appbar or show the smaller toolbar
         *
         * Only snaps if the [scrollView] has scrolled farther than the current app bar's height
         * @param callback closure updates along with snapping the appbar, use if something needs to
         * update alongside the appbar
         */
        fun snapAppBarY(
            controller: Controller?,
            scrollView: ScrollingView,
            callback: (() -> Unit)?,
        ): Float {
            val halfWay = compactAppBarHeight / 2
            val shortAnimationDuration =
                resources?.getInteger(
                    if (toolbarMode != ToolbarState.EXPANDED) {
                        android.R.integer.config_shortAnimTime
                    } else {
                        android.R.integer.config_longAnimTime
                    },
                ) ?: 0
            val realHeight = preLayoutHeightWhileSearching + paddingTop
            val closerToTop = abs(y) > realHeight - halfWay
            val atTop = !(scrollView as View).canScrollVertically(-1)
            val shortH =
                if (toolbarMode != ToolbarState.EXPANDED || compactSearchMode) 0f else compactAppBarHeight
            val lastY =
                if (closerToTop && !atTop) {
                    -height.toFloat()
                } else {
                    shortH
                }

            val onFirstItem = scrollView.computeVerticalScrollOffset() < realHeight - shortH

            return if (!onFirstItem) {
                yAnimator =
                    animate()
                        .y(lastY)
                        .setDuration(shortAnimationDuration.toLong())
                yAnimator?.setUpdateListener {
                    if (controller?.isControllerVisible == true) {
                        updateAppBarAfterY(scrollView, false)
                        callback?.invoke()
                    }
                }
                yAnimator?.start()
                useSearchToolbarForMenu(true)
                lastY
            } else {
                useSearchToolbarForMenu((mainToolbar?.alpha ?: 0f) <= 0f)
                y
            }
        }

        fun useSearchToolbarForMenu(showCardTB: Boolean) {
            val mainActivity = mainActivity ?: return
            if (lockYPos) return
            if ((showCardTB || toolbarMode == ToolbarState.SEARCH_ONLY) && cardFrame?.isVisible == true) {
                if (mainActivity.currentToolbar != searchToolbar) {
                    mainActivity.setFloatingToolbar(true, showSearchAnyway = true)
                } else {
                    mainActivity.setSearchTBMenuIfInvalid()
                }
                if (mainActivity.currentToolbar == searchToolbar) {
                    if (toolbarMode == ToolbarState.EXPANDED && !mainToolbarSuppressed) {
                        mainToolbar?.isInvisible = true
                    }
                    mainToolbar?.backgroundColor = null
                    cardFrame?.backgroundColor = null
                }
            } else {
                if (mainActivity.currentToolbar != mainToolbar) {
                    mainActivity.setFloatingToolbar(false, showSearchAnyway = true)
                }
                if (toolbarMode == ToolbarState.EXPANDED) {
                    if (mainToolbarSuppressed) {
                        mainToolbar?.isGone = true
                        mainToolbar?.alpha = 0f
                    } else {
                        mainToolbar?.isInvisible = false
                    }
                }
                if (tabsFrameLayout?.isVisible == false) {
                    cardFrame?.backgroundColor = mainActivity.getResourceColor(R.attr.colorSurface)
                } else {
                    cardFrame?.backgroundColor = null
                }
            }
            animateCardShadow(mainActivity.currentToolbar == searchToolbar && tabsFrameLayout?.isVisible != true)
        }

        /** Animates the search toolbar's card shadow in/out depending on if it's floating alone */
        private fun animateCardShadow(showShadow: Boolean) {
            if (showShadow == showingCardShadow) return
            showingCardShadow = showShadow
            val cardView = cardView ?: return
            val target = if (showShadow) 4f.dpToPx else 0f
            cardShadowAnimator?.cancel()
            cardShadowAnimator =
                ValueAnimator.ofFloat(cardView.cardElevation, target).apply {
                    duration = 150L
                    addUpdateListener {
                        toolbarFilterButton?.elevation = it.animatedValue as Float
                        cardView.cardElevation = it.animatedValue as Float
                    }
                    start()
                }
        }
    }

interface SmallToolbarInterface
