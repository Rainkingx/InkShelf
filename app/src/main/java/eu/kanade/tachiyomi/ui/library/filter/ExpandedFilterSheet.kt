package eu.kanade.tachiyomi.ui.library.filter

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ExpandedFilterSheetBinding
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.widget.StickyFooterBottomSheetDialog
import uy.kohesive.injekt.injectLazy

class ExpandedFilterSheet(
    private val activity: Activity,
    private val filters: List<LibraryFilter>,
    private val trackersFilter: LibraryFilter?,
    private val filterCallback: () -> Unit,
    private val clearFilterCallback: () -> Unit,
) : StickyFooterBottomSheetDialog<ExpandedFilterSheetBinding>(activity) {
    private val fastAdapter: FastAdapter<ExpandedFilterItem>
    private val itemAdapter = ItemAdapter<ExpandedFilterItem>()

    override var recyclerView: RecyclerView? = binding.categoryRecyclerView

    override fun createBinding(inflater: LayoutInflater) = ExpandedFilterSheetBinding.inflate(inflater)

    override val stickyFooterView: View
        get() = binding.buttonLayout

    val trackerItem = trackersFilter?.let { ExpandedFilterItem(trackersFilter) }
    internal val preferences by injectLazy<PreferencesHelper>()

    init {
        // For some reason the bottom sheet gets wonky it's too tall at the start
        binding.categoryRecyclerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = 100.dpToPx
        }
        binding.titleLayout.checkHeightThen {
            binding.categoryRecyclerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                val fullHeight = activity.window.decorView.height
                val insetTop = (activity as? MainActivity)?.cachedSystemInsets?.top ?: 0
                height =
                    fullHeight - insetTop -
                    binding.titleLayout.height - binding.buttonLayout.height - 45.dpToPx
            }
            sheetBehavior.expand()
        }

        fastAdapter = FastAdapter.with(itemAdapter)
        fastAdapter.setHasStableIds(true)
        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.categoryRecyclerView.adapter = fastAdapter
        fastAdapter
            .addEventHook(
                object : ClickEventHook<ExpandedFilterItem>() {
                    override fun onBindMany(viewHolder: RecyclerView.ViewHolder): List<View> =
                        if (viewHolder is ExpandedFilterItem.ViewHolder) {
                            viewHolder.textViews
                        } else {
                            emptyList()
                        }

                    override fun onClick(
                        v: View,
                        position: Int,
                        fastAdapter: FastAdapter<ExpandedFilterItem>,
                        item: ExpandedFilterItem,
                    ) {
                        val index =
                            (v.parent as ViewGroup)
                                .children
                                .toList()
                                .filterIsInstance<MaterialTextView>()
                                .indexOf(v)
                        (v.parent as ViewGroup).children.toList().forEach { it.isActivated = false }
                        item.filter.activeFilter = index
                        v.isActivated = true
                        when (filters[position].headerName) {
                            context.getString(R.string.tracking) -> {
                                if (index == 0 &&
                                    trackerItem != null &&
                                    itemAdapter.adapterItems.contains(
                                        trackerItem,
                                    )
                                ) {
                                    itemAdapter.remove(itemAdapter.adapterItems.indexOf(trackerItem))
                                } else if (index > 0 &&
                                    trackerItem != null &&
                                    !itemAdapter.adapterItems.contains(
                                        trackerItem,
                                    )
                                ) {
                                    itemAdapter.add(position + 1, trackerItem)
                                }
                            }

                            context.getString(R.string.read_progress), context.getString(R.string.unread) -> {
                                if (index != 0) {
                                    val otherName =
                                        if (filters[position].headerName == context.getString(R.string.read_progress)) {
                                            context.getString(R.string.unread)
                                        } else {
                                            context.getString(R.string.read_progress)
                                        }
                                    val otherFilter =
                                        filters.find { it.headerName == otherName } ?: return
                                    if (otherFilter.activeFilter != 0) {
                                        otherFilter.activeFilter = 0
                                        val otherPosition =
                                            itemAdapter.adapterItems.indexOfFirst {
                                                it.filter.headerName == otherFilter.headerName
                                            }
                                        fastAdapter.notifyAdapterItemChanged(otherPosition)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        itemAdapter.set(filters.map(::ExpandedFilterItem))
        val trackingFilter = filters.find { it.headerName == context.getString(R.string.tracking) }
        if ((trackingFilter?.activeFilter ?: 0) > 0 && trackerItem != null) {
            itemAdapter.add(filters.indexOf(trackingFilter) + 1, trackerItem)
        }

        binding.reorderFiltersButton.setOnClickListener {
            openReorderSheet()
        }
    }

    private fun openReorderSheet() {
        val recycler = RecyclerView(context)
        val filterOrder = preferences.filterOrder().get().toMutableList()
        FilterBottomSheet.Filters.entries.forEach {
            if (it.value !in filterOrder) {
                filterOrder.add(it.value)
            }
        }
        val adapter =
            FlexibleAdapter(
                filterOrder.mapNotNull { char ->
                    FilterBottomSheet.Filters.filterOf(char)?.let { ManageFilterItem(char) }
                },
                this,
                true,
            )
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        adapter.isHandleDragEnabled = true
        adapter.isLongPressDragEnabled = true
        context
            .materialAlertDialog()
            .setTitle(R.string.reorder_filters)
            .setView(recycler)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reorder) { _, _ ->
                val order = adapter.currentItems.map { it.char }.joinToString("")
                preferences.filterOrder().set(order)
                recycler.adapter = null
            }.show()
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.expand()
        sheetBehavior.skipCollapsed = true
        updateStickyFooterPosition()
        binding.root.post {
            binding.categoryRecyclerView.scrollToPosition(0)
            updateStickyFooterPosition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.bottomSpacer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = (activity as? MainActivity)?.cachedSystemInsets?.bottom ?: 0
        }

        binding.clearFiltersButton.setOnClickListener {
            clearFilters()
            dismiss()
        }
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        binding.applyButton.setOnClickListener {
            applyFilters()
            dismiss()
        }
    }

    private fun clearFilters() {
        clearFilterCallback()
    }

    private fun applyFilters() {
        val trackingFilter = filters.find { it.headerName == context.getString(R.string.tracking) }
        if (trackingFilter?.activeFilter == 0 && trackerItem != null) {
            trackerItem.filter.activeFilter = 0
        }
        itemAdapter.adapterItems.forEach {
            val state = it.filter.activeFilter - 1
            it.filter.tagGroup.reset()
            it.filter.tagGroup.state = state
        }
        filterCallback()
    }
}
