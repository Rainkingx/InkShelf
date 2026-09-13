package eu.kanade.tachiyomi.ui.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.transition.TransitionManager
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationBarSubheaderView
import com.google.android.material.navigationrail.NavigationRailMenuView
import com.google.android.material.navigationrail.NavigationRailView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlin.math.max
import com.google.android.material.R as MaterialR

/**
 * Navigation rail that hides the standalone recents item while expanded, as the submenu shown covers
 * it. Used as setting visibility of a menu item as hidden has a crossing janky animation
 */
@SuppressLint("RestrictedApi")
class SideNavView : NavigationRailView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    init {
        // InkShelf draws its own section dividers so they can follow the selected Appearance
        // accent colour rather than Material's fixed outline colour.
        setSubmenuDividersEnabled(false)

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            itemIconGravity = ITEM_ICON_GRAVITY_TOP
            itemGravity = ITEM_GRAVITY_TOP_CENTER
            itemActiveIndicatorExpandedHeight = itemActiveIndicatorHeight
            setItemActiveIndicatorExpandedPadding(0, 0, 0, 0)
            itemActiveIndicatorExpandedMarginHorizontal = itemActiveIndicatorMarginHorizontal
        }
    }

    private val sideNavMenuView: SideNavMenuView?
        get() = menuView as? SideNavMenuView

    // InkShelf's expanded fold/tablet rail is intentionally compact so the Library remains
    // the visual focus. Material's normal expanded rail can become drawer-like on tablets.
    // Only the expanded rail is capped; the collapsed rail keeps Material/J2K sizing.
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val resolvedWidthSpec =
            if (isExpanded) {
                val compactWidthPx = (160f * resources.displayMetrics.density).toInt()
                val incomingMode = MeasureSpec.getMode(widthMeasureSpec)
                val incomingSize = MeasureSpec.getSize(widthMeasureSpec)
                val allowedWidth =
                    if (incomingMode == MeasureSpec.UNSPECIFIED) {
                        compactWidthPx
                    } else {
                        minOf(compactWidthPx, incomingSize)
                    }

                MeasureSpec.makeMeasureSpec(allowedWidth, MeasureSpec.EXACTLY)
            } else {
                widthMeasureSpec
            }

        super.onMeasure(resolvedWidthSpec, heightMeasureSpec)
    }

    // Called from the superclass constructor, so this can't touch anything declared here.
    override fun createNavigationBarMenuView(context: Context): NavigationRailMenuView = SideNavMenuView(context)

    override fun expand() {
        settleRunningTransition()
        super.expand()
        syncInkShelfDividerVisibility(expanded = true)
    }

    override fun collapse() {
        settleRunningTransition()
        super.collapse()
        syncInkShelfDividerVisibility(expanded = false)
    }

    private fun syncInkShelfDividerVisibility(expanded: Boolean) {
        val menuView = sideNavMenuView ?: return
        for (index in 0 until menuView.childCount) {
            (menuView.getChildAt(index) as? InkShelfRailDivider)?.let { divider ->
                divider.visibility =
                    if (divider.expandedOnly && !expanded) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
            }
        }
    }

    // Added as overlapping expand/collapse transitions break the recents visibility
    private fun settleRunningTransition() {
        (parent as? ViewGroup)?.let(TransitionManager::endTransitions)
    }

    /**
     * Checks [itemId] without the menu animating it. That animation garbles the rail when it runs
     * alongside the expand/collapse one, as both move the same items.
     */
    fun setCheckedItemImmediately(itemId: Int) {
        val menuView = sideNavMenuView
        menuView?.suppressTransitions = true
        try {
            selectedItemId = itemId
        } finally {
            menuView?.suppressTransitions = false
        }
    }
}

@SuppressLint("RestrictedApi", "PrivateResource")
private class SideNavMenuView(
    context: Context,
) : NavigationRailMenuView(context) {
    /** See [SideNavView.setCheckedItemImmediately]. */
    var suppressTransitions = false

    override fun createNavigationBarItemView(context: Context): NavigationBarItemView = SideNavItemView(context)

    // TransitionManager skips a scene root that isn't laid out, the only seam for turning down the
    // transition the menu starts for itself
    override fun isLaidOut(): Boolean = !suppressTransitions && super.isLaidOut()

    // Add InkShelf section dividers at the semantic boundaries of the large-screen rail:
    // Library | Sort | Discover | More. The Sort-only divider hides with the submenu when the
    // rail is collapsed; the destination dividers remain useful in both rail modes.
    override fun addView(child: View) {
        val itemId =
            when (child) {
                is NavigationBarSubheaderView -> child.itemData?.itemId
                is NavigationBarItemView -> child.itemData?.itemId
                else -> null
            }

        when (itemId) {
            R.id.nav_recents_group -> super.addView(InkShelfRailDivider(context, expandedOnly = true))
            R.id.nav_browse -> super.addView(InkShelfRailDivider(context, expandedOnly = false))
            R.id.nav_settings -> super.addView(InkShelfRailDivider(context, expandedOnly = false))
        }

        super.addView(child)

        // The Sort subheader is intentionally centered in portrait-expanded rail mode.
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            child is NavigationBarSubheaderView &&
            child.itemData?.itemId == R.id.nav_recents_group
        ) {
            child.findViewById<TextView>(MaterialR.id.navigation_menu_subheader_label)?.apply {
                gravity = Gravity.CENTER
                updateLayoutParams<MarginLayoutParams> { marginStart = 0 }
            }
        }
    }
}

private class InkShelfRailDivider(
    context: Context,
    val expandedOnly: Boolean,
) : MaterialDivider(context) {
    init {
        // Resolve the active Appearance colour at construction time. Appearance changes recreate
        // the Activity, so each new rail receives the newly-selected accent automatically.
        dividerColor =
            ColorUtils.setAlphaComponent(
                context.getResourceColor(R.attr.colorPrimary),
                128,
            )
        visibility = if (expandedOnly) View.GONE else View.VISIBLE
    }
}

/** Stands in for the rail's own item view, which is final and package private. */
@SuppressLint("RestrictedApi", "PrivateResource")
private class SideNavItemView(
    context: Context,
) : NavigationBarItemView(context) {
    override fun getItemLayoutResId(): Int = MaterialR.layout.mtrl_navigation_rail_item

    override fun getItemDefaultMarginResId(): Int = MaterialR.dimen.mtrl_navigation_rail_icon_margin

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            setMeasuredDimension(
                measuredWidthAndState,
                max(measuredHeight, MeasureSpec.getSize(heightMeasureSpec)),
            )
        }
    }

    // Recents remains in the menu model so legacy shortcuts/deep links still resolve, but it is
    // never a visible large-screen destination. Its former submenu is now the Sort section.
    override fun setVisibility(visibility: Int) {
        val hidden = itemData?.itemId == R.id.nav_recents
        super.setVisibility(if (hidden) GONE else visibility)
    }
}
