package eu.kanade.tachiyomi.ui.library

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.github.florent37.viewtooltip.ViewTooltip
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryCategoryHeaderItemBinding
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryHeaderHolder(
    val view: View,
    val adapter: LibraryCategoryAdapter,
) : BaseFlexibleViewHolder(view, adapter, true) {
    private val binding = LibraryCategoryHeaderItemBinding.bind(view)
    val progressDrawableStart = CircularProgressDrawable(itemView.context)
    val progressDrawableEnd = CircularProgressDrawable(itemView.context)
    private val runningDrawable = CircularProgressDrawable(itemView.context)
    private val refreshDrawable = itemView.context.contextCompatDrawable(R.drawable.ic_refresh_24dp)
    var locked = false
    private val headerGestureDetector = LibraryHeaderGestureDetector(this, binding)

    init {
        binding.categoryHeaderLayout.setOnClickListener {
            if (adapter.controller?.isSubClass == false) return@setOnClickListener
            if (!locked) toggleCategory()
            locked = false
        }
        binding.updateButton.setOnClickListener { addCategoryToUpdate() }
        binding.categoryTitle.setOnLongClickListener {
            if (adapter.controller?.isSubClass == false) true else manageCategory()
        }
        binding.categoryTitle.setOnClickListener {
            if (adapter.controller?.isSubClass == false) return@setOnClickListener
            val category =
                (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category
            if (category?.isHidden == false && adapter.mode == SelectableAdapter.Mode.MULTI) {
                selectAll()
            } else {
                toggleCategory()
            }
        }
        binding.categorySort.setOnClickListener {
            it.post {
                if (adapter.controller?.isSubClass == false) {
                    adapter.controller?.showModernAllComicsSortMenu()
                } else {
                    showCatSortOptions()
                }
            }
        }
        binding.categorySort.tooltipText = view.context.getString(R.string.sort)
        binding.checkbox.setOnClickListener { selectAll() }

        runningDrawable.setStyle(CircularProgressDrawable.DEFAULT)
        runningDrawable.centerRadius = 6f.dpToPx
        runningDrawable.strokeWidth = 2f.dpToPx
        runningDrawable.setColorSchemeColors(itemView.context.getResourceColor(R.attr.colorPrimary))

        binding.endRefresh.setImageDrawable(progressDrawableEnd)
        binding.startRefresh.setImageDrawable(progressDrawableStart)
        binding.startRefresh.scaleX = -1f
        listOf(progressDrawableStart, progressDrawableEnd).forEach {
            it.setColorSchemeColors(itemView.context.getResourceColor(R.attr.colorOnPrimary))
            it.centerRadius = 1f
            it.arrowEnabled = true
            it.setStyle(CircularProgressDrawable.DEFAULT)
        }
        binding.setTouchEvents()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun LibraryCategoryHeaderItemBinding.setTouchEvents() {
        // In the modern root Library this holder is presentation-only modern grid header.
        // Consume touches so stock J2K swipe/collapse/update gestures cannot hide the library.
        if (adapter.controller?.isSubClass == false) {
            // Keep the stock collapse/swipe gestures disabled, but leave the modern
            // sort button clickable.
            listOf(categoryHeaderLayout, categoryTitle, updateButton).forEach {
                it.setOnTouchListener { _, _ -> true }
            }
            categorySort.setOnTouchListener(null)
            return
        }

        val gestureDetector = GestureDetectorCompat(root.context, headerGestureDetector)
        listOf(categoryHeaderLayout, categorySort, categoryTitle, updateButton).forEach {
            var isCancelling = false
            it.setOnTouchListener { _, event ->
                if (event?.action == MotionEvent.ACTION_DOWN) {
                    locked = false
                }
                when (event?.action) {
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                        itemView.parent.requestDisallowInterceptTouchEvent(false)
                        if (isCancelling) {
                            isCancelling = false
                            return@setOnTouchListener false
                        }
                        val result = gestureDetector.onTouchEvent(event)
                        if (!result) {
                            val anim =
                                binding.categoryHeaderLayout
                                    .animate()
                                    .setDuration(150L)
                                    .translationX(0f)
                            anim.withEndAction { rearView.isVisible = true }
                            anim.start()
                            if (headerGestureDetector.vibrated) {
                                addCategoryToUpdate()
                            }
                        } else {
                            isCancelling = true
                            val ev2 = MotionEvent.obtain(event)
                            ev2.action = MotionEvent.ACTION_CANCEL
                            it.dispatchTouchEvent(ev2)
                            ev2.recycle()
                        }
                        result
                    }
                    else -> {
                        gestureDetector.onTouchEvent(event)
                    }
                }
            }
        }
    }

    private fun toggleCategory() {
        adapter.libraryListener?.toggleCategoryVisibility(flexibleAdapterPosition)
        val tutorial = Injekt.get<PreferencesHelper>().shownLongPressCategoryTutorial()
        if (!tutorial.get()) {
            ViewTooltip
                .on(itemView.context as? Activity, binding.categoryTitle)
                .autoHide(true, 5000L)
                .align(ViewTooltip.ALIGN.START)
                .position(ViewTooltip.Position.TOP)
                .text(R.string.long_press_category)
                .color(itemView.context.getResourceColor(R.attr.colorPrimary))
                .textSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                .textColor(Color.WHITE)
                .withShadow(false)
                .corner(30)
                .arrowWidth(15)
                .arrowHeight(15)
                .distanceWithView(0)
                .show()
            tutorial.set(true)
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(item: LibraryHeaderItem) {
        // The modern main library uses the category tabs as navigation, so the old J2K
        // category title/sort/update rows are redundant. Keep the header objects internally
        // (the adapter still relies on them for sectioning) but only render one clean
        // modern heading before the grid. Subclass/filtered library screens keep the
        // original J2K header behaviour untouched.
        if (adapter.controller?.isSubClass == false) {
            val isFirstHeader = adapter.headerItems.firstOrNull() == item
            binding.root.isVisible = isFirstHeader
            if (!isFirstHeader) return

            binding.categoryHeaderLayout.isVisible = true
            binding.rearView.isVisible = false
            binding.categoryTitle.isVisible = true
            val controller = adapter.controller
            binding.categoryTitle.text =
                view.context.getString(controller?.modernGridHeaderTitleRes() ?: R.string.modern_library_all_comics)
            binding.categoryTitle.setCompoundDrawablesRelative(null, null, null, null)
            binding.categoryTitle.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = 18.dpToPx
            }
            binding.categorySort.isVisible = controller?.isModernAllComicsTabSelected() == true
            if (binding.categorySort.isVisible) {
                binding.categorySort.text =
                    view.context.getString(
                        controller?.modernAllComicsSortLabelRes() ?: R.string.modern_library_sort_recently_added,
                    )
            }
            binding.updateButton.isVisible = false
            binding.collapseArrow.isVisible = false
            binding.checkbox.isVisible = false
            return
        }

        // A holder may be recycled after leaving the main library. Restore normal visibility
        // before applying the standard J2K binding below.
        binding.root.isVisible = true
        binding.categoryHeaderLayout.isVisible = true
        binding.categoryTitle.isVisible = true
        binding.categorySort.isVisible = true

        val index = adapter.headerItems.indexOf(item)
        val previousIsCollapsed =
            if (index > 0) {
                (adapter.headerItems[index - 1] as? LibraryHeaderItem)?.category?.isHidden
                    ?: false
            } else {
                false
            }
        val shorterMargin = adapter.headerItems.firstOrNull() == item
        binding.categoryTitle.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin =
                (
                    when {
                        shorterMargin -> 2
                        previousIsCollapsed -> 5
                        else -> 32
                    }
                ).dpToPx
        }
        binding.rearView.updatePadding(top = binding.categoryTitle.marginTop - 6)
        val category = item.category

        val isFilteredList =
            (adapter.libraryListener as? FilteredLibraryController)?.let {
                it.filterCategories.size == 1 && it.getTitle() == category.name
            } ?: false
        val categoryName =
            if ((category.isAlone || isFilteredList) && !category.isDynamic) {
                ""
            } else {
                category.name
            }

        binding.categoryTitle.text = categoryName +
            if (adapter.showNumber) {
                " (${adapter.itemsPerCategory[item.catId]})"
            } else {
                ""
            }
        if (category.sourceId != null) {
            val icon = adapter.sourceManager.get(category.sourceId!!)?.icon()
            icon?.setBounds(0, 0, 32.dpToPx, 32.dpToPx)
            binding.categoryTitle.setCompoundDrawablesRelative(icon, null, null, null)
        } else if (category.langId != null) {
            val icon = getFlagIcon(category.langId!!) ?: 0
            binding.categoryTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)
        } else {
            binding.categoryTitle.setCompoundDrawablesRelative(null, null, null, null)
        }

        val isAscending = category.isAscending()
        val sortingMode = category.sortingMode()
        val sortDrawable = getSortRes(sortingMode, isAscending, R.drawable.ic_sort_24dp)

        binding.categorySort.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, sortDrawable, 0)
        binding.categorySort.setText(category.sortRes())
        binding.collapseArrow.setImageResource(
            if (category.isHidden) {
                R.drawable.ic_expand_more_24dp
            } else {
                R.drawable.ic_expand_less_24dp
            },
        )
        when {
            adapter.mode == SelectableAdapter.Mode.MULTI -> {
                binding.checkbox.isVisible = !category.isHidden
                binding.collapseArrow.isVisible = category.isHidden && !adapter.isSingleCategory
                binding.updateButton.isVisible = false
                setSelection()
            }
            (category.id ?: -1) < 0 || adapter.libraryListener is FilteredLibraryController -> {
                binding.collapseArrow.isVisible = false
                binding.checkbox.isVisible = false
                setRefreshing(false)
                binding.updateButton.isVisible = false
            }
            LibraryUpdateJob.categoryInQueue(category.id) -> {
                binding.collapseArrow.isVisible = !adapter.isSingleCategory
                binding.checkbox.isVisible = false
                binding.updateButton.isVisible = true
                setRefreshing(true)
            }
            else -> {
                binding.collapseArrow.isVisible = !adapter.isSingleCategory
                binding.checkbox.isVisible = false
                setRefreshing(false)
                binding.updateButton.isVisible = !adapter.isSingleCategory
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    fun getFlagIcon(lang: String): Int? {
        val flagId =
            itemView.resources
                .getIdentifier(
                    "ic_flag_${lang.replace("-", "_")}",
                    "drawable",
                    itemView.context.packageName,
                ).takeIf { it != 0 } ?: (
                if (lang.contains("-")) {
                    itemView.resources
                        .getIdentifier(
                            "ic_flag_${lang.split("-").first()}",
                            "drawable",
                            itemView.context.packageName,
                        ).takeIf { it != 0 }
                } else {
                    null
                }
            )
        return flagId
    }

    fun setRefreshing(refreshing: Boolean) {
        binding.updateButton.isClickable = !refreshing
        if (refreshing) {
            if (!runningDrawable.isRunning) {
                binding.updateButton.setImageDrawable(runningDrawable)
                runningDrawable.arrowEnabled = false
                runningDrawable.start()
            }
        } else {
            runningDrawable.stop()
            runningDrawable.setStartEndTrim(0f, 0.8f)
            runningDrawable.arrowEnabled = true
            binding.updateButton.setImageDrawable(refreshDrawable)
        }
    }

    fun addCategoryToUpdate() {
        if (adapter.libraryListener?.updateCategory(flexibleAdapterPosition) == true) {
            setRefreshing(true)
        }
    }

    fun manageCategory(): Boolean {
        val category = (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category
        adapter.libraryListener?.manageCategory(flexibleAdapterPosition)
        return category?.isDynamic == false
    }

    private fun showCatSortOptions() {
        val category =
            (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category ?: return
        adapter.controller?.activity?.let { activity ->
            val items = LibrarySort.entries.map { it.menuSheetItem(category.isDynamic) }
            val sortingMode = category.sortingMode(true)
            val sheet =
                MaterialMenuSheet(
                    activity,
                    items,
                    activity.getString(R.string.sort_by),
                    sortingMode?.mainValue,
                ) { sheet, item ->
                    onCatSortClicked(category, item)
                    val nCategory = (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category
                    val isAscending = nCategory?.isAscending() ?: false
                    val drawableRes = getSortRes(item, isAscending)
                    sheet.setDrawable(item, drawableRes)
                    false
                }
            val isAscending = category.isAscending()
            val drawableRes = getSortRes(sortingMode, isAscending)
            sheet.setDrawable(sortingMode?.mainValue ?: -1, drawableRes)
            sheet.show()
        }
    }

    private fun getSortRes(
        sortMode: LibrarySort?,
        isAscending: Boolean,
        @DrawableRes defaultDrawableRes: Int = R.drawable.ic_check_24dp,
    ): Int {
        sortMode ?: return defaultDrawableRes
        return when (sortMode) {
            LibrarySort.DragAndDrop -> defaultDrawableRes
            else -> {
                if (if (sortMode.hasInvertedSort) !isAscending else isAscending) {
                    R.drawable.ic_arrow_downward_24dp
                } else {
                    R.drawable.ic_arrow_upward_24dp
                }
            }
        }
    }

    private fun getSortRes(
        sortingMode: Int?,
        isAscending: Boolean,
        @DrawableRes defaultDrawableRes: Int = R.drawable.ic_check_24dp,
    ): Int {
        sortingMode ?: return defaultDrawableRes
        return when (val sortMode = LibrarySort.valueOf(sortingMode)) {
            LibrarySort.DragAndDrop -> defaultDrawableRes
            else -> {
                if (if (sortMode?.hasInvertedSort == true) !isAscending else isAscending) {
                    R.drawable.ic_arrow_downward_24dp
                } else {
                    R.drawable.ic_arrow_upward_24dp
                }
            }
        }
    }

    private fun onCatSortClicked(
        category: Category,
        menuId: Int?,
    ) {
        val modType =
            if (menuId == null) {
                val sortingMode = category.sortingMode() ?: LibrarySort.Title
                if (category.isAscending()) {
                    sortingMode.categoryValueDescending
                } else {
                    sortingMode.categoryValue
                }
            } else {
                val sortingMode = LibrarySort.valueOf(menuId) ?: LibrarySort.Title
                if (sortingMode != LibrarySort.DragAndDrop && sortingMode == category.sortingMode()) {
                    onCatSortClicked(category, null)
                    return
                }
                sortingMode.categoryValue
            }
        adapter.libraryListener?.sortCategory(category.id!!, modType)
    }

    private fun selectAll() {
        adapter.libraryListener?.selectAll(flexibleAdapterPosition)
    }

    fun setSelection() {
        val allSelected = adapter.libraryListener?.allSelected(flexibleAdapterPosition) == true
        val drawable =
            ContextCompat.getDrawable(
                contentView.context,
                if (allSelected) R.drawable.ic_check_circle_24dp else R.drawable.ic_radio_button_unchecked_24dp,
            )
        val tintedDrawable = drawable?.mutate()
        tintedDrawable?.setTint(
            if (allSelected) {
                contentView.context.getResourceColor(R.attr.colorPrimary)
            } else {
                ContextCompat.getColor(contentView.context, R.color.gray_button)
            },
        )
        binding.checkbox.setImageDrawable(tintedDrawable)
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }
}
