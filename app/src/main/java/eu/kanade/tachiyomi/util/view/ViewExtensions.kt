@file:Suppress("NOTHING_TO_INLINE")

package eu.kanade.tachiyomi.util.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.PowerManager
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.Dimension
import androidx.annotation.FloatRange
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.children
import androidx.core.view.descendants
import androidx.core.view.forEach
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.math.MathUtils
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationBarMenuView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.lang.tintText
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.pxToDp
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.StaggeredGridLayoutManagerAccurateOffset
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Returns coordinates of view.
 * Used for animation
 *
 * @return coordinates of view
 */
fun View.getCoordinates() = Point((left + right) / 2, (top + bottom) / 2)

/**
 * Shows a snackbar in this view.
 *
 * @param message the message to show.
 * @param length the duration of the snack.
 * @param f a function to execute in the snack, allowing for example to define a custom action.
 */
fun View.snack(
    message: CharSequence,
    length: Int = Snackbar.LENGTH_SHORT,
    f: (Snackbar.() -> Unit)? = null,
): Snackbar {
    val snack = Snackbar.make(this, message, length)
    if (f != null) {
        snack.f()
    }
    if (ThemeUtil.isPitchBlack(context)) {
        val textView: TextView =
            snack.view.findViewById(com.google.android.material.R.id.snackbar_text)
        val button: Button? =
            snack.view.findViewById(com.google.android.material.R.id.snackbar_action)
        textView.setTextColor(Color.WHITE)
        button?.setTextColor(Color.WHITE)
        snack.view.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
    }
    snack.show()
    return snack
}

fun View.snack(
    resource: Int,
    length: Int = Snackbar.LENGTH_SHORT,
    f: (Snackbar.() -> Unit)? = null,
): Snackbar = snack(context.getString(resource), length, f)

fun Snackbar.getText(): CharSequence {
    val textView: TextView = view.findViewById(com.google.android.material.R.id.snackbar_text)
    return textView.text
}

object RecyclerWindowInsetsListener : View.OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(
        v: View,
        insets: WindowInsets,
    ): WindowInsets {
        v.updatePaddingRelative(
            bottom =
                WindowInsetsCompat
                    .toWindowInsetsCompat(insets)
                    .getInsets(systemBars())
                    .bottom,
        )
        return insets
    }
}

fun View.applyBottomAnimatedInsets(
    bottomMargin: Int = 0,
    setPadding: Boolean = false,
    onApplyInsets: ((View, WindowInsetsCompat) -> Unit)? = null,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val setInsets: ((WindowInsetsCompat) -> Unit) = { insets ->
        val bottom = insets.getInsets(systemBars() or ime()).bottom

        if (setPadding) {
            updatePaddingRelative(bottom = bottomMargin + bottom)
        } else {
            updateLayoutParams<ViewGroup.MarginLayoutParams> {
                this.bottomMargin = bottom + bottomMargin
            }
        }
    }
    var handleInsets = true
    doOnApplyWindowInsetsCompat { view, insets, _ ->
        onApplyInsets?.invoke(view, insets)
        if (handleInsets) {
            setInsets(insets)
        }
    }

    ViewCompat.setWindowInsetsAnimationCallback(
        this,
        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                handleInsets = false
                super.onPrepare(animation)
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat,
            ): WindowInsetsAnimationCompat.BoundsCompat {
                handleInsets = false
                rootWindowInsetsCompat?.let { insets -> setInsets(insets) }
                return super.onStart(animation, bounds)
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat {
                setInsets(insets)
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                handleInsets = true
                rootWindowInsetsCompat?.let { insets -> setInsets(insets) }
            }
        },
    )
}

class ControllerViewWindowInsetsListener(
    private val topHeight: Int,
) : OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(
        v: View,
        insets: WindowInsetsCompat,
    ): WindowInsetsCompat {
        v.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = insets.getInsets(systemBars()).top + topHeight
        }
        return insets
    }
}

