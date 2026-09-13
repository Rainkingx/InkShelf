package eu.kanade.tachiyomi.ui.source.browse.repos

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem

/**
 * Custom adapter for repos.
 *
 * @param controller The containing controller.
 */
class RepoAdapter(
    controller: RepoController,
) : FlexibleAdapter<AbstractFlexibleItem<*>>(null, controller, true) {
    /**
     * Listener called when an item of the list is released.
     */
    val repoItemListener: RepoItemListener = controller

    /**
     * URL to prefill into the "create repo" row the next time it is bound, consumed once read.
     */
    var pendingUrl: String? = null

    /**
     * Clears the active selections from the model.
     */
    fun resetEditing(position: Int) {
        for (i in 0..itemCount) {
            (getItem(i) as? RepoItem)?.isEditing = false
        }
        (getItem(position) as? RepoItem)?.isEditing = true
        notifyDataSetChanged()
    }

    /**
     * Toggles the loading spinner for a single row while its repo is being validated
     * over the network, without disturbing the rest of the list.
     */
    fun setLoading(
        position: Int,
        loading: Boolean,
    ) {
        (getItem(position) as? RepoItem)?.isLoading = loading
        notifyItemChanged(position)
    }

    interface RepoItemListener {
        /**
         * Called when an item of the list is released.
         */
        fun onLogoClick(position: Int)

        fun onDiscordClick(position: Int)

        fun onRepoRename(
            position: Int,
            newName: String,
        )

        fun onItemDelete(position: Int)
    }
}
