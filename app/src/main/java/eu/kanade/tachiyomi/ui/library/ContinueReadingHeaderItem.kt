package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

/** Full-width section heading used before recycled READING-tab cards. */
class ContinueReadingHeaderItem : AbstractFlexibleItem<ContinueReadingHeaderItem.Holder>() {
    override fun getLayoutRes(): Int = R.layout.continue_reading_header

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

    override fun equals(other: Any?): Boolean = other is ContinueReadingHeaderItem

    override fun hashCode(): Int = -102

    class Holder(
        view: View,
        adapter: LibraryCategoryAdapter,
    ) : BaseFlexibleViewHolder(view, adapter, true)
}