fun View.doOnApplyWindowInsets(f: (View, WindowInsets, ViewPaddingState) -> Unit) {
    // Create a snapshot of the view's padding state
    val paddingState = createStateForView(this)
    setOnApplyWindowInsetsListener { v, insets ->
        f(v, insets, paddingState)
        insets
    }
    requestApplyInsetsWhenAttached()
}

fun View.doOnApplyWindowInsetsCompat(f: (View, WindowInsetsCompat, ViewPaddingState) -> Unit) {
    // Create a snapshot of the view's padding state
    val paddingState = createStateForView(this)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        f(v, insets, paddingState)
        insets
    }
    requestApplyInsetsWhenAttached()
}

fun View.applyWindowInsetsForController(topHeight: Int) {
    ViewCompat.setOnApplyWindowInsetsListener(this, ControllerViewWindowInsetsListener(topHeight))
    requestApplyInsetsWhenAttached()
}

fun View.checkHeightThen(f: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (height > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    f()
                }
            }
        },
    )
}

fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.requestApplyInsets()
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            },
        )
    }
}

private fun createStateForView(view: View) =
    ViewPaddingState(
        view.paddingLeft,
        view.paddingTop,
        view.paddingRight,
        view.paddingBottom,
        view.paddingStart,
        view.paddingEnd,
    )

data class ViewPaddingState(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val start: Int,
    val end: Int,
)

fun setBottomEdge(
    view: View,
    activity: Activity,
) {
    val marginB = view.marginBottom
    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        bottomMargin = marginB +
            (
                activity.window.decorView.rootWindowInsetsCompat
                    ?.getInsets(systemBars())
                    ?.bottom ?: 0
            )
    }
}

fun SwipeRefreshLayout.setStyle() {
    setColorSchemeColors(context.getResourceColor(R.attr.actionBarTintColor))
    setProgressBackgroundColorSchemeColor(context.getResourceColor(R.attr.colorSurfaceContainer))
}

// Sorted since obtainStyledAttributes expects the attr ids in ascending order.
private val buttonStyleAttrs =
    intArrayOf(
        android.R.attr.textColor,
        R.attr.backgroundTint,
        R.attr.rippleColor,
        R.attr.strokeColor,
        R.attr.strokeWidth,
    ).sortedArray()

/**
 * Swaps in the text color, background tint, ripple color, stroke color and stroke width of the
 * style [styleAttr] points at, with their state lists (disabled, checked, etc) intact.
 *
 * Expressive button styles barely set any of these directly: the text color selector resolves
 * ?colorOnContainer(Checked/Unchecked) from the style's materialThemeOverlay, and the stroke width
 * resolves ?containerStrokeWidth from its materialSizeOverlay, so both overlays have to be applied
 * before reading the style.
 */
fun MaterialButton.applyStyleFromAttr(
    @AttrRes styleAttr: Int,
) = applyStyle(context.obtainStyledAttributes(intArrayOf(styleAttr)).use { it.getResourceId(0, 0) })

/** [applyStyleFromAttr] for a style referenced directly instead of through a theme attr. */
fun MaterialButton.applyStyle(
    @StyleRes styleRes: Int,
) {
    if (styleRes == 0) return
    var styleContext: Context = context
    intArrayOf(R.attr.materialThemeOverlay, R.attr.materialSizeOverlay).forEach { overlayAttr ->
        val overlayRes =
            styleContext.obtainStyledAttributes(styleRes, intArrayOf(overlayAttr)).use {
                it.getResourceId(0, 0)
            }
        if (overlayRes != 0) {
            styleContext = ContextThemeWrapper(styleContext, overlayRes)
        }
    }
    styleContext.obtainStyledAttributes(styleRes, buttonStyleAttrs).use { attrs ->
        attrs
            .getColorStateList(buttonStyleAttrs.binarySearch(android.R.attr.textColor))
            ?.let { setTextColor(it) }
        backgroundTintList = attrs.getColorStateList(buttonStyleAttrs.binarySearch(R.attr.backgroundTint))
        rippleColor = attrs.getColorStateList(buttonStyleAttrs.binarySearch(R.attr.rippleColor))
        strokeColor = attrs.getColorStateList(buttonStyleAttrs.binarySearch(R.attr.strokeColor))
        strokeWidth = attrs.getDimensionPixelSize(buttonStyleAttrs.binarySearch(R.attr.strokeWidth), 0)
    }
}

