package eu.kanade.tachiyomi.ui.source.browse.repos

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.RepoMetadata

/**
 * Repo item for a recycler view.
 */
class RepoItem(
    val repo: String,
    val metadata: RepoMetadata? = null,
) : AbstractFlexibleItem<RepoHolder>() {
    /**
     * Whether this item is currently selected.
     */
    var isEditing = false

    /**
     * Whether this item's repo is being validated over the network right now.
     */
    var isLoading = false

    /**
     * Returns the layout resource for this item.
     */
    override fun getLayoutRes(): Int = R.layout.repo_item

    /**
     * Returns a new view holder for this item.
     *
     * @param view The view of this item.
     * @param adapter The adapter of this item.
     */
    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): RepoHolder = RepoHolder(view, adapter as RepoAdapter)

    /**
     * Binds the given view holder with this item.
     *
     * @param adapter The adapter of this item.
     * @param holder The holder to bind.
     * @param position The position of this item in the adapter.
     * @param payloads List of partial changes.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: RepoHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(repo, metadata)
        holder.isEditing(isEditing)
        holder.setLoading(isLoading)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean = false

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = repo.hashCode()
}
