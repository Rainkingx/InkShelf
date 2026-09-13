package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

/**
 * Real full-width spacer used only for a fresh ALL Library before Continue Reading exists.
 *
 * This is deliberately an adapter item instead of RecyclerView padding/decoration. InkShelf's
 * Library/app-bar layout can consume container spacing during positioning, while a real adapter
 * row must occupy measurable content height.
 */
class FreshLibraryTopSpacerItem :
    AbstractFlexibleItem<FreshLibraryTopSpacerItem.Holder>() {

    override fun getLayoutRes(): Int = R.layout.inkshelf_fresh_library_top_spacer

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): Holder = Holder(view, adapter as LibraryCategoryAdapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
    }

    override fun isSelectable(): Boolean = false

    override fun isSwipeable(): Boolean = false

    override fun isDraggable(): Boolean = false

    override fun equals(other: Any?): Boolean = other is FreshLibraryTopSpacerItem

    override fun hashCode(): Int = -104

    class Holder(
        view: View,
        adapter: LibraryCategoryAdapter,
    ) : BaseFlexibleViewHolder(view, adapter, true)
}