@SuppressLint("RestrictedApi")
fun NavigationBarView.getItemView(
    @IdRes id: Int,
): NavigationBarItemView? = (menuView as NavigationBarMenuView).findViewById(id)

fun RecyclerView.smoothScrollToTop() {
    val linearLayoutManager = layoutManager as? LinearLayoutManager
    val staggeredLayoutManager = layoutManager as? StaggeredGridLayoutManagerAccurateOffset
    if (linearLayoutManager != null || staggeredLayoutManager != null) {
        val smoothScroller: SmoothScroller =
            object : LinearSmoothScroller(context) {
                override fun getVerticalSnapPreference(): Int = SNAP_TO_START
            }
        smoothScroller.targetPosition = 0
        val firstItemPos =
            linearLayoutManager?.findFirstVisibleItemPosition()
                ?: staggeredLayoutManager?.findFirstVisibleItemPosition() ?: 0
        if (firstItemPos > 15) {
            scrollToPosition(15)
            post {
                linearLayoutManager?.startSmoothScroll(smoothScroller)
                staggeredLayoutManager?.startSmoothScroll(smoothScroller)
            }
        } else {
            linearLayoutManager?.startSmoothScroll(smoothScroller)
            staggeredLayoutManager?.startSmoothScroll(smoothScroller)
        }
    } else {
        scrollToPosition(0)
    }
}

fun View.rowsForValue(value: Float) = measuredWidth.numberOfRowsForValue(value)

fun Int.numberOfRowsForValue(rawValue: Float): Int {
    val value = (rawValue / 2f) - .5f
    val size = 1.5f.pow(value)
    val trueSize =
        AutofitRecyclerView.MULTIPLE * ((size * 100 / AutofitRecyclerView.MULTIPLE).roundToInt()) / 100f
    val dpWidth = (this.pxToDp / 100f).roundToInt()
    return max(1, (dpWidth / trueSize).roundToInt())
}

@SuppressLint("RestrictedApi")
inline fun View.popupMenu(
    items: List<Pair<Int, Int>>,
    selectedItemId: Int? = null,
    noinline onMenuItemClick: MenuItem.() -> Unit,
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.NO_GRAVITY)
    items.forEach { (id, stringRes) ->
        popup.menu.add(0, id, 0, stringRes)
    }

    if (selectedItemId != null) {
        val blendedAccent =
            ColorUtils.blendARGB(
                context.getResourceColor(R.attr.colorPrimary),
                context.getResourceColor(R.attr.colorOnBackground),
                0.5f,
            )
        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        val emptyIcon = ContextCompat.getDrawable(context, R.drawable.ic_blank_24dp)
        popup.menu.forEach { item ->
            item.icon =
                when (item.itemId) {
                    selectedItemId ->
                        ContextCompat.getDrawable(context, R.drawable.ic_check_24dp)?.mutate()?.apply {
                            setTint(blendedAccent)
                        }
                    else -> emptyIcon
                }
            if (item.itemId == selectedItemId) {
                item.title = item.title?.tintText(blendedAccent)
            }
        }
    }

    popup.setOnMenuItemClickListener {
        it.onMenuItemClick()
        true
    }

    popup.show()
    return popup
}

