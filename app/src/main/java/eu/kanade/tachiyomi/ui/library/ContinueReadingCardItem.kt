package eu.kanade.tachiyomi.ui.library

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ContinueReadingCardBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx

/** One recycled full-width card used by the unlimited READING tab list. */
class ContinueReadingCardItem(
    val entry: ContinueReadingEntry,
) : AbstractFlexibleItem<ContinueReadingCardItem.Holder>() {
    override fun getLayoutRes(): Int = R.layout.continue_reading_card

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
        holder.bind(entry)
        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
    }

    override fun isSelectable(): Boolean = false

    override fun isSwipeable(): Boolean = false

    override fun isDraggable(): Boolean = false

    // These objects are cheap and rebuilt with each Library refresh. Identity equality avoids
    // FlexibleAdapter confusing two chapters from the same series during a fast history update.
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    class Holder(
        view: View,
        private val libraryAdapter: LibraryCategoryAdapter,
    ) : BaseFlexibleViewHolder(view, libraryAdapter, true) {
        private val binding = ContinueReadingCardBinding.bind(view)

        init {
            itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = 12.dpToPx
                marginEnd = 12.dpToPx
            }
        }

        fun bind(entry: ContinueReadingEntry) {
            binding.bindContinueReadingEntry(entry, libraryAdapter.controller)
        }
    }
}
