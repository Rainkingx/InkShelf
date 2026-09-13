package eu.kanade.tachiyomi.ui.library

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ContinueReadingCardBinding
import eu.kanade.tachiyomi.databinding.ContinueReadingItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

class ContinueReadingItem(
    entries: List<ContinueReadingEntry> = emptyList(),
) : AbstractFlexibleItem<ContinueReadingItem.Holder>() {
    var entries: List<ContinueReadingEntry> = entries

    override fun getLayoutRes(): Int = R.layout.continue_reading_item

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
        holder.bind(entries)
        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
    }

    override fun isSelectable(): Boolean = false

    override fun isSwipeable(): Boolean = false

    override fun isDraggable(): Boolean = false

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = -101

    class Holder(
        view: View,
        private val libraryAdapter: LibraryCategoryAdapter,
    ) : BaseFlexibleViewHolder(view, libraryAdapter, true) {
        private val binding = ContinueReadingItemBinding.bind(view)
        private val inflater = LayoutInflater.from(view.context)

        fun bind(entries: List<ContinueReadingEntry>) {
            binding.root.isVisible = entries.isNotEmpty()
            binding.viewAll.isVisible = entries.size > 3
            binding.viewAll.setOnClickListener {
                libraryAdapter.controller?.openReadingTabFromContinueReading()
            }
            binding.cardContainer.removeAllViews()

            // ALL intentionally remains a compact shelf. The READING tab uses individual
            // recycler items instead, so an unlimited list does not inflate every card at once.
            entries.take(3).forEach { entry ->
                val card = ContinueReadingCardBinding.inflate(inflater, binding.cardContainer, false)
                card.bindContinueReadingEntry(entry, libraryAdapter.controller)
                binding.cardContainer.addView(card.root)
            }

            // This shelf is one retained RecyclerView item containing all three cards.
            // When returning from Manga Details, FlexibleAdapter can reuse the same holder
            // without remeasuring its rebuilt child views. Explicitly request a fresh measure
            // so the shelf cannot come back clipped to only part of its previous height.
            binding.cardContainer.requestLayout()
            binding.root.requestLayout()
        }
    }
}