fun MaterialCardView.makeContainerShape(
    top: Boolean,
    bottom: Boolean,
): ShapeAppearanceModel {
    val mainCornerRadius = resources.getDimension(R.dimen.container_main_corner)
    val subCornerRadius = resources.getDimension(R.dimen.container_sub_corner)
    val topRadius = if (top) mainCornerRadius else subCornerRadius
    val bottomRadius = if (bottom) mainCornerRadius else subCornerRadius
    return shapeAppearanceModel
        .toBuilder()
        .apply {
            setTopLeftCorner(CornerFamily.ROUNDED, topRadius)
            setTopRightCorner(CornerFamily.ROUNDED, topRadius)
            setBottomLeftCorner(CornerFamily.ROUNDED, bottomRadius)
            setBottomRightCorner(CornerFamily.ROUNDED, bottomRadius)
        }.build()
}

fun MaterialCardView.makeShapeCorners(
    @Dimension topStart: Float = 0f,
    @Dimension bottomEnd: Float = 0f,
    roundEnd: Boolean = false,
): ShapeAppearanceModel =
    shapeAppearanceModel
        .toBuilder()
        .apply {
            if (context.resources.isLTR) {
                setTopLeftCorner(CornerFamily.ROUNDED, topStart)
                setBottomLeftCorner(CornerFamily.ROUNDED, if (topStart > 0) 4f.dpToPx else 0f)
                setBottomRightCorner(CornerFamily.ROUNDED, bottomEnd)
                setTopRightCorner(
                    CornerFamily.ROUNDED,
                    if (roundEnd) {
                        bottomEnd
                    } else if (bottomEnd > 0) {
                        4f.dpToPx
                    } else {
                        0f
                    },
                )
            } else {
                setTopLeftCorner(
                    CornerFamily.ROUNDED,
                    if (roundEnd) {
                        bottomEnd
                    } else if (bottomEnd > 0) {
                        4f.dpToPx
                    } else {
                        0f
                    },
                )
                setBottomLeftCorner(CornerFamily.ROUNDED, bottomEnd)
                setBottomRightCorner(CornerFamily.ROUNDED, if (topStart > 0) 4f.dpToPx else 0f)
                setTopRightCorner(CornerFamily.ROUNDED, topStart)
            }
        }.build()

fun setCards(
    showOutline: Boolean,
    mainCard: MaterialCardView,
    badgeView: MaterialCardView?,
) {
    badgeView?.strokeWidth = if (showOutline) 0.75f.dpToPx.toInt() else 0
    badgeView?.cardElevation = if (showOutline) 0f else 3f.dpToPx
    mainCard.strokeWidth = if (showOutline) 1.dpToPx else 0
}

var View.backgroundColor: Int?
    get() = (background as? ColorDrawable)?.color ?: (background as? MaterialShapeDrawable)?.fillColor?.defaultColor
    set(value) {
        if (value != null) setBackgroundColor(value) else background = null
    }

var View.gradientBackgroundColor: Int?
    get() = (background as? GradientDrawable)?.colors?.firstOrNull()
    set(value) {
        background =
            if (value != null) {
                val topColor = if (Color.alpha(value) > 0) ColorUtils.setAlphaComponent(value, 255) else value
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        topColor,
                        ColorUtils.setAlphaComponent(
                            topColor,
                            if (Color.alpha(value) >
                                0
                            ) {
                                204
                            } else {
                                0
                            },
                        ),
                        ColorUtils.setAlphaComponent(topColor, 0),
                    ),
                )
            } else {
                null
            }
    }

/**
 * Returns this ViewGroup's first descendant of specified class
 */
inline fun <reified T> ViewGroup.findChild(): T? = children.find { it is T } as? T

/**
 * Returns this ViewGroup's first descendant of specified class
 */
inline fun <reified T> ViewGroup.findDescendant(): T? = descendants.find { it is T } as? T

