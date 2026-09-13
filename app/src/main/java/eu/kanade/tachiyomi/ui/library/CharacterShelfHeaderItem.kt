package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.textview.MaterialTextView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import java.util.Locale

class CharacterShelfHeaderItem(
    val shelfName: String,
    header: LibraryHeaderItem,
) : AbstractSectionableItem<CharacterShelfHeaderItem.Holder, LibraryHeaderItem>(header) {

    override fun getLayoutRes(): Int = R.layout.character_shelf_header

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
        holder.bind(shelfName)

        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)
            ?.isFullSpan = true
    }

    override fun isSelectable(): Boolean = false
    override fun isSwipeable(): Boolean = false
    override fun isDraggable(): Boolean = false

    override fun equals(other: Any?): Boolean =
        other is CharacterShelfHeaderItem &&
            other.shelfName == shelfName &&
            other.header == header

    override fun hashCode(): Int =
        31 * shelfName.hashCode() + header.hashCode()

    class Holder(
        view: View,
        adapter: LibraryCategoryAdapter,
    ) : BaseFlexibleViewHolder(view, adapter, true) {

        private val title =
            view.findViewById<MaterialTextView>(R.id.character_shelf_title)

        fun bind(name: String) {
            title.text = name.uppercase(Locale.ROOT)
        }
    }
}