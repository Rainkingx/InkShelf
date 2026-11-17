package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R

class MissingChaptersItem(
    val id: String,
    val count: Int,
    val startChapter: Int,
    val endChapter: Int,
    val comic: Boolean = false,
) : AbstractFlexibleItem<MissingChaptersItem.Holder>() {
    override fun getLayoutRes(): Int = R.layout.missing_chapters_item

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): Holder = Holder(view, adapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        holder.bind(count, comic)
    }

    override fun isSelectable(): Boolean = false

    override fun isDraggable(): Boolean = false

    override fun isSwipeable(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MissingChaptersItem) return false
        return id == other.id && count == other.count && comic == other.comic
    }

    override fun hashCode(): Int = id.hashCode()

    class Holder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ) : FlexibleViewHolder(view, adapter) {
        private val text: TextView = view.findViewById(R.id.missing_chapters_text)

        fun bind(count: Int, comic: Boolean) {
            val label = if (comic) R.plurals.inkshelf_missing_issues else R.plurals.missing_chapters
            text.text = itemView.resources.getQuantityString(label, count, count)
        }
    }
}