fun Dialog.blurBehindWindow(
    window: Window?,
    blurAmount: Float = 20f,
    onShow: DialogInterface.OnShowListener? = null,
    onDismiss: DialogInterface.OnDismissListener? = null,
    onCancel: DialogInterface.OnCancelListener? = null,
) {
    var supportsBlur = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window?.windowManager?.isCrossWindowBlurEnabled == true) {
        supportsBlur = true
    }
    var registered = true
    val powerSaverChangeReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window?.windowManager?.isCrossWindowBlurEnabled == true) {
                    return
                }
                val canBlur =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !this@blurBehindWindow.context.powerManager.isPowerSaveMode
                window?.setDimAmount(if (canBlur) 0.45f else 0.77f)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
                if (canBlur) {
                    window?.decorView?.setRenderEffect(
                        RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP),
                    )
                } else {
                    window?.decorView?.setRenderEffect(null)
                }
            }
        }
    val filter = IntentFilter()
    filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
    ContextCompat.registerReceiver(context, powerSaverChangeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    val unregister: () -> Unit = {
        if (registered) {
            context.unregisterReceiver(powerSaverChangeReceiver)
            registered = false
        }
    }
    setOnShowListener {
        onShow?.onShow(it)
        if (!supportsBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.decorView?.animateBlur(1f, blurAmount, 50)?.start()
        }
    }
    setOnDismissListener {
        onDismiss?.onDismiss(it)
        unregister()
        if (!supportsBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.decorView?.animateBlur(blurAmount, 1f, 50, true)?.start()
        }
    }
    setOnCancelListener {
        onCancel?.onCancel(it)
        unregister()
        if (!supportsBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.decorView?.animateBlur(blurAmount, 1f, 50, true)?.start()
        }
    }
}

fun TextView.setTextColorAlpha(alpha: Int) {
    setTextColor(ColorUtils.setAlphaComponent(currentTextColor, alpha))
}

fun View.updateGradiantBGRadius(
    ogRadius: Float,
    deviceRadius: Pair<Float, Float>,
    progress: Float,
    vararg updateOtherViews: View,
) {
    (background as? GradientDrawable)?.let { drawable ->
        val hasRail = resources.configuration.screenWidthDp >= 720
        val lerpL =
            MathUtils.lerp(
                ogRadius,
                if (hasRail && resources.isLTR) 0f else deviceRadius.first,
                max(0f, progress),
            )
        val lerpR =
            MathUtils.lerp(
                ogRadius,
                if (hasRail && !resources.isLTR) 0f else deviceRadius.second,
                max(0f, progress),
            )
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadii = floatArrayOf(lerpL, lerpL, lerpR, lerpR, 0f, 0f, 0f, 0f)
        background = drawable
        updateOtherViews.forEach {
            (it.background as? GradientDrawable)?.let { tDrawable ->
                tDrawable.shape = GradientDrawable.RECTANGLE
                tDrawable.cornerRadii = floatArrayOf(lerpL, lerpL, lerpR, lerpR, 0f, 0f, 0f, 0f)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
fun View.animateBlur(
    @FloatRange(from = 0.1) from: Float,
    @FloatRange(from = 0.1) to: Float,
    duration: Long,
    removeBlurAtEnd: Boolean = false,
): ValueAnimator? {
    if (context.powerManager.isPowerSaveMode) {
        if (to <= 0.1f) {
            setRenderEffect(null)
        }
        return null
    }
    return ValueAnimator.ofFloat(from, to).apply {
        interpolator = FastOutLinearInInterpolator()
        this.duration = duration
        addUpdateListener { animator ->
            val amount = animator.animatedValue as Float
            try {
                setRenderEffect(
                    RenderEffect.createBlurEffect(amount, amount, Shader.TileMode.CLAMP),
                )
            } catch (_: Exception) {
            }
        }
        if (removeBlurAtEnd) {
            addListener(
                onEnd = {
                    setRenderEffect(null)
                },
            )
        }
    }
}

fun View?.isVisibleOnScreen(): Boolean {
    if (this == null) {
        return false
    }
    if (!this.isShown) {
        return false
    }
    val actualPosition = Rect()
    this.getGlobalVisibleRect(actualPosition)
    val screen = Rect(0, 0, Resources.getSystem().displayMetrics.widthPixels, Resources.getSystem().displayMetrics.heightPixels)
    return actualPosition.intersect(screen)
}
