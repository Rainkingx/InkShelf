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
 * Physical space between the InkShelf tabs and root Library search results.
 * Using a RecyclerView item means AppBar/inset recalculation cannot erase it.
 */
class SearchResultsTopSpacerItem :
    AbstractFlexibleItem<SearchResultsTopSpacerItem.Holder>() {

    override fun getLayoutRes(): Int =
        R.layout.inkshelf_search_results_top_spacer

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): Holder =
        Holder(view, adapter as LibraryCategoryAdapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)
            ?.isFullSpan = true
    }

    override fun isSelectable(): Boolean = false
    override fun isSwipeable(): Boolean = false
    override fun isDraggable(): Boolean = false

    override fun equals(other: Any?): Boolean =
        other is SearchResultsTopSpacerItem

    override fun hashCode(): Int = -105

    class Holder(
        view: View,
        adapter: LibraryCategoryAdapter,
    ) : BaseFlexibleViewHolder(view, adapter, true)
}