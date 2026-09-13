package eu.kanade.tachiyomi.ui.source

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.BackEventCompat
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnNextLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.BrowseControllerBinding
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isIncognitoModeForSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.extension.ExtensionFilterController
import eu.kanade.tachiyomi.ui.library.ModernLibraryStatus
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.InkShelfHeaderInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.ui.recents.RecentsPresenter
import eu.kanade.tachiyomi.ui.recents.RecentsViewType
import eu.kanade.tachiyomi.ui.setting.SettingsBrowseController
import eu.kanade.tachiyomi.ui.setting.SettingsSourcesController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.browse.repos.RepoController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.requestFilePermissionsSafe
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.updateGradiantBGRadius
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.parcelize.Parcelize
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * This controller shows and manages the different catalogues enabled by the user.
 * This controller should only handle UI actions, IO actions should be done by [SourcePresenter]
 * [SourceAdapter.SourceListener] call function data on browse item click.
 */
class BrowseController :
    BaseController<BrowseControllerBinding>(),
    FlexibleAdapter.OnItemClickListener,
    SourceAdapter.SourceListener,
    RootSearchInterface,
    FloatingSearchInterface,
    InkShelfHeaderInterface,
    BottomSheetController {
    /**
     * Application preferences.
     */
    private val preferences: PreferencesHelper = Injekt.get()
    private val db: DatabaseHelper = Injekt.get()

    private var discoverCacheContext: Context? = null

    private var pickedForYouItems: List<DiscoverRecommendation> = emptyList()
    private var pickedForYouFingerprint: String? = null
    private var pickedForYouLoadInProgress = false
    private var trendingAdapter: DiscoverRecommendationAdapter? = null
    private var trendingItems: List<DiscoverRecommendation> = emptyList()
    private var trendingLoadInProgress = false
    private var newThisWeekAdapter: DiscoverRecommendationAdapter? = null
    private var newThisWeekItems: List<DiscoverRecommendation> = emptyList()
    private var newThisWeekLoadInProgress = false
    private var recentlyUpdatedAdapter: DiscoverRecommendationAdapter? = null
    private var recentlyUpdatedItems: List<DiscoverRecommendation> = emptyList()
    private var recentlyUpdatedFingerprint: String? = null
    private var recentlyUpdatedLastCheckedAt: Long = 0L
    private var recentlyUpdatedLoadInProgress = false

    // Keeps recommendation generation alive while the full Picked for You page is on top.
    // Unlike viewScope, this scope is not cancelled when Discover's view is detached.
    private val pickedForYouRefreshScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Adapter containing sources.
     */
    private var adapter: SourceAdapter? = null

    var extQuery = ""
        private set

    var headerHeight = 0

    var showingExtensions = false

    var snackbar: Snackbar? = null

    private var ogRadius = 0f
    private var deviceRadius = 0f to 0f
    private var lastScale = 1f

    override val mainRecycler: RecyclerView
        get() = binding.sourceRecycler

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? = view?.context?.getString(R.string.browse)

    override fun getSearchTitle(): String? = searchTitle(view?.context?.getString(R.string.sources)?.lowercase(Locale.ROOT))

    val presenter = SourcePresenter(this)

    override fun createBinding(inflater: LayoutInflater) = BrowseControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Discover is stale-while-revalidate now: restore the last finished shelves from
        // app-private storage first, then let WorkManager refresh Trending/New while the app
        // is closed. This avoids a burst of source requests during normal app startup.
        discoverCacheContext = view.context.applicationContext
        discoverCacheContext?.let { appContext ->
            InkShelfDiscoverRefreshWorker.schedule(appContext)
            if (cachedPickedForYouItems.isEmpty()) {
                cachedPickedForYouItems =
                    InkShelfDiscoverCache.load(appContext, InkShelfDiscoverCache.Section.PICKED_FOR_YOU, db)
            }
            if (cachedTrendingItems.isEmpty()) {
                cachedTrendingItems =
                    InkShelfDiscoverCache.load(appContext, InkShelfDiscoverCache.Section.TRENDING, db)
            }
            if (cachedNewThisWeekItems.isEmpty()) {
                cachedNewThisWeekItems =
                    InkShelfDiscoverCache.load(appContext, InkShelfDiscoverCache.Section.NEW_THIS_WEEK, db)
            }
        }

        // Modern Browse landing page: one search field, everything else behind Manage Sources.
        binding.browseSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitModernBrowseSearch()
                true
            } else {
                false
            }
        }
        binding.browseSearchLayout.setEndIconOnClickListener { submitModernBrowseSearch() }
        binding.popularSearchBatman.setOnClickListener { runQuickSearch("Batman") }
        binding.popularSearchSpiderman.setOnClickListener { runQuickSearch("Spider-Man") }
        binding.popularSearchXmen.setOnClickListener { runQuickSearch("X-Men") }
        binding.popularSearchHorror.setOnClickListener { runQuickSearch("Horror") }
        binding.popularSearchScifi.setOnClickListener { runQuickSearch("Sci-Fi") }
        binding.discoverMenuButton.setOnClickListener { showModernManageSourcesMenu() }

        // Discover now uses a single teaser banner that opens the full Picked for You page.
        binding.pickedForYouBannerCard.setOnClickListener {
            if (pickedForYouItems.isNotEmpty()) openPickedForYouPage()
        }
        if (pickedForYouItems.isEmpty() && cachedPickedForYouItems.isNotEmpty()) {
            pickedForYouItems = cachedPickedForYouItems
            pickedForYouFingerprint = cachedPickedForYouFingerprint
        }
        if (pickedForYouItems.isNotEmpty()) {
            showPickedForYou(pickedForYouItems, animate = false)
        }
        // Picked for You is generated once per app process. Switching away from Discover,
        // opening a comic, or recreating this controller reuses the in-memory shelf instead
        // of hitting the sources again. A full app restart clears the process cache and allows
        // a fresh recommendation pass.
        if (pickedForYouItems.isEmpty() && cachedPickedForYouItems.isEmpty()) {
            loadPickedForYou()
        }

        trendingAdapter = DiscoverRecommendationAdapter(::openPickedForYouManga)
        binding.trendingRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        binding.trendingRecycler.adapter = trendingAdapter
        if (trendingItems.isEmpty() && cachedTrendingItems.isNotEmpty()) {
            trendingItems = cachedTrendingItems
        }
        if (trendingItems.isNotEmpty()) {
            showTrending(trendingItems, animate = false)
        }
        loadTrending(force = trendingItems.isEmpty())

        newThisWeekAdapter = DiscoverRecommendationAdapter(::openPickedForYouManga)
        binding.newThisWeekRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        binding.newThisWeekRecycler.adapter = newThisWeekAdapter
        if (newThisWeekItems.isEmpty() && cachedNewThisWeekItems.isNotEmpty()) {
            newThisWeekItems = cachedNewThisWeekItems
        }
        if (newThisWeekItems.isNotEmpty()) {
            showNewThisWeek(newThisWeekItems, animate = false)
        }
        loadNewThisWeek(force = newThisWeekItems.isEmpty())

        recentlyUpdatedAdapter = DiscoverRecommendationAdapter(::openPickedForYouManga)
        binding.recentlyUpdatedRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        binding.recentlyUpdatedRecycler.adapter = recentlyUpdatedAdapter
        binding.recentlyUpdatedSeeAll.setOnClickListener {
            // Match More -> Updates exactly: open the Recents Updates controller on top
            // of Discover rather than trying to select the now-hidden Recents nav item.
            router.pushController(
                RecentsController(launchWithType = RecentsViewType.Updates).withFadeTransaction(),
            )
        }
        if (recentlyUpdatedItems.isEmpty() && cachedRecentlyUpdatedItems.isNotEmpty()) {
            recentlyUpdatedItems = cachedRecentlyUpdatedItems
            recentlyUpdatedFingerprint = cachedRecentlyUpdatedFingerprint
        }
        if (recentlyUpdatedItems.isNotEmpty()) {
            showRecentlyUpdated(recentlyUpdatedItems, animate = false)
        }
        loadRecentlyUpdated(force = recentlyUpdatedItems.isEmpty())

        val isReturning = adapter != null
        adapter = SourceAdapter(this)
        // Create binding.sourceRecycler and set adapter.
        binding.sourceRecycler.layoutManager = LinearLayoutManagerAccurateOffset(view.context)

        binding.sourceRecycler.adapter = adapter
        binding.sourceRecycler.addItemDecoration(SourceDividerItemDecoration(view.context))
        adapter?.isSwipeEnabled = true
        adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        scrollViewWith(
            binding.sourceRecycler,
            afterInsets = {
                headerHeight = binding.sourceRecycler.paddingTop
                // Discover shares the measured header/status-bar inset with source management.
                binding.browseContent.updatePaddingRelative(top = headerHeight)
                binding.sourceRecycler.updatePaddingRelative(
                    bottom = (activityBinding?.bottomNav?.height ?: it.getBottomGestureInsets()) + 58.spToPx,
                )
                if (activityBinding?.bottomNav == null) {
                    setBottomPadding()
                }
                deviceRadius =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val wInsets = it.toWindowInsets()
                        val lCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                        val rCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                        (lCorner?.radius?.toFloat() ?: 0f) to (rCorner?.radius?.toFloat() ?: 0f)
                    } else {
                        ogRadius to ogRadius
                    }
            },
            onBottomNavUpdate = {
                setBottomPadding()
            },
        )
        // Use the initial inset too, before the first window-insets callback arrives.
        binding.browseContent.updatePaddingRelative(top = binding.sourceRecycler.paddingTop)
        if (!isReturning) {
            activityBinding?.appBar?.lockYPos = true
        }
        binding.sourceRecycler.post {
            setBottomSheetTabs(
                if (binding.bottomSheet.root.sheetBehavior
                        .isCollapsed()
                ) {
                    0f
                } else {
                    1f
                },
            )
            binding.sourceRecycler.updatePaddingRelative(
                bottom = (activityBinding?.bottomNav?.height ?: 0) + 58.spToPx,
            )
            updateTitleAndMenu()
        }

        requestFilePermissionsSafe(301, preferences)
        binding.bottomSheet.root.onCreate(this)

        preferences
            .extensionInstaller()
            .asFlow()
            .drop(1)
            .onEach {
                binding.bottomSheet.root.setCanInstallPrivately(it == ExtensionInstaller.PRIVATE)
            }.launchIn(viewScope)

        binding.bottomSheet.root.sheetBehavior
            ?.isGestureInsetBottomIgnored = true

        binding.bottomSheet.root.sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior
                .BottomSheetCallback() {
                override fun onSlide(
                    bottomSheet: View,
                    progress: Float,
                ) {
                    val oldShow = showingExtensions
                    showingExtensions = progress > 0.92f
                    if (oldShow != showingExtensions) {
                        updateTitleAndMenu()
                        (activity as? MainActivity)?.reEnableBackPressedCallBack()
                    }
                    binding.bottomSheet.root.apply {
                        if (lastScale != 1f && scaleY != 1f) {
                            val scaleProgress = ((1f - progress) * (1f - lastScale)) + lastScale
                            scaleX = scaleProgress
                            scaleY = scaleProgress
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = scaleProgress
                            }
                        }
                    }
                    binding.bottomSheet.sheetToolbar.isVisible = true
                    setBottomSheetTabs(max(0f, progress))
                }

                override fun onStateChanged(
                    p0: View,
                    state: Int,
                ) {
                    if (state == BottomSheetBehavior.STATE_SETTLING) {
                        binding.bottomSheet.root.updatedNestedRecyclers()
                    } else if (state == BottomSheetBehavior.STATE_EXPANDED && binding.bottomSheet.root.isExpanding) {
                        binding.bottomSheet.root.updatedNestedRecyclers()
                        binding.bottomSheet.root.isExpanding = false
                    }

                    binding.bottomSheet.root.apply {
                        if ((
                                state == BottomSheetBehavior.STATE_COLLAPSED ||
                                    state == BottomSheetBehavior.STATE_EXPANDED ||
                                    state == BottomSheetBehavior.STATE_HIDDEN
                            ) &&
                            scaleY != 1f
                        ) {
                            scaleX = 1f
                            scaleY = 1f
                            pivotY = 0f
                            translationX = 0f
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = 1f
                            }
                            lastScale = 1f
                        }
                    }

                    val extBottomSheet = binding.bottomSheet.root
                    if (state == BottomSheetBehavior.STATE_EXPANDED ||
                        state == BottomSheetBehavior.STATE_COLLAPSED
                    ) {
                        binding.bottomSheet.root.sheetBehavior
                            ?.isDraggable = true
                        showingExtensions = state == BottomSheetBehavior.STATE_EXPANDED
                        binding.bottomSheet.sheetToolbar.isVisible = showingExtensions
                        updateTitleAndMenu()
                        if (state == BottomSheetBehavior.STATE_EXPANDED) {
                            extBottomSheet.fetchOnlineExtensionsIfNeeded()
                        } else {
                            extBottomSheet.shouldCallApi = true
                        }
                    }

                    retainViewMode =
                        if (state == BottomSheetBehavior.STATE_EXPANDED) {
                            RetainViewMode.RETAIN_DETACH
                        } else {
                            RetainViewMode.RELEASE_DETACH
                        }
                    binding.bottomSheet.sheetLayout.isClickable = state == BottomSheetBehavior.STATE_COLLAPSED
                    binding.bottomSheet.sheetLayout.isFocusable = state == BottomSheetBehavior.STATE_COLLAPSED
                    if (state == BottomSheetBehavior.STATE_COLLAPSED || state == BottomSheetBehavior.STATE_EXPANDED) {
                        setBottomSheetTabs(if (state == BottomSheetBehavior.STATE_COLLAPSED) 0f else 1f)
                    }
                }
            },
        )

        if (showingExtensions) {
            binding.bottomSheet.root.sheetBehavior
                ?.expand()
        }
        ogRadius = view.resources.getDimension(R.dimen.rounded_radius)

        setSheetToolbar()
        presenter.onCreate()
        if (presenter.sourceItems.isNotEmpty()) {
            setSources(presenter.sourceItems, presenter.lastUsedItem)
        } else {
            binding.sourceRecycler.checkHeightThen {
                binding.sourceRecycler.scrollToPosition(0)
            }
        }
    }

    private fun updateSheetMenu() {
        binding.bottomSheet.sheetToolbar.title =
            if (binding.bottomSheet.tabs.selectedTabPosition != 0) {
                binding.bottomSheet.root.currentSourceTitle
                    ?: view?.context?.getString(R.string.source_migration)
            } else {
                view?.context?.getString(R.string.extensions)
            }
        val onExtensionTab = binding.bottomSheet.tabs.selectedTabPosition == 0
        if (binding.bottomSheet.sheetToolbar.menu
                .findItem(if (onExtensionTab) R.id.action_search else R.id.action_migration_guide) !=
            null
        ) {
            return
        }
        val oldSearchView =
            binding.bottomSheet.sheetToolbar.menu
                .findItem(R.id.action_search)
                ?.actionView as? SearchView
        oldSearchView?.setOnQueryTextListener(null)
        binding.bottomSheet.sheetToolbar.menu
            .clear()
        binding.bottomSheet.sheetToolbar.inflateMenu(
            if (binding.bottomSheet.tabs.selectedTabPosition == 0) {
                R.menu.extension_main
            } else {
                R.menu.migration_main
            },
        )

        val id =
            when (PreferenceValues.MigrationSourceOrder.fromPreference(preferences)) {
                PreferenceValues.MigrationSourceOrder.Alphabetically -> R.id.action_sort_alpha
                PreferenceValues.MigrationSourceOrder.MostEntries -> R.id.action_sort_largest
                PreferenceValues.MigrationSourceOrder.Obsolete -> R.id.action_sort_obsolete
            }
        binding.bottomSheet.sheetToolbar.menu
            .findItem(id)
            ?.isChecked = true

        // Initialize search option.
        binding.bottomSheet.sheetToolbar.menu.findItem(R.id.action_search)?.let { searchItem ->
            val searchView = searchItem.actionView as SearchView

            // Change hint to show global search.
            searchView.queryHint = view?.context?.getString(R.string.search_extensions)
            if (extQuery.isNotEmpty()) {
                searchView.setOnQueryTextListener(null)
                searchItem.expandActionView()
                searchView.setQuery(extQuery, true)
                searchView.clearFocus()
            } else {
                searchItem.collapseActionView()
            }
            // Create query listener which opens the global search view.
            setOnQueryTextChangeListener(searchView) {
                extQuery = it ?: ""
                binding.bottomSheet.root.drawExtensions()
                true
            }
        }
    }

    private fun setSheetToolbar() {
        binding.bottomSheet.sheetToolbar.setOnMenuItemClickListener { item ->
            val sorting =
                when (item.itemId) {
                    R.id.action_sort_alpha -> PreferenceValues.MigrationSourceOrder.Alphabetically
                    R.id.action_sort_largest -> PreferenceValues.MigrationSourceOrder.MostEntries
                    R.id.action_sort_obsolete -> PreferenceValues.MigrationSourceOrder.Obsolete
                    else -> null
                }
            if (sorting != null) {
                preferences.migrationSourceOrder().set(sorting.value)
                binding.bottomSheet.root.presenter
                    .refreshMigrations()
                item.isChecked = true
                return@setOnMenuItemClickListener true
            }
            when (item.itemId) {
                // Initialize option to open catalogue settings.
                R.id.action_filter -> {
                    router.pushController(ExtensionFilterController().withFadeTransaction())
                }
                R.id.action_migration_guide -> {
                    activity?.openInBrowser(HELP_URL)
                }
                R.id.action_sources_settings -> {
                    router.pushController(SettingsBrowseController().withFadeTransaction())
                }
                R.id.action_ext_repos -> {
                    router.pushController(RepoController().withFadeTransaction())
                }
            }
            return@setOnMenuItemClickListener true
        }
        binding.bottomSheet.sheetToolbar.setNavigationOnClickListener {
            binding.bottomSheet.root.sheetBehavior
                ?.collapse()
        }
        updateSheetMenu()
    }

    fun updateTitleAndMenu() {
        if (isControllerVisible) {
            val activity = (activity as? MainActivity) ?: return
            activityBinding?.appBar?.isInvisible = showingExtensions
            (activity as? MainActivity)?.setStatusBarColorTransparent(showingExtensions)
            updateSheetMenu()
        }
    }

    fun setBottomSheetTabs(progress: Float) {
        val bottomSheet = binding.bottomSheet.root
        val halfStepProgress = (max(0.5f, progress) - 0.5f) * 2
        binding.bottomSheet.tabs.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin =
                (
                    (
                        activityBinding
                            ?.appBar
                            ?.paddingTop
                            ?.minus(9f.dpToPx)
                            ?.plus(toolbarHeight ?: 0) ?: 0f
                    ) * halfStepProgress
                ).toInt()
        }
        binding.bottomSheet.pill.alpha = (1 - progress) * 0.25f
        binding.bottomSheet.sheetToolbar.alpha = progress
        if (isControllerVisible) {
            activityBinding?.appBar?.alpha = (1 - progress * 3) + 0.5f
        }

        binding.bottomSheet.root.updateGradiantBGRadius(
            ogRadius,
            deviceRadius,
            progress,
            binding.bottomSheet.sheetLayout,
        )

        val selectedColor =
            ColorUtils.setAlphaComponent(
                bottomSheet.context.getResourceColor(R.attr.tabBarIconColor),
                (progress * 255).toInt(),
            )
        val unselectedColor =
            ColorUtils.setAlphaComponent(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                153,
            )
        binding.bottomSheet.pager.alpha = progress * 10
        binding.bottomSheet.tabs.setSelectedTabIndicatorColor(selectedColor)
        binding.bottomSheet.tabs.setTabTextColors(
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                unselectedColor,
                progress,
            ),
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                selectedColor,
                progress,
            ),
        )

        /*binding.bottomSheet.sheetLayout.backgroundTintList = ColorStateList.valueOf(
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.colorSurfaceContainer),
                bottomSheet.context.getResourceColor(R.attr.colorSurface),
                progress
            )
        )*/
    }

    private fun setBottomPadding() {
        val bottomBar = activityBinding?.bottomNav
        val pad = bottomBar?.translationY?.minus(bottomBar.height) ?: 0f
        // Keep Extensions/Migration fully hidden until Manage Sources opens them.
        binding.bottomSheet.root.sheetBehavior
            ?.peekHeight = 0
        binding.bottomSheet.root.extensionFrameLayout?.binding?.fastScroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -pad.toInt()
        }
        binding.bottomSheet.root.migrationFrameLayout?.binding?.fastScroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -pad.toInt()
        }
        binding.sourceRecycler.updatePaddingRelative(
            bottom =
                (
                    activityBinding?.bottomNav?.height
                        ?: view?.rootWindowInsetsCompat?.getBottomGestureInsets() ?: 0
                ) + 58.spToPx,
        )
    }

    override fun showSheet() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.sheetBehavior
            ?.expand()
    }

    fun updateAllPendingExtensions() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.updateAllPendingExtensions()
    }

    override fun hideSheet() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.sheetBehavior
            ?.collapse()
    }

    override fun toggleSheet() {
        if (!binding.bottomSheet.root.sheetBehavior
                .isCollapsed()
        ) {
            binding.bottomSheet.root.sheetBehavior
                ?.collapse()
        } else {
            binding.bottomSheet.root.sheetBehavior
                ?.expand()
        }
    }

    override fun canStillGoBack(): Boolean = showingExtensions

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior
                ?.startBackProgress(backEvent)
        }
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior
                ?.updateBackProgress(backEvent)
        } else {
            super.handleOnBackProgressed(backEvent)
        }
    }

    override fun handleOnBackCancelled() {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior
                ?.cancelBackProgress()
        } else {
            super.handleOnBackCancelled()
        }
    }

    override fun handleBack(): Boolean {
        if (showingExtensions) {
            if (binding.bottomSheet.root.canGoBack()) {
                lastScale = binding.bottomSheet.root.scaleX
                binding.bottomSheet.root.sheetBehavior
                    ?.collapse()
            }
            return true
        }
        return false
    }

    override fun onDestroyView(view: View) {
        // Detach view-owned objects before dropping controller references. This prevents
        // recycled card holders and click listeners from keeping the destroyed Discover
        // view tree alive across navigation/configuration changes.
        if (isBindingInitialized) {
            binding.trendingRecycler.adapter = null
            binding.newThisWeekRecycler.adapter = null
            binding.recentlyUpdatedRecycler.adapter = null
            binding.sourceRecycler.adapter = null

            binding.pickedForYouBannerCard.setOnClickListener(null)
            binding.browseSearchInput.setOnEditorActionListener(null)
            binding.browseSearchLayout.setEndIconOnClickListener(null)
            binding.popularSearchBatman.setOnClickListener(null)
            binding.popularSearchSpiderman.setOnClickListener(null)
            binding.popularSearchXmen.setOnClickListener(null)
            binding.popularSearchHorror.setOnClickListener(null)
            binding.popularSearchScifi.setOnClickListener(null)
            binding.discoverMenuButton.setOnClickListener(null)
            binding.recentlyUpdatedSeeAll.setOnClickListener(null)
        }

        adapter = null
        trendingAdapter = null
        newThisWeekAdapter = null
        recentlyUpdatedAdapter = null
        binding.bottomSheet.root.onDestroy()
        super.onDestroyView(view)
    }

    override fun onDestroy() {
        pickedForYouRefreshScope.cancel()
        super.onDestroy()
        presenter.onDestroy()
    }

    override fun onChangeStarted(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeStarted(handler, type)
        if (!type.isPush) {
            binding.bottomSheet.root.updateExtTitle()
            binding.bottomSheet.root.presenter
                .refreshExtensions()
            presenter.updateSources()
            if (type.isEnter && isControllerVisible) {
                activityBinding?.appBar?.doOnNextLayout {
                    activityBinding?.appBar?.y = 0f
                    activityBinding?.appBar?.updateAppBarAfterY(binding.sourceRecycler)
                }
                updateSheetMenu()
            }
        }
        if (!type.isEnter) {
            clearModernBrowseSearchState(clearText = true)
            binding.bottomSheet.root.canExpand = false
            activityBinding?.appBar?.alpha = 1f
            activityBinding?.appBar?.isInvisible = false
            binding.bottomSheet.sheetToolbar.menu.findItem(R.id.action_search)?.let { searchItem ->
                val searchView = searchItem.actionView as SearchView
                searchView.clearFocus()
            }
        } else {
            binding.bottomSheet.root.presenter
                .refreshMigrations()
            updateTitleAndMenu()
        }
        setBottomPadding()
    }

    override fun onChangeEnded(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeEnded(handler, type)
        if (type.isEnter) {
            binding.bottomSheet.root.canExpand = true
            setBottomPadding()
            updateTitleAndMenu()
            if (type == ControllerChangeType.POP_ENTER) {
                if (forcePickedForYouRefresh) {
                    // Manual refresh from the dedicated Picked for You page is the one exception
                    // to the once-per-process rule. Clear the controller copy as well as the
                    // process cache, then rebuild the shelf on Discover.
                    forcePickedForYouRefresh = false
                    pickedForYouItems = emptyList()
                    pickedForYouFingerprint = null
                    loadPickedForYou()
                }
                // Normal back-navigation keeps Picked for You stable for the whole app session.
                loadRecentlyUpdated(force = true)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!isBindingInitialized) return
        binding.bottomSheet.root.presenter
            .refreshExtensions()
        binding.bottomSheet.root.presenter
            .refreshMigrations()
        setBottomPadding()
        if (showingExtensions) {
            updateSheetMenu()
        }
    }

    override fun onItemClick(
        view: View,
        position: Int,
    ): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        val source = item.source
        // Open the catalogue view.
        openCatalogue(source, BrowseSourceController(source))
        return false
    }

    fun hideCatalogue(position: Int) {
        val source = (adapter?.getItem(position) as? SourceItem)?.source ?: return
        val current = preferences.hiddenSources().get()
        preferences.hiddenSources().set(current + source.id.toString())

        presenter.updateSources()

        snackbar =
            view?.snack(R.string.source_hidden, Snackbar.LENGTH_INDEFINITE) {
                anchorView = binding.bottomSheet.root
                setAction(R.string.undo) {
                    val newCurrent = preferences.hiddenSources().get()
                    preferences.hiddenSources().set(newCurrent - source.id.toString())
                    presenter.updateSources()
                }
            }
        (activity as? MainActivity)?.setUndoSnackBar(snackbar)
    }

    private fun pinCatalogue(
        source: Source,
        isPinned: Boolean,
    ) {
        val current = preferences.pinnedCatalogues().get()
        if (isPinned) {
            preferences.pinnedCatalogues().set(current - source.id.toString())
        } else {
            preferences.pinnedCatalogues().set(current + source.id.toString())
        }

        presenter.updateSources()
    }

    /**
     * Called when browse is clicked in [SourceAdapter]
     */
    override fun onPinClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        val isPinned =
            item.isPinned ?: item.header?.code?.equals(SourcePresenter.PINNED_KEY)
                ?: false
        pinCatalogue(item.source, isPinned)
    }

    /**
     * Called when latest is clicked in [SourceAdapter]
     */
    override fun onLatestClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        openCatalogue(item.source, BrowseSourceController(item.source, useLatest = true))
    }

    /**
     * Opens a catalogue with the given controller.
     */
    private fun openCatalogue(
        source: CatalogueSource,
        controller: BrowseSourceController,
    ) {
        if (!isIncognitoModeForSource(source.id, preferences)) {
            preferences.lastUsedCatalogueSource().set(source.id)
            if (source !is LocalSource) {
                val list = preferences.lastUsedSources().get().toMutableSet()
                list.removeAll { it.startsWith("${source.id}:") }
                list.add("${source.id}:${Date().time}")
                val sortedList =
                    list
                        .filter { it.split(":").size == 2 }
                        .sortedByDescending { it.split(":").last().toLong() }
                preferences
                    .lastUsedSources()
                    .set(sortedList.take(2).toSet())
            }
        }
        router.pushController(controller.withFadeTransaction())
    }

    override fun showFloatingBar(): Boolean = false

    override fun expandSearch() {
        if (showingExtensions) {
            binding.bottomSheet.root.sheetBehavior
                ?.collapse()
        }
        binding.browseSearchInput.requestFocus()
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        // The modern Browse screen owns its search field, so no duplicate toolbar actions are added.
    }

    private enum class RecommendationKind(
        val label: String,
    ) {
        DIRECT_MATCH("Direct match"),
        ADJACENT_PICK("Adjacent pick"),
        CREATOR_CONNECTION("Creator connection"),
        NEXT_STEP("Next step"),
        HIDDEN_GEM("Hidden gem"),
        WILDCARD("Wildcard"),
    }

    private data class TasteExample(
        val title: String,
        val weight: Int,
        val active: Boolean,
    )

    private data class PickCandidate(
        val source: CatalogueSource,
        val manga: SManga,
        val score: Int,
        val reason: String,
        val kind: RecommendationKind,
    )

    private data class LibraryTasteProfile(
        val genreWeights: Map<String, Int>,
        val creatorWeights: Map<String, Int>,
        val titleTokenWeights: Map<String, Int>,
        val keywordWeights: Map<String, Int>,
        val activeGenres: Map<String, Int>,
        val activeCreators: Map<String, Int>,
        val activeTitleTokens: Map<String, Int>,
        val activeKeywords: Map<String, Int>,
        val genreExamples: Map<String, TasteExample>,
        val creatorExamples: Map<String, TasteExample>,
        val titleTokenExamples: Map<String, TasteExample>,
        val keywordExamples: Map<String, TasteExample>,
    )

    private data class RecommendationMatch(
        val score: Int,
        val reason: String,
        val kind: RecommendationKind,
    )

    /**
     * Builds a whole-Library taste profile, then deliberately creates a mixed shelf instead
     * of ten variations of the same recommendation. Reading/recent titles carry the most
     * weight, Completed is a strong secondary signal, and On Hold/Plan To Read are lighter.
     */
    private fun loadPickedForYou(
        background: Boolean = false,
        onComplete: ((List<DiscoverRecommendation>) -> Unit)? = null,
    ) {
        if (pickedForYouLoadInProgress) return
        if (!background && !isBindingInitialized) return

        // If the app-start warm-up is already generating the shared recommendation cache,
        // do not launch a duplicate set of source requests from Discover. Keep the banner
        // visible and pick up the warmed cache as soon as it lands.
        if (!background && startupPickedForYouPrewarmInProgress && cachedPickedForYouItems.isEmpty()) {
            binding.pickedForYouSection.isVisible = true
            binding.pickedForYouProgress.isVisible = false
            binding.pickedForYouBannerCard.isVisible = true
            binding.pickedForYouBannerCard.isEnabled = false
            awaitStartupPickedForYouPrewarm()
            return
        }

        if (!background && isBindingInitialized) {
            if (pickedForYouItems.isEmpty()) {
                // The Discover teaser is static artwork, so show it immediately while the
                // recommendation batch warms quietly in the background. No floating spinner.
                binding.pickedForYouSection.isVisible = true
                binding.pickedForYouProgress.isVisible = false
                binding.pickedForYouBannerCard.isVisible = true
                binding.pickedForYouBannerCard.isEnabled = false
            } else {
                showPickedForYou(pickedForYouItems, animate = false)
            }
        }

        pickedForYouLoadInProgress = true
        val recommendationScope = if (background) pickedForYouRefreshScope else viewScope
        recommendationScope.launchIO {
            try {
                val recentReads =
                    runCatching { RecentsPresenter.getRecentManga(includeRead = true, customAmount = 12) }
                        .getOrDefault(emptyList())

                val libraryRows = db.getLibraryMangas().executeAsBlocking()
                val libraryManga = libraryRows.distinctBy { it.id ?: it.url.hashCode().toLong() }
                val libraryIds = libraryManga.mapNotNull { it.id }.toSet()

                val categoryNamesById =
                    db
                        .getCategories()
                        .executeAsBlocking()
                        .mapNotNull { category ->
                            category.id?.let { id -> id to ModernLibraryStatus.normalize(category.name) }
                        }.toMap()

                val statusesByMangaId = mutableMapOf<Long, MutableSet<String>>()
                libraryRows.forEach { manga ->
                    val mangaId = manga.id ?: return@forEach
                    val status = categoryNamesById[manga.category] ?: return@forEach
                    if (status in ModernLibraryStatus.ALL) {
                        statusesByMangaId.getOrPut(mangaId) { mutableSetOf() }.add(status)
                    }
                }

                val recentLibraryReads =
                    recentReads
                        .filter { (manga, _) -> manga.id == null || manga.id in libraryIds }
                        .distinctBy { (manga, _) -> manga.id ?: manga.url.hashCode().toLong() }
                        .take(10)
                val recentRankByMangaId =
                    recentLibraryReads
                        .mapIndexedNotNull { index, (manga, _) -> manga.id?.let { it to index } }
                        .toMap()

                val libraryFingerprint =
                    libraryManga
                        .sortedBy { it.id ?: Long.MAX_VALUE }
                        .joinToString("|") { manga ->
                            val id = manga.id ?: manga.url.hashCode().toLong()
                            val statuses = statusesByMangaId[id].orEmpty().sorted().joinToString(",")
                            "$id:${manga.source}:$statuses:${manga.last_update}:${manga.genre.hashCode()}:${manga.author.hashCode()}"
                        }
                val recentFingerprint =
                    recentLibraryReads.joinToString("|") { (manga, lastRead) ->
                        "${manga.id ?: manga.url.hashCode().toLong()}:${manga.source}:$lastRead"
                    }
                val tasteFingerprint = "$libraryFingerprint#$recentFingerprint"

                val cachedItems =
                    when {
                        tasteFingerprint == pickedForYouFingerprint && pickedForYouItems.isNotEmpty() -> pickedForYouItems
                        tasteFingerprint == cachedPickedForYouFingerprint && cachedPickedForYouItems.isNotEmpty() -> cachedPickedForYouItems
                        else -> emptyList()
                    }

                if (cachedItems.isNotEmpty()) {
                    withUIContext {
                        pickedForYouFingerprint = tasteFingerprint
                        pickedForYouItems = cachedItems
                        if (!background && isBindingInitialized) {
                            showPickedForYou(cachedItems, animate = false)
                        }
                        onComplete?.invoke(cachedItems)
                    }
                    return@launchIO
                }

                val profile =
                    buildLibraryTasteProfile(
                        library = libraryManga,
                        statusesByMangaId = statusesByMangaId,
                        recentRankByMangaId = recentRankByMangaId,
                    )

                val libraryTitles = libraryManga.map { normalizePickedForYouTitle(it.title) }.toSet()

                val sourceWeights = mutableMapOf<Long, Int>()
                libraryManga.forEach { manga ->
                    val id = manga.id
                    val statuses = id?.let { statusesByMangaId[it] }.orEmpty()
                    val baseWeight =
                        when {
                            statuses.any { it in ModernLibraryStatus.READING } -> 8
                            statuses.any { it in ModernLibraryStatus.COMPLETED } -> 4
                            statuses.any { it in ModernLibraryStatus.ON_HOLD } -> 2
                            statuses.any { it in ModernLibraryStatus.PLAN_TO_READ } -> 1
                            else -> 2
                        }
                    val recentBonus = id?.let { recentRankByMangaId[it] }?.let { (5 - it).coerceAtLeast(1) } ?: 0
                    sourceWeights[manga.source] = (sourceWeights[manga.source] ?: 0) + baseWeight + recentBonus
                }

                val preferredSources =
                    sourceWeights
                        .entries
                        .sortedByDescending { it.value }
                        .mapNotNull { (id, _) -> presenter.sources.firstOrNull { it.id == id } }
                        .filterNot { it.id == LocalSource.ID }

                val recommendationSources =
                    (preferredSources + presenter.sources)
                        .distinctBy { it.id }
                        .filterNot { it.id == LocalSource.ID }
                        .take(4)

                val candidates =
                    recommendationSources
                        .mapIndexed { sourceOrder, source ->
                            async {
                                val page =
                                    withTimeoutOrNull(
                                        10_000L,
                                    ) {
                                        // Fetch Popular pages 1-4 concurrently. awaitAll() preserves
                                        // page order, so rank still favours earlier Popular pages while
                                        // avoiding four sequential network waits per source.
                                        (1..4)
                                            .map { popularPage ->
                                                async {
                                                    runCatching {
                                                        source.getPopularManga(popularPage)
                                                    }.getOrNull()
                                                        ?.mangas
                                                        .orEmpty()
                                                }
                                            }.awaitAll()
                                            .flatten()
                                            .distinctBy { manga ->
                                                normalizePickedForYouTitle(manga.title)
                                            }
                                            .take(40)
                                    }

                                page
                                    ?.take(40)
                                    ?.mapIndexed { rank, manga ->
                                        async {
                                            // Detailed metadata makes the reasons much better, but asking every
                                            // source for details on every card made first load unnecessarily slow.
                                            // Enrich the strongest six per source; keep the rest as a fast fallback
                                            // pool so the shelf can still fill if detailed matches are sparse.
                                            val detailed =
                                                if (rank < 6) {
                                                    enrichRecommendationCandidate(source, manga)
                                                } else {
                                                    manga
                                                }
                                            if (detailed.thumbnail_url.isNullOrBlank()) return@async null

                                            val match = scoreRecommendationAgainstLibrary(detailed, profile, rank)
                                            if (match.score <= 0) return@async null

                                            PickCandidate(
                                                source = source,
                                                manga = detailed,
                                                score = match.score - (sourceOrder * 25) - rank,
                                                reason = match.reason,
                                                kind = match.kind,
                                            )
                                        }
                                    }?.awaitAll()
                                    ?.filterNotNull()
                                    .orEmpty()
                            }
                        }.awaitAll()
                        .flatten()
                        .sortedByDescending { it.score }

                val seenTitles = mutableSetOf<String>()
                val uniqueCandidates =
                    candidates.filter { candidate ->
                        val title = normalizePickedForYouTitle(candidate.manga.title)
                        title !in libraryTitles && seenTitles.add(title)
                    }

                // A manual refresh creates a fresh recommendation session. Prefer titles that
                // were not present in the previous nine, but keep the old batch as a fallback
                // pool so a small source/candidate set never leaves the shelf empty.
                val previousPickKeys = pendingPickedForYouExclusions
                val refreshSeed = pendingPickedForYouRefreshSeed

                val freshCandidates =
                    if (previousPickKeys.isEmpty()) {
                        uniqueCandidates
                    } else {
                        uniqueCandidates.filterNot { candidate ->
                            normalizePickedForYouTitle(candidate.manga.title) in previousPickKeys
                        }
                    }

                val freshPicks =
                    selectRecommendationMix(
                        candidates = freshCandidates,
                        refreshSeed = refreshSeed,
                    )

                val targetSize = minOf(9, uniqueCandidates.size)
                val freshKeys =
                    freshPicks
                        .map { candidate ->
                            normalizePickedForYouTitle(candidate.manga.title)
                        }.toSet()

                val repeatCandidates =
                    if (freshPicks.size >= targetSize || previousPickKeys.isEmpty()) {
                        emptyList()
                    } else {
                        uniqueCandidates.filter { candidate ->
                            val key = normalizePickedForYouTitle(candidate.manga.title)
                            key in previousPickKeys && key !in freshKeys
                        }
                    }

                val fallbackPicks =
                    if (repeatCandidates.isEmpty()) {
                        emptyList()
                    } else {
                        selectRecommendationMix(
                            candidates = repeatCandidates,
                            refreshSeed = refreshSeed xor 0x5A5A5A5AL,
                        ).take(targetSize - freshPicks.size)
                    }

                val selectedCandidates =
                    (freshPicks + fallbackPicks)
                        .take(targetSize)

                val picks =
                    selectedCandidates
                        .mapNotNull { candidate ->
                            runCatching {
                                val manga = networkToLocalManga(candidate.manga, candidate.source.id)
                                DiscoverRecommendation(manga, candidate.reason)
                            }.getOrNull()
                        }

                cachedPickedForYouFingerprint = tasteFingerprint
                cachedPickedForYouItems = picks

                discoverCacheContext?.let { appContext ->
                    InkShelfDiscoverCache.save(appContext, InkShelfDiscoverCache.Section.PICKED_FOR_YOU, picks)
                }

                // The exclusion list and seed apply to one manual refresh only.
                pendingPickedForYouExclusions = emptySet()
                pendingPickedForYouRefreshSeed = 0L

                withUIContext {
                    pickedForYouFingerprint = tasteFingerprint
                    pickedForYouItems = picks
                    if (!background && isBindingInitialized) {
                        showPickedForYou(picks, animate = true)
                    }
                    onComplete?.invoke(picks)
                }
            } catch (_: Exception) {
                withUIContext {
                    onComplete?.invoke(emptyList())
                }
            } finally {
                pickedForYouLoadInProgress = false
            }
        }
    }

    internal fun refreshPickedForYouFromChild(
        onComplete: (List<DiscoverRecommendation>) -> Unit,
    ) {
        if (pickedForYouLoadInProgress) {
            onComplete(emptyList())
            return
        }

        requestPickedForYouRefresh()

        // Clear this controller's own copy too. Otherwise loadPickedForYou() sees the
        // previous in-memory shelf and returns it as a cache hit instead of regenerating.
        pickedForYouItems = emptyList()
        pickedForYouFingerprint = null

        // This is an in-place refresh from the child page, so Discover does not need
        // to receive POP_ENTER and we do not navigate away from Picked for You.
        forcePickedForYouRefresh = false

        loadPickedForYou(
            background = true,
            onComplete = onComplete,
        )
    }

    private fun selectRecommendationMix(
        candidates: List<PickCandidate>,
        refreshSeed: Long = 0L,
    ): List<PickCandidate> {
        if (candidates.isEmpty()) return emptyList()

        // 3 direct, 2 adjacent, then one of each specialist lane. Interleaving the lanes means
        // the first few carousel cards already feel different rather than looking cloned.
        val targetOrder =
            listOf(
                RecommendationKind.DIRECT_MATCH,
                RecommendationKind.ADJACENT_PICK,
                RecommendationKind.CREATOR_CONNECTION,
                RecommendationKind.DIRECT_MATCH,
                RecommendationKind.NEXT_STEP,
                RecommendationKind.ADJACENT_PICK,
                RecommendationKind.HIDDEN_GEM,
                RecommendationKind.DIRECT_MATCH,
                RecommendationKind.WILDCARD,
            )

        val remaining = candidates.toMutableList()
        val selected = mutableListOf<PickCandidate>()
        val sourceCounts = mutableMapOf<Long, Int>()

        fun refreshJitter(candidate: PickCandidate): Int {
            if (refreshSeed == 0L) return 0

            val identity =
                recommendationIdentity(
                    candidate.source.id,
                    candidate.manga.url,
                    candidate.manga.title,
                )

            // Tiny seeded tie-break variation. The existing recommendation score remains
            // dominant; this only stops a manual refresh from resolving near-equal choices
            // in exactly the same order every time.
            val mixed = identity.hashCode().toLong() xor refreshSeed
            return mixed.hashCode().and(0x0F)
        }

        fun adjustedScore(candidate: PickCandidate): Int =
            candidate.score +
                refreshJitter(candidate) -
                ((sourceCounts[candidate.source.id] ?: 0) * 140)

        targetOrder.forEach { kind ->
            val pick =
                remaining
                    .filter { it.kind == kind }
                    .maxByOrNull(::adjustedScore)
                    ?: return@forEach
            selected += pick
            remaining.remove(pick)
            sourceCounts[pick.source.id] = (sourceCounts[pick.source.id] ?: 0) + 1
        }

        // Fill toward the intended nine-card shelf when the exact lane mix is not available.
        // Underrepresented recommendation kinds and underused sources get a bonus, so fallback
        // cards do not simply become nine copies of the same sort of recommendation.
        val targetSize = minOf(9, candidates.size)
        while (selected.size < targetSize && remaining.isNotEmpty()) {
            val kindCounts = selected.groupingBy { it.kind }.eachCount()
            val pick =
                remaining.maxByOrNull { candidate ->
                    adjustedScore(candidate) - ((kindCounts[candidate.kind] ?: 0) * 90)
                } ?: break

            selected += pick
            remaining.remove(pick)
            sourceCounts[pick.source.id] = (sourceCounts[pick.source.id] ?: 0) + 1
        }

        return selected
    }

    private fun buildLibraryTasteProfile(
        library: List<Manga>,
        statusesByMangaId: Map<Long, Set<String>>,
        recentRankByMangaId: Map<Long, Int>,
    ): LibraryTasteProfile {
        val genreWeights = mutableMapOf<String, Int>()
        val creatorWeights = mutableMapOf<String, Int>()
        val titleTokenWeights = mutableMapOf<String, Int>()
        val keywordWeights = mutableMapOf<String, Int>()
        val activeGenres = mutableMapOf<String, Int>()
        val activeCreators = mutableMapOf<String, Int>()
        val activeTitleTokens = mutableMapOf<String, Int>()
        val activeKeywords = mutableMapOf<String, Int>()
        val genreExamples = mutableMapOf<String, TasteExample>()
        val creatorExamples = mutableMapOf<String, TasteExample>()
        val titleTokenExamples = mutableMapOf<String, TasteExample>()
        val keywordExamples = mutableMapOf<String, TasteExample>()

        fun addWeights(
            target: MutableMap<String, Int>,
            values: Set<String>,
            weight: Int,
        ) {
            values.forEach { value -> target[value] = (target[value] ?: 0) + weight }
        }

        fun rememberExamples(
            target: MutableMap<String, TasteExample>,
            values: Set<String>,
            title: String,
            weight: Int,
            active: Boolean,
        ) {
            values.forEach { value ->
                val current = target[value]
                val shouldReplace =
                    current == null ||
                        (active && !current.active) ||
                        (active == current.active && weight > current.weight)
                if (shouldReplace) target[value] = TasteExample(title, weight, active)
            }
        }

        library.forEach { manga ->
            val mangaId = manga.id
            val statuses = mangaId?.let { statusesByMangaId[it] }.orEmpty()
            val isReading = statuses.any { it in ModernLibraryStatus.READING }
            val recentRank = mangaId?.let { recentRankByMangaId[it] }
            val isActive = isReading || recentRank != null

            val shelfWeight =
                when {
                    isReading -> 8
                    statuses.any { it in ModernLibraryStatus.COMPLETED } -> 4
                    statuses.any { it in ModernLibraryStatus.ON_HOLD } -> 2
                    statuses.any { it in ModernLibraryStatus.PLAN_TO_READ } -> 1
                    else -> 2
                }
            val recentBonus = recentRank?.let { (5 - it).coerceAtLeast(1) } ?: 0
            val weight = shelfWeight + recentBonus

            val genres = extractRecommendationGenres(manga.genre).map(::normalizeRecommendationTag).toSet()
            val creators = extractRecommendationCreators(manga.author, manga.artist)
            val titleTokens = tokenizeRecommendationTitle(manga.title)
            val keywords =
                extractRecommendationKeywords(
                    manga.title,
                    manga.description,
                    manga.genre,
                )

            addWeights(genreWeights, genres, weight)
            addWeights(creatorWeights, creators, weight)
            addWeights(titleTokenWeights, titleTokens, weight)
            addWeights(keywordWeights, keywords, weight)

            rememberExamples(genreExamples, genres, manga.title, weight, isActive)
            rememberExamples(creatorExamples, creators, manga.title, weight, isActive)
            rememberExamples(titleTokenExamples, titleTokens, manga.title, weight, isActive)
            rememberExamples(keywordExamples, keywords, manga.title, weight, isActive)

            if (isActive) {
                addWeights(activeGenres, genres, weight)
                addWeights(activeCreators, creators, weight)
                addWeights(activeTitleTokens, titleTokens, weight)
                addWeights(activeKeywords, keywords, weight)
            }
        }

        return LibraryTasteProfile(
            genreWeights = genreWeights,
            creatorWeights = creatorWeights,
            titleTokenWeights = titleTokenWeights,
            keywordWeights = keywordWeights,
            activeGenres = activeGenres,
            activeCreators = activeCreators,
            activeTitleTokens = activeTitleTokens,
            activeKeywords = activeKeywords,
            genreExamples = genreExamples,
            creatorExamples = creatorExamples,
            titleTokenExamples = titleTokenExamples,
            keywordExamples = keywordExamples,
        )
    }

    private suspend fun enrichRecommendationCandidate(
        source: CatalogueSource,
        manga: SManga,
    ): SManga {
        val alreadyDetailed =
            !manga.genre.isNullOrBlank() ||
                !manga.author.isNullOrBlank() ||
                !manga.artist.isNullOrBlank() ||
                !manga.description.isNullOrBlank()
        if (alreadyDetailed) return manga

        val originalThumbnail = manga.thumbnail_url
        val detailed =
            withTimeoutOrNull(2_500L) {
                runCatching { source.getMangaDetails(manga) }.getOrNull()
            } ?: return manga

        if (detailed.thumbnail_url.isNullOrBlank()) detailed.thumbnail_url = originalThumbnail
        return detailed
    }

    private fun scoreRecommendationAgainstLibrary(
        candidate: SManga,
        profile: LibraryTasteProfile,
        sourceRank: Int,
    ): RecommendationMatch {
        val candidateGenres = extractRecommendationGenres(candidate.genre)
        val genres = candidateGenres.map(::normalizeRecommendationTag).toSet()
        val creators = extractRecommendationCreators(candidate.author, candidate.artist)
        val titleTokens = tokenizeRecommendationTitle(candidate.title)
        val keywords =
            extractRecommendationKeywords(
                candidate.title,
                candidate.description,
                candidate.genre,
            )

        val sharedCreators = creators.filter { (profile.creatorWeights[it] ?: 0) > 0 }.toSet()
        val sharedGenres = genres.filter { (profile.genreWeights[it] ?: 0) > 0 }.toSet()
        val sharedTitleTokens = titleTokens.filter { (profile.titleTokenWeights[it] ?: 0) > 0 }.toSet()
        val sharedKeywords = keywords.filter { (profile.keywordWeights[it] ?: 0) > 0 }.toSet()

        val activeCreators = sharedCreators.filter { (profile.activeCreators[it] ?: 0) > 0 }.toSet()
        val activeGenres = sharedGenres.filter { (profile.activeGenres[it] ?: 0) > 0 }.toSet()
        val activeTitleTokens = sharedTitleTokens.filter { (profile.activeTitleTokens[it] ?: 0) > 0 }.toSet()
        val activeKeywords = sharedKeywords.filter { (profile.activeKeywords[it] ?: 0) > 0 }.toSet()
        val novelGenres = genres.filter { (profile.genreWeights[it] ?: 0) == 0 }.toSet()

        val meaningfulMatch =
            sharedCreators.isNotEmpty() ||
                sharedGenres.isNotEmpty() ||
                sharedTitleTokens.isNotEmpty() ||
                sharedKeywords.size >= 2
        if (!meaningfulMatch) {
            // Some popular feeds expose very little metadata. Keep those titles as low-priority
            // wildcards rather than dropping them completely, so stronger profile matches win
            // first but the carousel can still fill to a useful size.
            return RecommendationMatch(
                score = 30 - sourceRank,
                reason =
                    "${RecommendationKind.WILDCARD.label} • " +
                        "This sits outside the strongest patterns in your library, so it is here as a deliberate change of pace.",
                kind = RecommendationKind.WILDCARD,
            )
        }

        var score = 0
        score += sharedCreators.sumOf { (profile.creatorWeights[it] ?: 0) * 45 }
        score += sharedGenres.sumOf { (profile.genreWeights[it] ?: 0) * 24 }
        score += sharedTitleTokens.sumOf { (profile.titleTokenWeights[it] ?: 0) * 32 }
        score += sharedKeywords.sumOf { (profile.keywordWeights[it] ?: 0) * 4 }
        score += activeCreators.sumOf { (profile.activeCreators[it] ?: 0) * 35 }
        score += activeGenres.sumOf { (profile.activeGenres[it] ?: 0) * 18 }
        score += activeTitleTokens.sumOf { (profile.activeTitleTokens[it] ?: 0) * 24 }
        score += activeKeywords.sumOf { (profile.activeKeywords[it] ?: 0) * 3 }

        val strongestCreator = strongestRecommendationSignal(activeCreators.ifEmpty { sharedCreators }, profile.creatorWeights)
        val strongestTitleToken =
            strongestRecommendationSignal(activeTitleTokens.ifEmpty { sharedTitleTokens }, profile.titleTokenWeights)
        val strongestGenres =
            (activeGenres.ifEmpty { sharedGenres })
                .sortedByDescending { profile.genreWeights[it] ?: 0 }
        val strongestKeywords =
            (activeKeywords.ifEmpty { sharedKeywords })
                .sortedByDescending { profile.keywordWeights[it] ?: 0 }

        val kind =
            when {
                strongestCreator != null -> RecommendationKind.CREATOR_CONNECTION
                strongestTitleToken != null && (profile.titleTokenWeights[strongestTitleToken] ?: 0) >= 6 ->
                    RecommendationKind.NEXT_STEP
                sourceRank >= 6 &&
                    sharedCreators.isEmpty() &&
                    sharedTitleTokens.isEmpty() &&
                    activeGenres.size <= 1 &&
                    sharedGenres.size <= 1 &&
                    sharedKeywords.size >= 2 -> RecommendationKind.HIDDEN_GEM
                activeGenres.size >= 2 ||
                    (sharedGenres.size >= 2 && sharedKeywords.size >= 2) ||
                    activeKeywords.size >= 4 -> RecommendationKind.DIRECT_MATCH
                sharedGenres.isNotEmpty() &&
                    (novelGenres.isNotEmpty() || sharedKeywords.size >= 2) -> RecommendationKind.ADJACENT_PICK
                else -> RecommendationKind.WILDCARD
            }

        score +=
            when (kind) {
                RecommendationKind.DIRECT_MATCH -> 180
                RecommendationKind.ADJACENT_PICK -> 95
                RecommendationKind.CREATOR_CONNECTION -> 160
                RecommendationKind.NEXT_STEP -> 140
                RecommendationKind.HIDDEN_GEM -> 70
                RecommendationKind.WILDCARD -> 20
            }

        val reason =
            buildRecommendationReason(
                kind = kind,
                candidate = candidate,
                profile = profile,
                candidateGenres = candidateGenres,
                strongestCreator = strongestCreator,
                strongestTitleToken = strongestTitleToken,
                strongestGenres = strongestGenres,
                strongestKeywords = strongestKeywords,
                novelGenres = novelGenres,
            )

        return RecommendationMatch(score, reason, kind)
    }

    private fun buildRecommendationReason(
        kind: RecommendationKind,
        candidate: SManga,
        profile: LibraryTasteProfile,
        candidateGenres: List<String>,
        strongestCreator: String?,
        strongestTitleToken: String?,
        strongestGenres: List<String>,
        strongestKeywords: List<String>,
        novelGenres: Set<String>,
    ): String {
        fun exampleForGenre(signal: String?): TasteExample? = signal?.let { profile.genreExamples[it] }
        fun exampleForCreator(signal: String?): TasteExample? = signal?.let { profile.creatorExamples[it] }
        fun exampleForTitle(signal: String?): TasteExample? = signal?.let { profile.titleTokenExamples[it] }
        fun exampleForKeyword(signal: String?): TasteExample? = signal?.let { profile.keywordExamples[it] }

        fun titleOf(example: TasteExample?): String = example?.title?.take(36).orEmpty()

        fun candidateGenreLabel(normalized: String?): String? =
            normalized?.let { target ->
                candidateGenres
                    .firstOrNull { normalizeRecommendationTag(it) == target }
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }

        val primaryGenre = strongestGenres.firstOrNull()
        val primaryGenreLabel = candidateGenreLabel(primaryGenre) ?: primaryGenre?.let(::formatRecommendationKeyword)
        val secondGenre = strongestGenres.getOrNull(1)
        val secondGenreLabel = candidateGenreLabel(secondGenre) ?: secondGenre?.let(::formatRecommendationKeyword)
        val primaryKeyword = strongestKeywords.firstOrNull()
        val novelGenre = novelGenres.firstOrNull()
        val novelGenreLabel = candidateGenreLabel(novelGenre) ?: novelGenre?.let(::formatRecommendationKeyword)

        return when (kind) {
            RecommendationKind.CREATOR_CONNECTION -> {
                val creator = strongestCreator?.let { recommendationCreatorLabel(candidate, it) } ?: "this creator"
                val example = titleOf(exampleForCreator(strongestCreator))
                if (example.isNotBlank()) {
                    "${kind.label} • $creator links this to $example, giving you a different title from a creator already in your library."
                } else {
                    "${kind.label} • $creator is already a strong creator signal in your library, so this is a creator-led pick rather than a franchise repeat."
                }
            }
            RecommendationKind.NEXT_STEP -> {
                val thread = strongestTitleToken?.let(::formatRecommendationKeyword) ?: "this series thread"
                val example = titleOf(exampleForTitle(strongestTitleToken))
                if (example.isNotBlank()) {
                    "${kind.label} • $example makes $thread a strong thread in your library; this continues it with a different title."
                } else {
                    "${kind.label} • $thread keeps appearing in your library, making this a logical continuation rather than a random match."
                }
            }
            RecommendationKind.DIRECT_MATCH -> {
                val example = titleOf(exampleForGenre(primaryGenre) ?: exampleForKeyword(primaryKeyword))
                val mix = listOfNotNull(primaryGenreLabel, secondGenreLabel).distinct().joinToString(" + ")
                when {
                    example.isNotBlank() && mix.isNotBlank() ->
                        "${kind.label} • $example anchors your $mix taste; this hits the same mix without simply repeating that title."
                    mix.isNotBlank() ->
                        "${kind.label} • $mix is one of the strongest patterns across your recent reading and wider library."
                    else ->
                        "${kind.label} • Several strong themes overlap with what you are actively reading, not just one matching tag."
                }
            }
            RecommendationKind.ADJACENT_PICK -> {
                val example = titleOf(exampleForGenre(primaryGenre) ?: exampleForKeyword(primaryKeyword))
                val bridge = primaryGenreLabel ?: primaryKeyword?.let(::formatRecommendationKeyword) ?: "one familiar theme"
                when {
                    example.isNotBlank() && novelGenreLabel != null ->
                        "${kind.label} • $bridge connects it to $example, while $novelGenreLabel pushes it sideways instead of serving more of the same."
                    example.isNotBlank() ->
                        "${kind.label} • $bridge connects it to $example, but the rest of the match sits outside your most repeated patterns."
                    else ->
                        "${kind.label} • It keeps one strong $bridge connection while deliberately moving away from your usual combinations."
                }
            }
            RecommendationKind.HIDDEN_GEM -> {
                val signal = primaryGenreLabel ?: primaryKeyword?.let(::formatRecommendationKeyword) ?: "a niche theme"
                val example = titleOf(exampleForGenre(primaryGenre) ?: exampleForKeyword(primaryKeyword))
                if (example.isNotBlank()) {
                    "${kind.label} • $example points to your $signal interest; this is a less obvious title carrying that same thread."
                } else {
                    "${kind.label} • Your library has a quieter $signal pattern, and this lower-ranked pick lines up with it surprisingly well."
                }
            }
            RecommendationKind.WILDCARD -> {
                val bridge = primaryGenreLabel ?: primaryKeyword?.let(::formatRecommendationKeyword) ?: "one theme"
                val example = titleOf(exampleForGenre(primaryGenre) ?: exampleForKeyword(primaryKeyword))
                when {
                    example.isNotBlank() && novelGenreLabel != null ->
                        "${kind.label} • $bridge is the bridge back to $example; $novelGenreLabel takes you somewhere your library rarely goes."
                    example.isNotBlank() ->
                        "${kind.label} • $bridge is the main bridge back to $example; everything else is deliberately outside your usual pattern."
                    else ->
                        "${kind.label} • Only a small part of this overlaps with your normal taste, so it is here to genuinely broaden the shelf."
                }
            }
        }
    }

    private fun strongestRecommendationSignal(
        signals: Set<String>,
        weights: Map<String, Int>,
    ): String? = signals.maxByOrNull { weights[it] ?: 0 }

    private fun recommendationGenreLabels(
        originalGenres: List<String>,
        matches: Set<String>,
        limit: Int,
    ): List<String> =
        originalGenres
            .filter { normalizeRecommendationTag(it) in matches }
            .distinctBy(::normalizeRecommendationTag)
            .take(limit)

    private fun recommendationCreatorLabel(
        candidate: SManga,
        normalizedCreator: String,
    ): String {
        val rawCreators = listOfNotNull(candidate.author, candidate.artist)
        return rawCreators
            .firstOrNull { normalizeRecommendationTag(it) == normalizedCreator }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: formatRecommendationKeyword(normalizedCreator)
    }

    /**
     * Builds a curated "Trending Now" shelf from a broader slice of enabled-source Popular feeds.
     *
     * Ranking deliberately combines several signals instead of exposing a raw source list:
     * - cross-source agreement is the strongest signal;
     * - page 1 / high rank is treated as fresher/stronger than deeper Popular pages;
     * - duplicate editions/issues are collapsed with a dedicated series-title normalizer;
     * - anything already represented in the user's Library is removed before ranking;
     * - source caps keep one extension from taking over the shelf;
     * - when duplicates exist, the strongest representative with the best-looking cover is preferred.
     *
     * Pages 2-3 enlarge the candidate pool, but they are fallback depth rather than equal-ranked
     * material. That keeps the shelf broad without making deep source results look artificially hot.
     */
    private fun loadTrending(
        force: Boolean = false,
        background: Boolean = false,
        cacheContext: Context? = discoverCacheContext,
        onComplete: ((List<DiscoverRecommendation>) -> Unit)? = null,
    ) {
        if (trendingLoadInProgress) {
            onComplete?.invoke(emptyList())
            return
        }
        if (!background && !isBindingInitialized) return

        if (!background && !force && trendingItems.isNotEmpty()) {
            showTrending(trendingItems, animate = false)
            return
        }

        if (!background && trendingItems.isEmpty() && cachedTrendingItems.isNotEmpty()) {
            trendingItems = cachedTrendingItems
            showTrending(trendingItems, animate = false)
            return
        }

        if (!background) {
            // Avoid another floating loader on Discover. The curated shelf appears when ready.
            binding.trendingSection.isVisible = false
            binding.trendingProgress.isVisible = false
            binding.trendingRecycler.isVisible = false
        }

        trendingLoadInProgress = true
        val refreshScope = if (background) pickedForYouRefreshScope else viewScope
        refreshScope.launchIO {
            try {
                val libraryTitleKeys =
                    runCatching { db.getLibraryMangas().executeAsBlocking() }
                        .getOrDefault(emptyList())
                        .map { manga -> normalizeDiscoverRankTitle(manga.title) }
                        .filter(String::isNotBlank)
                        .toSet()

                // Use more enabled sources than v1 and a deeper Popular pool. Sources are still
                // queried in parallel, and each source contributes at most 60 unique candidates.
                val trendSources =
                    presenter.sources
                        .filterNot { it.id == LocalSource.ID }
                        .take(10)

                data class RankedTrend(
                    val source: CatalogueSource,
                    val manga: SManga,
                    val page: Int,
                    val rank: Int,
                )

                data class TrendGroup(
                    val key: String,
                    val entries: List<RankedTrend>,
                    val sourceCount: Int,
                    val firstPageSourceCount: Int,
                    val topTenFirstPageCount: Int,
                    val bestPage: Int,
                    val bestRank: Int,
                    val score: Int,
                )

                fun entrySignal(entry: RankedTrend): Int {
                    val pagePenalty = (entry.page - 1) * 95
                    val rankPenalty = entry.rank * 7
                    return (320 - pagePenalty - rankPenalty).coerceAtLeast(0)
                }

                val ranked =
                    trendSources
                        .map { source ->
                            async {
                                val pages =
                                    (1..3)
                                        .map { popularPage ->
                                            async {
                                                val mangas =
                                                    withTimeoutOrNull(7_500L) {
                                                        runCatching { source.getPopularManga(popularPage) }
                                                            .getOrNull()
                                                            ?.mangas
                                                            .orEmpty()
                                                    }.orEmpty()

                                                mangas.mapIndexedNotNull { rank, manga ->
                                                    val key = normalizeDiscoverRankTitle(manga.title)
                                                    if (
                                                        key.isBlank() ||
                                                        key in libraryTitleKeys ||
                                                        manga.thumbnail_url.isNullOrBlank()
                                                    ) {
                                                        null
                                                    } else {
                                                        RankedTrend(
                                                            source = source,
                                                            manga = manga,
                                                            page = popularPage,
                                                            rank = rank,
                                                        )
                                                    }
                                                }
                                            }
                                        }.awaitAll()
                                        .flatten()

                                // Some sources repeat page 1 items on later pages. Keep only the
                                // strongest occurrence of each title from each source.
                                pages
                                    .sortedWith(
                                        compareBy<RankedTrend> { it.page }
                                            .thenBy { it.rank },
                                    )
                                    .distinctBy { item -> normalizeDiscoverRankTitle(item.manga.title) }
                                    .take(60)
                            }
                        }.awaitAll()
                        .flatten()

                val groups =
                    ranked
                        .groupBy { item -> normalizeDiscoverRankTitle(item.manga.title) }
                        .mapNotNull { (key, entries) ->
                            if (key.isBlank() || entries.isEmpty() || key in libraryTitleKeys) {
                                return@mapNotNull null
                            }

                            val uniqueSources = entries.map { it.source.id }.distinct().size
                            val firstPageSources = entries.filter { it.page == 1 }.map { it.source.id }.distinct().size
                            val topTenFirstPage = entries.count { it.page == 1 && it.rank < 10 }
                            val best =
                                entries.minWithOrNull(
                                    compareBy<RankedTrend> { it.page }
                                        .thenBy { it.rank },
                                ) ?: return@mapNotNull null

                            // Cross-source agreement matters most, but a genuine page-1 chart leader
                            // can still outrank a weak duplicate found deep on two sources.
                            val groupScore =
                                (uniqueSources * 650) +
                                    (firstPageSources * 220) +
                                    (topTenFirstPage * 110) +
                                    entrySignal(best) +
                                    entries
                                        .sortedByDescending(::entrySignal)
                                        .take(3)
                                        .sumOf { entrySignal(it) / 4 }

                            TrendGroup(
                                key = key,
                                entries = entries,
                                sourceCount = uniqueSources,
                                firstPageSourceCount = firstPageSources,
                                topTenFirstPageCount = topTenFirstPage,
                                bestPage = best.page,
                                bestRank = best.rank,
                                score = groupScore,
                            )
                        }.sortedWith(
                            compareByDescending<TrendGroup> { it.score }
                                .thenByDescending { it.sourceCount }
                                .thenBy { it.bestPage }
                                .thenBy { it.bestRank }
                                .thenBy { it.key },
                        )

                val selected = mutableListOf<Pair<TrendGroup, RankedTrend>>()
                val sourceUse = mutableMapOf<Long, Int>()
                val selectedKeys = mutableSetOf<String>()

                fun representativeScore(entry: RankedTrend): Int =
                    entrySignal(entry) + (discoverCoverQualityScore(entry.manga.thumbnail_url) * 12)

                fun fillWithSourceCap(cap: Int) {
                    groups.forEach { group ->
                        if (selected.size >= 6 || group.key in selectedKeys) return@forEach

                        val representative =
                            group.entries
                                .filter { entry -> (sourceUse[entry.source.id] ?: 0) < cap }
                                .sortedWith(
                                    compareBy<RankedTrend> { sourceUse[it.source.id] ?: 0 }
                                        .thenByDescending(::representativeScore)
                                        .thenBy { it.page }
                                        .thenBy { it.rank },
                                ).firstOrNull()
                                ?: return@forEach

                        selected += group to representative
                        selectedKeys += group.key
                        sourceUse[representative.source.id] = (sourceUse[representative.source.id] ?: 0) + 1
                    }
                }

                // Keep the normal cap tight, relaxing only when the user's enabled-source set is
                // too small to make a full shelf. This is intentionally stricter than raw feeds.
                fillWithSourceCap(2)
                if (selected.size < 6) fillWithSourceCap(3)
                if (selected.size < 6) fillWithSourceCap(4)

                val trends =
                    selected
                        .take(6)
                        .mapNotNull { (group, representative) ->
                            runCatching {
                                val manga = networkToLocalManga(representative.manga, representative.source.id)
                                val reason =
                                    when {
                                        group.sourceCount >= 3 && group.firstPageSourceCount >= 2 ->
                                            "Strong across ${group.sourceCount} popular feeds"
                                        group.sourceCount >= 2 && group.topTenFirstPageCount >= 2 ->
                                            "Top 10 on ${group.topTenFirstPageCount} popular feeds"
                                        group.sourceCount >= 2 ->
                                            "Popular across ${group.sourceCount} sources"
                                        group.bestPage == 1 && group.bestRank <= 2 ->
                                            "Top 3 in a current popular feed"
                                        group.bestPage == 1 && group.bestRank <= 9 ->
                                            "Top 10 in a current popular feed"
                                        group.bestPage == 1 ->
                                            "Strong on a current popular feed"
                                        else ->
                                            "Rising through current popular results"
                                    }
                                DiscoverRecommendation(manga, reason)
                            }.getOrNull()
                        }

                cachedTrendingItems = trends
                cacheContext?.let { appContext ->
                    InkShelfDiscoverCache.save(appContext, InkShelfDiscoverCache.Section.TRENDING, trends)
                }

                if (!background) {
                    withUIContext {
                        if (!isBindingInitialized) return@withUIContext
                        trendingItems = trends
                        showTrending(trends, animate = true)
                    }
                }
                onComplete?.invoke(trends)
            } catch (_: Exception) {
                onComplete?.invoke(emptyList())
            } finally {
                trendingLoadInProgress = false
            }
        }
    }

    /**
     * Series-level identity used by public discovery shelves. It is intentionally separate from
     * Picked for You's normalizer so tuning Trending/New cannot change the stable PFY engine.
     */
    private fun normalizeDiscoverRankTitle(title: String): String =
        title
            .lowercase(Locale.ROOT)
            .replace(
                Regex(
                    "\\((?:19|20)\\d{2}(?:\\s*[-–]\\s*(?:19|20)?\\d{0,4})?\\)",
                    RegexOption.IGNORE_CASE,
                ),
                " ",
            )
            .replace(
                Regex(
                    "\\[(?:19|20)\\d{2}(?:\\s*[-–]\\s*(?:19|20)?\\d{0,4})?]",
                    RegexOption.IGNORE_CASE,
                ),
                " ",
            )
            .replace(Regex("#\\s*\\d+(?:\\.\\d+)?\\b"), " ")
            .replace(
                Regex(
                    "\\b(?:vol(?:ume)?|issue|chapter|ch|book|part)\\.?\\s*#?\\s*(?:\\d+(?:\\.\\d+)?|[ivxlcdm]+)\\b",
                    RegexOption.IGNORE_CASE,
                ),
                " ",
            )
            .replace(Regex("\\b(?:19|20)\\d{2}\\b"), " ")
            .replace(
                Regex(
                    "\\b(?:digital|ongoing|complete|completed|official|english|deluxe|edition)\\b",
                    RegexOption.IGNORE_CASE,
                ),
                " ",
            )
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    /**
     * Lightweight URL heuristic for choosing between duplicate-source covers. It does not decide
     * whether a title trends; it only helps choose the cleanest representative after ranking.
     */
    private fun discoverCoverQualityScore(url: String?): Int {
        if (url.isNullOrBlank()) return -100
        val value = url.lowercase(Locale.ROOT)
        var score = 0

        if (value.startsWith("https://")) score += 4
        if ("original" in value || "full" in value || "large" in value) score += 4
        if ("cover" in value) score += 2
        if ("thumb" in value || "thumbnail" in value || "small" in value || "tiny" in value) score -= 4
        if (Regex("(?:^|[/_-])(?:80|100|120|150|180|200)(?:x|[/_.-])").containsMatchIn(value)) score -= 3
        if (url.length >= 80) score += 1

        return score
    }

    private fun showTrending(
        items: List<DiscoverRecommendation>,
        animate: Boolean = true,
    ) {
        binding.trendingProgress.isVisible = false
        binding.trendingRecycler.isVisible = items.isNotEmpty()
        revealDiscoverSection(binding.trendingSection, items.isNotEmpty(), animate)
        trendingAdapter?.submitList(items.take(6))
    }

    /**
     * Builds the Discover "New This Week" shelf from enabled sources' Latest feeds.
     *
     * The generic J2K source contract does not expose a trustworthy publication timestamp for
     * every catalogue item, so this deliberately does not invent exact ages. Instead it treats
     * the current Latest feed as the freshness signal, dedupes editions/issues, excludes anything
     * already represented in the Library, balances sources, keeps an approximately ten-title
     * curated pool, then shows the strongest six cards.
     */
    private fun loadNewThisWeek(
        force: Boolean = false,
        background: Boolean = false,
        cacheContext: Context? = discoverCacheContext,
        onComplete: ((List<DiscoverRecommendation>) -> Unit)? = null,
    ) {
        if (newThisWeekLoadInProgress) {
            onComplete?.invoke(emptyList())
            return
        }
        if (!background && !isBindingInitialized) return

        if (!background && !force && newThisWeekItems.isNotEmpty()) {
            showNewThisWeek(newThisWeekItems, animate = false)
            return
        }

        if (!background && newThisWeekItems.isEmpty() && cachedNewThisWeekItems.isNotEmpty()) {
            newThisWeekItems = cachedNewThisWeekItems
            showNewThisWeek(newThisWeekItems, animate = false)
            return
        }

        // Match Trending: no spinner flash. Reveal only when a real curated shelf is ready.
        if (!background) {
            binding.newThisWeekSection.isVisible = false
            binding.newThisWeekProgress.isVisible = false
            binding.newThisWeekRecycler.isVisible = false
        }

        newThisWeekLoadInProgress = true
        val refreshScope = if (background) pickedForYouRefreshScope else viewScope
        refreshScope.launchIO {
            try {
                val libraryTitleKeys =
                    runCatching { db.getLibraryMangas().executeAsBlocking() }
                        .getOrDefault(emptyList())
                        .map { manga -> normalizeDiscoverRankTitle(manga.title) }
                        .filter(String::isNotBlank)
                        .toSet()

                // One current Latest page per enabled source is enough for this deliberately small
                // shelf. The final cross-source pool is capped at about ten distinct titles.
                val latestSources =
                    presenter.sources
                        .filterNot { it.id == LocalSource.ID }
                        .take(8)

                data class FreshEntry(
                    val source: CatalogueSource,
                    val manga: SManga,
                    val rank: Int,
                )

                data class FreshGroup(
                    val key: String,
                    val entries: List<FreshEntry>,
                    val sourceCount: Int,
                    val bestRank: Int,
                    val score: Int,
                )

                val latestEntries =
                    latestSources
                        .map { source ->
                            async {
                                val mangas =
                                    withTimeoutOrNull(7_500L) {
                                        runCatching { source.getLatestUpdates(1) }
                                            .getOrNull()
                                            ?.mangas
                                            .orEmpty()
                                    }.orEmpty()

                                mangas
                                    .mapIndexedNotNull { rank, manga ->
                                        val key = normalizeDiscoverRankTitle(manga.title)
                                        if (
                                            key.isBlank() ||
                                            key in libraryTitleKeys ||
                                            manga.thumbnail_url.isNullOrBlank()
                                        ) {
                                            null
                                        } else {
                                            FreshEntry(source = source, manga = manga, rank = rank)
                                        }
                                    }
                                    // A single source can repeat alternate editions in its Latest feed.
                                    .distinctBy { item -> normalizeDiscoverRankTitle(item.manga.title) }
                                    .take(18)
                            }
                        }.awaitAll()
                        .flatten()

                val groups =
                    latestEntries
                        .groupBy { item -> normalizeDiscoverRankTitle(item.manga.title) }
                        .mapNotNull { (key, entries) ->
                            if (key.isBlank() || key in libraryTitleKeys || entries.isEmpty()) {
                                return@mapNotNull null
                            }

                            val sourceCount = entries.map { it.source.id }.distinct().size
                            val bestRank = entries.minOf { it.rank }
                            val topTenSources = entries.count { it.rank < 10 }

                            // Rank position is the primary freshness signal. Agreement between
                            // independent Latest feeds adds confidence without allowing duplicates.
                            val score =
                                (sourceCount * 420) +
                                    (topTenSources * 100) +
                                    (260 - bestRank * 9).coerceAtLeast(0)

                            FreshGroup(
                                key = key,
                                entries = entries,
                                sourceCount = sourceCount,
                                bestRank = bestRank,
                                score = score,
                            )
                        }.sortedWith(
                            compareByDescending<FreshGroup> { it.score }
                                .thenByDescending { it.sourceCount }
                                .thenBy { it.bestRank }
                                .thenBy { it.key },
                        )

                val curatedPool = mutableListOf<Pair<FreshGroup, FreshEntry>>()
                val usedKeys = mutableSetOf<String>()
                val sourceUse = mutableMapOf<Long, Int>()

                fun representativeScore(entry: FreshEntry): Int =
                    ((240 - entry.rank * 8).coerceAtLeast(0)) +
                        (discoverCoverQualityScore(entry.manga.thumbnail_url) * 10)

                fun fillPoolWithSourceCap(cap: Int) {
                    groups.forEach { group ->
                        if (curatedPool.size >= 10 || group.key in usedKeys) return@forEach

                        val representative =
                            group.entries
                                .filter { entry -> (sourceUse[entry.source.id] ?: 0) < cap }
                                .sortedWith(
                                    compareBy<FreshEntry> { sourceUse[it.source.id] ?: 0 }
                                        .thenByDescending(::representativeScore)
                                        .thenBy { it.rank },
                                ).firstOrNull()
                                ?: return@forEach

                        curatedPool += group to representative
                        usedKeys += group.key
                        sourceUse[representative.source.id] = (sourceUse[representative.source.id] ?: 0) + 1
                    }
                }

                // Build roughly ten candidates first, then the UI shows the best six.
                fillPoolWithSourceCap(2)
                if (curatedPool.size < 10) fillPoolWithSourceCap(3)
                if (curatedPool.size < 10) fillPoolWithSourceCap(4)

                val freshItems =
                    curatedPool
                        .take(10)
                        .mapNotNull { (group, representative) ->
                            runCatching {
                                val manga = networkToLocalManga(representative.manga, representative.source.id)
                                val reason =
                                    when {
                                        group.sourceCount >= 3 ->
                                            "Fresh across ${group.sourceCount} latest feeds"
                                        group.sourceCount >= 2 ->
                                            "New across ${group.sourceCount} sources"
                                        representative.rank <= 2 ->
                                            "Top 3 in a current latest feed"
                                        representative.rank <= 9 ->
                                            "Top 10 in a current latest feed"
                                        else ->
                                            "Fresh from a current latest feed"
                                    }
                                DiscoverRecommendation(manga, reason)
                            }.getOrNull()
                        }

                cachedNewThisWeekItems = freshItems
                cacheContext?.let { appContext ->
                    InkShelfDiscoverCache.save(appContext, InkShelfDiscoverCache.Section.NEW_THIS_WEEK, freshItems)
                }

                if (!background) {
                    withUIContext {
                        if (!isBindingInitialized) return@withUIContext
                        newThisWeekItems = freshItems
                        showNewThisWeek(freshItems, animate = true)
                    }
                }
                onComplete?.invoke(freshItems)
            } catch (_: Exception) {
                onComplete?.invoke(emptyList())
            } finally {
                newThisWeekLoadInProgress = false
            }
        }
    }

    private fun showNewThisWeek(
        items: List<DiscoverRecommendation>,
        animate: Boolean = true,
    ) {
        binding.newThisWeekProgress.isVisible = false
        binding.newThisWeekRecycler.isVisible = items.isNotEmpty()
        revealDiscoverSection(binding.newThisWeekSection, items.isNotEmpty(), animate)
        newThisWeekAdapter?.submitList(items.take(6))
    }

    /**
     * Mirrors the same database feed used by the bottom-nav Updates screen.
     * No global Latest/source catalogue data is used here: only favourite Library titles
     * with chapters present in J2K's recent-updates query are shown.
     */
    private fun loadRecentlyUpdated(force: Boolean = false) {
        if (!isBindingInitialized || recentlyUpdatedLoadInProgress) return

        val now = System.currentTimeMillis()
        if (!force && recentlyUpdatedItems.isNotEmpty() && now - recentlyUpdatedLastCheckedAt < 15_000L) {
            showRecentlyUpdated(recentlyUpdatedItems, animate = false)
            return
        }

        recentlyUpdatedLastCheckedAt = now
        if (recentlyUpdatedItems.isEmpty()) {
            // No spinner flash on first load; reveal only once the real shelf is ready.
            binding.recentlyUpdatedSection.isVisible = false
            binding.recentlyUpdatedProgress.isVisible = false
            binding.recentlyUpdatedRecycler.isVisible = false
        } else {
            // Keep the existing shelf visible while we quietly check for changes.
            showRecentlyUpdated(recentlyUpdatedItems, animate = false)
        }

        recentlyUpdatedLoadInProgress = true
        viewScope.launchIO {
            try {
                val recentRows =
                    runCatching {
                        db.getRecentChapters(search = "", offset = 0, isResuming = false).executeAsBlocking()
                    }.getOrDefault(emptyList())

                val fingerprint =
                    recentRows
                        .take(80)
                        .joinToString("|") { row ->
                            "${row.manga.id}:${row.chapter.id}:${row.chapter.date_fetch}:${row.chapter.read}"
                        }

                val cachedItems =
                    when {
                        fingerprint == recentlyUpdatedFingerprint && recentlyUpdatedItems.isNotEmpty() -> recentlyUpdatedItems
                        fingerprint == cachedRecentlyUpdatedFingerprint && cachedRecentlyUpdatedItems.isNotEmpty() -> cachedRecentlyUpdatedItems
                        else -> emptyList()
                    }

                if (cachedItems.isNotEmpty()) {
                    withUIContext {
                        if (!isBindingInitialized) return@withUIContext
                        recentlyUpdatedFingerprint = fingerprint
                        recentlyUpdatedItems = cachedItems
                        showRecentlyUpdated(cachedItems, animate = false)
                    }
                    return@launchIO
                }

                val updates =
                    recentRows
                        .groupBy { row ->
                            row.manga.id ?: row.manga.url
                                .hashCode()
                                .toLong()
                        }.mapNotNull { (_, rows) ->
                            val newest = rows.maxByOrNull { it.chapter.date_fetch } ?: return@mapNotNull null
                            val count = rows.size
                            val whenUpdated =
                                DateUtils
                                    .getRelativeTimeSpanString(
                                        newest.chapter.date_fetch,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS,
                                    ).toString()
                            val reason =
                                if (count > 1) {
                                    "$count chapters • $whenUpdated"
                                } else {
                                    "Updated $whenUpdated"
                                }
                            newest.manga to reason
                        }.sortedByDescending { (manga, _) ->
                            recentRows
                                .asSequence()
                                .filter { it.manga.id == manga.id }
                                .maxOfOrNull { it.chapter.date_fetch }
                                ?: 0L
                        }.take(12)
                        .map { (manga, reason) -> DiscoverRecommendation(manga, reason) }

                cachedRecentlyUpdatedFingerprint = fingerprint
                cachedRecentlyUpdatedItems = updates

                withUIContext {
                    if (!isBindingInitialized) return@withUIContext
                    recentlyUpdatedFingerprint = fingerprint
                    recentlyUpdatedItems = updates
                    showRecentlyUpdated(updates, animate = true)
                }
            } finally {
                recentlyUpdatedLoadInProgress = false
            }
        }
    }

    private fun showRecentlyUpdated(
        items: List<DiscoverRecommendation>,
        animate: Boolean = true,
    ) {
        binding.recentlyUpdatedProgress.isVisible = false
        binding.recentlyUpdatedRecycler.isVisible = items.isNotEmpty()
        binding.recentlyUpdatedSeeAll.isVisible = items.isNotEmpty()
        revealDiscoverSection(binding.recentlyUpdatedSection, items.isNotEmpty(), animate)
        recentlyUpdatedAdapter?.submitList(items)
    }

    private fun awaitStartupPickedForYouPrewarm(attempt: Int = 0) {
        if (!isBindingInitialized) return

        if (cachedPickedForYouItems.isNotEmpty()) {
            pickedForYouItems = cachedPickedForYouItems
            pickedForYouFingerprint = cachedPickedForYouFingerprint
            showPickedForYou(pickedForYouItems, animate = true)
            return
        }

        // If startup warm-up failed or timed out, fall back to the normal Discover loader.
        if (!startupPickedForYouPrewarmInProgress || attempt >= 80) {
            loadPickedForYou()
            return
        }

        binding.root.postDelayed(
            { awaitStartupPickedForYouPrewarm(attempt + 1) },
            125L,
        )
    }

    private fun showPickedForYou(
        items: List<DiscoverRecommendation>,
        animate: Boolean = true,
    ) {
        binding.pickedForYouProgress.isVisible = false
        binding.pickedForYouBannerCard.isVisible = items.isNotEmpty()
        binding.pickedForYouBannerCard.isEnabled = items.isNotEmpty()
        revealDiscoverSection(binding.pickedForYouSection, items.isNotEmpty(), animate)
    }

    private fun revealDiscoverSection(
        section: View,
        visible: Boolean,
        animate: Boolean,
    ) {
        section.animate().cancel()
        if (!visible) {
            section.alpha = 1f
            section.isVisible = false
            return
        }

        if (section.isVisible || !animate) {
            section.alpha = 1f
            section.isVisible = true
            return
        }

        section.alpha = 0f
        section.isVisible = true
        section
            .animate()
            .alpha(1f)
            .setDuration(160L)
            .start()
    }

    private fun normalizeRecommendationTitle(title: String): String =
        title
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun tokenizeRecommendationTitle(title: String): Set<String> =
        title
            .lowercase(Locale.ROOT)
            .replace("-", " ")
            .split(Regex("[^a-z0-9]+"))
            .mapNotNull { token ->
                token
                    .trim()
                    .takeIf {
                        it.length >= 3 &&
                            it !in recommendationStopWords &&
                            it !in recommendationTitleNoiseWords
                    }
            }.toSet()

    private fun extractRecommendationGenres(genreText: String?): List<String> =
        genreText
            ?.split(Regex("[,;|]"))
            ?.mapNotNull { it.trim().takeUnless { value -> value.isBlank() } }
            .orEmpty()

    private fun normalizeRecommendationTag(tag: String): String =
        tag
            .lowercase(Locale.ROOT)
            .replace("-", " ")
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractRecommendationCreators(vararg names: String?): Set<String> =
        names
            .flatMap { name ->
                name
                    ?.split(Regex("[,;/&]"))
                    .orEmpty()
            }.mapNotNull { it.trim().takeUnless(String::isBlank) }
            .map { normalizeRecommendationTag(it) }
            .filter(String::isNotBlank)
            .toSet()

    private fun extractRecommendationKeywords(vararg texts: String?): Set<String> =
        texts
            .flatMap { text ->
                text
                    ?.lowercase(Locale.ROOT)
                    ?.replace("-", " ")
                    ?.split(Regex("[^a-z0-9]+"))
                    .orEmpty()
            }.mapNotNull { token ->
                token
                    .trim()
                    .takeIf {
                        it.length >= 4 &&
                            it !in recommendationStopWords &&
                            it !in recommendationTitleNoiseWords
                    }
            }.toSet()

    private fun formatRecommendationKeyword(keyword: String): String =
        keyword
            .split(" ")
            .joinToString(" ") { part -> part.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }

    private val recommendationStopWords: Set<String>
        get() =
            setOf(
                "about",
                "after",
                "again",
                "also",
                "always",
                "among",
                "because",
                "before",
                "being",
                "comic",
                "comics",
                "from",
                "have",
                "into",
                "just",
                "more",
                "most",
                "much",
                "over",
                "read",
                "reads",
                "that",
                "their",
                "them",
                "they",
                "this",
                "those",
                "through",
                "very",
                "when",
                "with",
                "your",
                "while",
                "story",
                "stories",
                "series",
                "volume",
                "issue",
                "book",
                "books",
                "chapter",
                "chapters",
                "edition",
                "complete",
                "current",
                "recent",
                "dark",
                "character",
                "driven",
                "collection",
                "part",
                "vol",
                "and",
                "the",
                "for",
                "you",
            )

    private val recommendationTitleNoiseWords: Set<String>
        get() =
            setOf(
                "annual",
                "comic",
                "comics",
                "edition",
                "issue",
                "issues",
                "man",
                "novel",
                "one",
                "special",
                "story",
                "stories",
                "the",
                "vol",
                "volume",
            )

    private fun networkToLocalManga(
        sManga: SManga,
        sourceId: Long,
    ): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        } else if (!localManga.favorite) {
            localManga.copyFrom(sManga)
        }
        return localManga
    }

    private fun openPickedForYouManga(manga: Manga) {
        preferences.lastUsedCatalogueSource().set(manga.source)
        router.pushController(MangaDetailsController(manga, true).withFadeTransaction())
    }

    private fun openPickedForYouPage() {
        if (pickedForYouItems.isEmpty() && cachedPickedForYouItems.isEmpty()) return
        router.pushController(PickedForYouController().withFadeTransaction())
    }

    private fun runQuickSearch(query: String) {
        if (!isBindingInitialized) return
        binding.browseSearchInput.setText(query)
        binding.browseSearchInput.setSelection(query.length)
        submitModernBrowseSearch()
    }

    private fun submitModernBrowseSearch() {
        val query =
            binding.browseSearchInput.text
                ?.toString()
                ?.trim()
                .orEmpty()
        if (query.isNotEmpty()) {
            clearModernBrowseSearchState(clearText = true)
            performGlobalSearch(query)
        }
    }

    private fun clearModernBrowseSearchState(clearText: Boolean) {
        if (!isBindingInitialized) return
        binding.browseSearchInput.clearFocus()
        if (clearText) binding.browseSearchInput.text?.clear()
        (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.browseSearchInput.windowToken, 0)
    }

    private fun showModernManageSourcesMenu() {
        val anchor = binding.discoverMenuButton
        PopupMenu(anchor.context, anchor).apply {
            menu.add(0, MODERN_MENU_ENABLED_SOURCES, 0, R.string.modern_browse_enabled_sources)
            menu.add(0, MODERN_MENU_EXTENSIONS, 1, R.string.modern_browse_extensions)
            menu.add(0, MODERN_MENU_MIGRATION, 2, R.string.modern_browse_migration)
            menu.add(0, MODERN_MENU_SETTINGS, 3, R.string.modern_browse_settings)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MODERN_MENU_ENABLED_SOURCES -> {
                        router.pushController(SettingsSourcesController().withFadeTransaction())
                        true
                    }
                    MODERN_MENU_EXTENSIONS -> {
                        binding.bottomSheet.tabs
                            .getTabAt(0)
                            ?.select()
                        binding.bottomSheet.root.fetchOnlineExtensionsIfNeeded()
                        showSheet()
                        true
                    }
                    MODERN_MENU_MIGRATION -> {
                        binding.bottomSheet.tabs
                            .getTabAt(1)
                            ?.select()
                        showSheet()
                        true
                    }
                    MODERN_MENU_SETTINGS -> {
                        router.pushController(SettingsBrowseController().withFadeTransaction())
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun performGlobalSearch(query: String) {
        router.pushController(GlobalSearchController(query).withFadeTransaction())
    }

    /**
     * Called when an option menu item has been selected by the user.
     *
     * @param item The selected item.
     * @return True if this event has been consumed, false if it has not.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_filter -> {
                router.pushController(SettingsSourcesController().withFadeTransaction())
            }
            R.id.action_migration_guide -> {
                activity?.openInBrowser(HELP_URL)
            }
            R.id.action_sources_settings -> {
                router.pushController(SettingsBrowseController().withFadeTransaction())
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Called to update adapter containing sources.
     */
    fun setSources(
        sources: List<IFlexible<*>>,
        lastUsed: SourceItem?,
    ) {
        adapter?.updateDataSet(sources, false)
        setLastUsedSource(lastUsed)
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
    }

    /**
     * Called to set the last used catalogue at the top of the view.
     */
    fun setLastUsedSource(item: SourceItem?) {
        adapter?.removeAllScrollableHeaders()
        if (item != null) {
            adapter?.addScrollableHeader(item)
            adapter?.addScrollableHeader(LangItem(SourcePresenter.LAST_USED_KEY))
        }
    }

    @Parcelize
    data class SmartSearchConfig(
        val origTitle: String,
        val origMangaId: Long,
    ) : Parcelable

    companion object {
        const val HELP_URL = "https://mihon.app/docs/guides/source-migration"
        private const val MODERN_MENU_ENABLED_SOURCES = 9101
        private const val MODERN_MENU_EXTENSIONS = 9102
        private const val MODERN_MENU_MIGRATION = 9103
        private const val MODERN_MENU_SETTINGS = 9104

        // Process-memory cache: switching tabs/recreating the Discover controller does not
        // rebuild Picked for You. The shelf stays fixed for the app session and naturally
        // refreshes on the next process/app restart when these static values are cleared.
        private var cachedPickedForYouItems: List<DiscoverRecommendation> = emptyList()
        private var cachedPickedForYouFingerprint: String? = null
        private var forcePickedForYouRefresh = false
        private var startupPickedForYouPrewarmInProgress = false
        private var pendingPickedForYouExclusions: Set<String> = emptySet()
        private var pendingPickedForYouRefreshSeed: Long = 0L
        private var cachedTrendingItems: List<DiscoverRecommendation> = emptyList()
        private var cachedNewThisWeekItems: List<DiscoverRecommendation> = emptyList()
        private var cachedRecentlyUpdatedItems: List<DiscoverRecommendation> = emptyList()

        internal fun pickedForYouSnapshot(): List<DiscoverRecommendation> = cachedPickedForYouItems.toList()

        /**
         * Starts the expensive recommendation pass shortly after app launch, while Library/Recents
         * is on screen. The generated batch lands in the same process cache Discover already uses.
         */
        internal fun prewarmPickedForYouAtStartup() {
            // Deliberately no network work at app launch. Discover restores its persisted PFY
            // batch instead; Trending/New are refreshed by WorkManager while the app is closed.
            // A first-ever install can still generate PFY when Discover is actually opened.
        }

        internal suspend fun refreshDiscoverFeedsInBackground(context: Context): Boolean {
            val controller = BrowseController()
            return try {
                // Refresh sequentially rather than firing every source request at once. This keeps
                // background work battery/network friendly and avoids recreating the startup burst.
                val trendingDeferred = CompletableDeferred<List<DiscoverRecommendation>>()
                controller.loadTrending(
                    force = true,
                    background = true,
                    cacheContext = context.applicationContext,
                ) { items ->
                    if (!trendingDeferred.isCompleted) trendingDeferred.complete(items)
                }
                val trends = withTimeoutOrNull(90_000L) { trendingDeferred.await() }.orEmpty()

                val newDeferred = CompletableDeferred<List<DiscoverRecommendation>>()
                controller.loadNewThisWeek(
                    force = true,
                    background = true,
                    cacheContext = context.applicationContext,
                ) { items ->
                    if (!newDeferred.isCompleted) newDeferred.complete(items)
                }
                val fresh = withTimeoutOrNull(60_000L) { newDeferred.await() }.orEmpty()

                trends.isNotEmpty() || fresh.isNotEmpty()
            } finally {
                controller.pickedForYouRefreshScope.cancel()
            }
        }

        internal fun requestPickedForYouRefresh() {
            // Remember the current shelf before clearing it. The next generation pass will
            // avoid these titles wherever the candidate pool makes that possible.
            pendingPickedForYouExclusions =
                cachedPickedForYouItems
                    .map { item ->
                        normalizePickedForYouTitle(item.manga.title)
                    }.toSet()

            pendingPickedForYouRefreshSeed = System.nanoTime()

            cachedPickedForYouItems = emptyList()
            cachedPickedForYouFingerprint = null
            forcePickedForYouRefresh = true
        }

        private fun normalizePickedForYouTitle(title: String): String =
            title
                .lowercase(Locale.ROOT)

                // Treat edition/year-labelled versions of the same comic as one title.
                // Examples:
                // Batman (2016) -> batman
                // Batman [2016] -> batman
                // Batman 2016 -> batman
                .replace(Regex("\\([^)]*\\)"), " ")
                .replace(Regex("\\[[^]]*]"), " ")
                .replace(Regex("\\b(?:19|20)\\d{2}\\b"), " ")

                // Common source naming noise.
                .replace(
                    Regex(
                        "\\b(?:digital|ongoing|complete|completed|english|official)\\b",
                        RegexOption.IGNORE_CASE,
                    ),
                    " ",
                )

                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

        private fun recommendationIdentity(
            sourceId: Long,
            url: String,
            title: String,
        ): String =
            "$sourceId|${url.trim().lowercase(Locale.ROOT)}|${title.trim().lowercase(Locale.ROOT)}"
        private var cachedRecentlyUpdatedFingerprint: String? = null
    }
}
