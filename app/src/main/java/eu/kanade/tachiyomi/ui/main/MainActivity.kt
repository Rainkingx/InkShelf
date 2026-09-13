package eu.kanade.tachiyomi.ui.main

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.assist.AssistContent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsCompat.Type.tappableElement
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.doOnNextLayout
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.google.common.primitives.Floats.max
import com.google.common.primitives.Ints.max
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.Migrations
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.preference.toggle
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateNotifier
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.source.isIncognitoModeForSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.more.AboutController
import eu.kanade.tachiyomi.ui.more.OverflowDialog
import eu.kanade.tachiyomi.ui.more.InkShelfSetupDialog
import eu.kanade.tachiyomi.ui.more.stats.StatsController
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.ui.recents.RecentsViewType
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.browse.repos.RepoController
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.system.Themes
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.end
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isBottomTappable
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.prepareSideNavContext
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.BackHandlerControllerInterface
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.blurBehindWindow
import eu.kanade.tachiyomi.util.view.canStillGoBack
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.findChild
import eu.kanade.tachiyomi.util.view.getItemView
import eu.kanade.tachiyomi.util.view.gradientBackgroundColor
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.mainRecyclerView
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeInTransaction
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

@SuppressLint("ResourceType")
open class MainActivity : BaseActivity<MainActivityBinding>() {
    protected lateinit var router: Router
    private var recreatingForSettingsChange = false
    private var pendingInkShelfResponsiveReset = false
    private var initialInkShelfChromeSyncPending = true

    /** Set while code moves the side-rail checkmark programmatically, so it is not taken as a tap. */
    private var isSyncingNavSelection = false

    private val sideNavToggleButton: MaterialButton?
        get() = binding.sideNav?.headerView?.findViewById(R.id.side_nav_toggle_btn)

    protected val searchDrawable by lazy { contextCompatDrawable(R.drawable.ic_search_24dp) }
    protected val backDrawable by lazy { contextCompatDrawable(R.drawable.ic_arrow_back_24dp) }
    private var gestureDetector: GestureDetector? = null

    private var snackBar: Snackbar? = null
    private var extraViewForUndo: View? = null
    private var canDismissSnackBar = false

    private var animationSet: AnimatorSet? = null
    private val downloadManager: DownloadManager by injectLazy()
    private val mangaShortcutManager: MangaShortcutManager by injectLazy()
    private val extensionManager: ExtensionManager by injectLazy()
    private val hideBottomNav
        get() = router.backstackSize > 1 && router.backstack[1].controller !is DialogController

    private val updateChecker by lazy { AppUpdateChecker() }
    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER
    private var tabAnimation: ValueAnimator? = null
    private var searchBarAnimation: ValueAnimator? = null
    private var overflowDialog: Dialog? = null
    private var inkShelfSetupDialog: InkShelfSetupDialog? = null
    var cachedSystemInsets: Insets = Insets.NONE
        private set
    var currentToolbar: Toolbar? = null
    var ogWidth: Int = Int.MAX_VALUE
    var hingeGapSize = 0
        private set

    val velocityTracker: VelocityTracker by lazy { VelocityTracker.obtain() }
    private val actionButtonSize: Pair<Int, Int> by lazy {
        val attrs = intArrayOf(android.R.attr.minWidth, android.R.attr.minHeight)
        val ta = obtainStyledAttributes(androidx.appcompat.R.style.Widget_AppCompat_ActionButton, attrs)
        val dimenW = ta.getDimensionPixelSize(0, 0.dpToPx)
        val dimenH = ta.getDimensionPixelSize(1, 0.dpToPx)
        ta.recycle()
        dimenW to dimenH
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                materialAlertDialog()
                    .setTitle(R.string.warning)
                    .setMessage(R.string.allow_notifications_recommended)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

    fun setUndoSnackBar(
        snackBar: Snackbar?,
        extraViewToCheck: View? = null,
    ) {
        this.snackBar = snackBar
        canDismissSnackBar = false
        launchUI {
            delay(1.seconds)
            if (this@MainActivity.snackBar == snackBar) {
                canDismissSnackBar = true
            }
        }
        extraViewForUndo = extraViewToCheck
    }

    override fun attachBaseContext(newBase: Context?) {
        ogWidth = min(newBase?.resources?.configuration?.screenWidthDp ?: Int.MAX_VALUE, ogWidth)
        super.attachBaseContext(newBase?.prepareSideNavContext())
    }

    val toolbarHeight: Int
        get() = max(binding.toolbar.height, binding.cardFrame.height, binding.appBar.attrToolbarHeight)

    private var actionMode: ActionMode? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private val backCallback = {
        // Predictive back can deliver a queued back-completed event after the
        // activity has already started finishing/destroying, at which point
        // Conductor's router no longer has a live host and pressingBack() ->
        // router.handleBack() crashes with an NPE deep in Conductor internals.
        if (!isFinishing && !isDestroyed) {
            pressingBack()
            reEnableBackPressedCallBack()
        }
    }

    fun bigToolbarHeight(
        includeSearchToolbar: Boolean,
        includeTabs: Boolean,
        includeLargeToolbar: Boolean,
    ): Int =
        if (!includeLargeToolbar || !binding.appBar.useLargeToolbar) {
            toolbarHeight + if (includeTabs) 48.dpToPx else 0
        } else {
            binding.appBar.getEstimatedLayout(includeSearchToolbar, includeTabs, includeLargeToolbar)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set up shared element transition and disable overlay so views don't show above system bars
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setExitSharedElementCallback(
            object : MaterialContainerTransformSharedElementCallback() {
                override fun onMapSharedElements(
                    names: MutableList<String>,
                    sharedElements: MutableMap<String, View>,
                ) {
                    val mangaController =
                        router.backstack.lastOrNull()?.controller as? MangaDetailsController
                    if (mangaController == null || chapterIdToExitTo == 0L) {
                        super.onMapSharedElements(names, sharedElements)
                        return
                    }
                    if (names.isEmpty()) return
                    val recyclerView = mangaController.binding.recycler
                    val selectedViewHolder =
                        recyclerView.findViewHolderForItemId(chapterIdToExitTo) ?: return
                    sharedElements[names[0]] = selectedViewHolder.itemView
                    chapterIdToExitTo = 0L
                }
            },
        )
        window.sharedElementsUseOverlay = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            // needed as the XML does not work for this
            val wic = WindowInsetsControllerCompat(window, window.decorView)
            val isLightMode = resources.getBoolean(R.bool.isLightMode)
            wic.isAppearanceLightStatusBars = isLightMode
            wic.isAppearanceLightNavigationBars = isLightMode
        }

        super.onCreate(savedInstanceState)
        backPressedCallback =
            object : OnBackPressedCallback(enabled = true) {
                var startTime: Long = 0
                var lastX: Float = 0f
                var lastY: Float = 0f
                var controllerHandlesBackPress = false

                override fun handleOnBackPressed() {
                    if (controllerHandlesBackPress &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        lastX != 0f &&
                        lastY != 0f
                    ) {
                        val motionEvent =
                            MotionEvent.obtain(
                                startTime,
                                SystemClock.uptimeMillis(),
                                MotionEvent.ACTION_UP,
                                lastX,
                                lastY,
                                0,
                            )
                        velocityTracker.addMovement(motionEvent)
                        motionEvent.recycle()
                        velocityTracker.computeCurrentVelocity(1, 5f)
                        backVelocity =
                            max(0.5f, abs(velocityTracker.getAxisVelocity(MotionEvent.AXIS_X)) * 0.5f)
                    }
                    lastX = 0f
                    lastY = 0f
                    backCallback()
                }

                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    controllerHandlesBackPress = false
                    val controller by lazy { router.backstack.lastOrNull()?.controller }
                    if (!(
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ViewCompat
                                    .getRootWindowInsets(window.decorView)
                                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                        ) &&
                        actionMode == null &&
                        !(
                            binding.searchToolbar.hasExpandedActionView() &&
                                binding.cardFrame.isVisible &&
                                controller !is SearchControllerInterface
                        )
                    ) {
                        controllerHandlesBackPress = true
                    }
                    if (controllerHandlesBackPress) {
                        startTime = SystemClock.uptimeMillis()
                        velocityTracker.clear()
                        val motionEvent =
                            MotionEvent.obtain(
                                startTime,
                                startTime,
                                MotionEvent.ACTION_DOWN,
                                backEvent.touchX,
                                backEvent.touchY,
                                0,
                            )
                        velocityTracker.addMovement(motionEvent)
                        motionEvent.recycle()
                        (controller as? BackHandlerControllerInterface)?.handleOnBackStarted(backEvent)
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (controllerHandlesBackPress) {
                        val motionEvent =
                            MotionEvent.obtain(
                                startTime,
                                SystemClock.uptimeMillis(),
                                MotionEvent.ACTION_MOVE,
                                backEvent.touchX,
                                backEvent.touchY,
                                0,
                            )
                        lastX = backEvent.touchX
                        lastY = backEvent.touchY
                        velocityTracker.addMovement(motionEvent)
                        motionEvent.recycle()
                        val controller = router.backstack.lastOrNull()?.controller as? BackHandlerControllerInterface
                        controller?.handleOnBackProgressed(backEvent)
                    }
                }

                override fun handleOnBackCancelled() {
                    if (controllerHandlesBackPress) {
                        val controller = router.backstack.lastOrNull()?.controller as? BackHandlerControllerInterface
                        controller?.handleOnBackCancelled()
                    }
                }
            }
        onBackPressedDispatcher.addCallback(backPressedCallback!!)
        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot && this !is SearchActivity) {
            finish()
            return
        }
        gestureDetector = GestureDetector(this, GestureListener())
        binding = MainActivityBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Remove Material 3's selected-item pill; the modern navigation uses only an underline.
        binding.bottomNav?.let { bottomNav ->
            runCatching {
                bottomNav.javaClass
                    .getMethod("setItemActiveIndicatorEnabled", Boolean::class.javaPrimitiveType!!)
                    .invoke(bottomNav, false)
            }
        }

        binding.toolbar.overflowIcon?.setTint(getResourceColor(R.attr.actionBarTintColor))
        if (isTablet()) {
            binding.sideNav?.let { sideNav ->
                // InkShelf: the large-screen rail is user-controlled in both portrait and
                // landscape. J2K previously forced portrait tablets open and removed the
                // collapse button, which made the rail feel like a permanent drawer.
                if (preferences.sideNavExpanded().get()) {
                    sideNav.expand()
                } else {
                    sideNav.collapse()
                }

                // Keep the rail toggle available on every tablet/fold orientation.
                if (sideNav.headerView == null) {
                    sideNav.addHeaderView(R.layout.side_nav_header)
                }
                sideNav.headerView?.isVisible = true

                // Keep the toggle aligned with the rail icons in both expanded and collapsed modes.
                sideNav.headerView?.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = Gravity.START or Gravity.TOP
                    marginStart = 24.dpToPx
                }
                sideNavToggleButton?.setOnClickListener {
                    preferences.sideNavExpanded().toggle()
                }
            }
        } else {
            binding.sideNav?.removeHeaderView()
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        var continueSwitchingTabs = false
        nav.getItemView(R.id.nav_library)?.setOnLongClickListener {
            if (!LibraryUpdateJob.isRunning(this)) {
                LibraryUpdateJob.startNow(this)
                binding.mainContent.snack(R.string.updating_library) {
                    anchorView = binding.bottomNav
                    setAction(R.string.cancel) {
                        LibraryUpdateJob.stop(context)
                        lifecycleScope.launchUI {
                            NotificationReceiver.dismissNotification(
                                context,
                                Notifications.ID_LIBRARY_PROGRESS,
                            )
                        }
                    }
                }
            }
            true
        }
        val longPressNavItems =
            if (binding.sideNav != null) {
                // The four former Recents submenu ids are now Library sort actions on large screens.
                listOf(R.id.nav_browse)
            } else {
                listOf(
                    R.id.nav_recents,
                    R.id.nav_browse,
                    R.id.nav_summary,
                    R.id.nav_ungrouped,
                    R.id.nav_history,
                    R.id.nav_updates,
                )
            }
        for (id in longPressNavItems) {
            nav.getItemView(id)?.setOnLongClickListener {
                nav.selectedItemId = if (R.id.nav_browse == id) id else R.id.nav_recents
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? BottomSheetController
                    controller?.showSheet()
                }
                true
            }
        }

        val container: ViewGroup = binding.controllerContainer

        val content: ViewGroup = binding.mainContent
        DownloadJob.downloadFlow.onEach(::downloadStatusChanged).launchIn(lifecycleScope)
        lifecycleScope
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowCustomEnabled(true)

        setNavBarColor(content.rootWindowInsetsCompat)
        binding.statusBar.gradientBackgroundColor = getColor(R.color.status_bar)
        binding.appBar.mainActivity = this
        nav.isVisible = false
        content.doOnApplyWindowInsetsCompat { v, insets, _ ->
            setNavBarColor(insets)
            val systemInsets = insets.ignoredSystemInsets
            val horizontalInsets = insets.getInsetsIgnoringVisibility(systemBars() or displayCutout())
            val contextView = window?.decorView?.findViewById<View>(R.id.action_mode_bar)
            contextView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = horizontalInsets.left
                rightMargin = horizontalInsets.right
            }
            // Consume any horizontal insets and pad all content in. There's not much we can do
            // with horizontal insets
            v.updatePadding(
                left = horizontalInsets.left,
                right = horizontalInsets.right,
            )
            binding.appBar.updatePadding(
                top = systemInsets.top,
            )
            binding.statusBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = systemInsets.top
            }
            binding.actionModeStatusBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = systemInsets.top
            }
            binding.navBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = insets.getInsetsIgnoringVisibility(tappableElement()).bottom
            }
            binding.bottomNav?.updatePadding(
                bottom = systemInsets.bottom,
            )
            binding.sideNav?.updatePadding(
                left = 0,
                right = 0,
                bottom = systemInsets.bottom,
                top = systemInsets.top,
            )
            binding.sideNav?.translationX = 0f
            binding.bottomView?.isVisible = systemInsets.bottom > 0
            binding.bottomView?.updateLayoutParams<ViewGroup.LayoutParams> {
                height = systemInsets.bottom
            }
            cachedSystemInsets = systemInsets
            (overflowDialog as? OverflowDialog)?.let {
                it.binding.overflowCardView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = toolbarHeight - 2.dpToPx + cachedSystemInsets.top
                    marginEnd = 14.dpToPx + cachedSystemInsets.end(resources.isLTR)
                }
            }
        }
        // Set this as nav view will try to set its own insets and they're hilariously bad
        ViewCompat.setOnApplyWindowInsetsListener(nav) { _, insets -> insets }

        router = Conductor.attachRouter(this, container, savedInstanceState)

        arrayOf(binding.toolbar, binding.searchToolbar).forEach { toolbar ->
            toolbar.setNavigationIconTint(getResourceColor(R.attr.actionBarTintColor))
            toolbar.router = router
        }
        if (router.hasRootController()) {
            nav.selectedItemId =
                when (router.backstack.firstOrNull()?.controller) {
                    is RecentsController -> R.id.nav_recents
                    is BrowseController -> R.id.nav_browse
                    else -> R.id.nav_library
                }

            // Cold-start restore can finish before the retained root controller has caused
            // a normal bottom-nav sync. Re-assert InkShelf chrome once the first frame is queued.
            scheduleInkShelfRootChromeSync(resetAppBarPosition = true)
        }

        nav.setOnItemSelectedListener { item ->
            val id = item.itemId
            // A programmatic side-rail selection move is not a user navigation action.
            if (isSyncingNavSelection) return@setOnItemSelectedListener true

            // On foldables/tablets the old Recents submenu is repurposed as the Library sort
            // selector. Reuse LibraryController's existing All Comics sort state and keep the
            // Library destination itself selected; an accent badge marks the active sort.
            val largeScreenSortIndex =
                if (binding.sideNav != null) {
                    when (id) {
                        R.id.nav_summary -> 0 // Recently Added
                        R.id.nav_ungrouped -> 1 // Title A-Z
                        R.id.nav_history -> 2 // Latest Update
                        R.id.nav_updates -> 3 // Character
                        else -> null
                    }
                } else {
                    null
                }
            if (largeScreenSortIndex != null) {
                val rootController = router.backstack.firstOrNull()?.controller
                val libraryController =
                    (rootController as? LibraryController)
                        ?: LibraryController().also { setRoot(it, R.id.nav_library) }

                libraryController.applyInkShelfLargeScreenSort(largeScreenSortIndex)
                syncInkShelfLargeScreenLibrarySort(largeScreenSortIndex)

                binding.sideNav?.let { sideNav ->
                    isSyncingNavSelection = true
                    sideNav.setCheckedItemImmediately(R.id.nav_library)
                    isSyncingNavSelection = false
                }
                return@setOnItemSelectedListener false
            }

            val currentController = router.backstack.lastOrNull()?.controller
            if (id == R.id.nav_settings) {
                showSettings()
                return@setOnItemSelectedListener false
            }
            if (!continueSwitchingTabs && currentController is BottomNavBarInterface) {
                if (!currentController.canChangeTabs {
                        continueSwitchingTabs = true
                        this@MainActivity.nav.selectedItemId = id
                    }
                ) {
                    return@setOnItemSelectedListener false
                }
            }
            continueSwitchingTabs = false
            val currentRoot = router.backstack.firstOrNull()
            when (id) {
                R.id.nav_summary, R.id.nav_ungrouped, R.id.nav_history, R.id.nav_updates -> {
                    val recentsType =
                        when (id) {
                            R.id.nav_summary -> RecentsViewType.GroupedAll
                            R.id.nav_ungrouped -> RecentsViewType.UngroupedAll
                            R.id.nav_history -> RecentsViewType.History
                            else -> RecentsViewType.Updates
                        }
                    val updatePlace = {
                        val controller =
                            router.backstack.firstOrNull()?.controller as? RecentsController
                        controller?.setViewType(recentsType)
                        controller?.hideSheet()
                        binding.mainTabs.run { selectTab(getTabAt(recentsType.mainValue)) }
                    }
                    if (currentRoot?.tag()?.toIntOrNull() != R.id.nav_recents) {
                        setRoot(RecentsController(launchWithType = recentsType), R.id.nav_recents)
                    } else {
                        val controller =
                            router.backstack.firstOrNull()?.controller as? RecentsController
                        if (controller?.presenter?.viewType != recentsType) {
                            updatePlace()
                        } else if (router.backstackSize == 1) {
                            controller.toggleSheet()
                        }
                    }
                    return@setOnItemSelectedListener true
                }
            }
            if (currentRoot?.tag()?.toIntOrNull() != id) {
                setRoot(
                    when (id) {
                        R.id.nav_library -> LibraryController()
                        R.id.nav_recents -> RecentsController(launchWithType = RecentsViewType.Updates)
                        else -> BrowseController()
                    },
                    id,
                )
            } else if (currentRoot.tag()?.toIntOrNull() == id) {
                // Re-selecting Library/Discover should behave like a normal root tab:
                // if we're deeper in that tab, return to its root. At the root itself,
                // do nothing. In particular, Discover no longer exposes J2K's hidden
                // source bottom sheet just because its nav item was tapped again.
                if (router.backstackSize > 1) {
                    binding.searchToolbar.searchView?.clearFocus()
                    router.popToRoot()
                }
            }
            id != R.id.nav_recents || (nav as? NavigationRailView)?.isExpanded != true
        }

        if (!router.hasRootController()) {
            // Set start screen
            if (!handleIntentAction(intent)) {
                goToStartingTab()
            }
        }

        // InkShelf first-run onboarding. Existing installs see it once after this feature
        // is introduced; afterwards it can always be rerun from More -> New User Setup.
        binding.root.post {
            if (!isFinishing && !isDestroyed) {
                showInkShelfSetup(force = false)
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val focusLibrarySearch = {
            val controller = router.backstack.lastOrNull()?.controller as? LibraryController
            if (controller != null && !controller.isSubClass) {
                binding.librarySearchInput.requestFocus()
                binding.librarySearchInput.setSelection(
                    binding.librarySearchInput.text?.length ?: 0,
                )
                ViewCompat.getWindowInsetsController(binding.librarySearchInput)
                    ?.show(WindowInsetsCompat.Type.ime())
            }
        }

        // InkShelf Library now uses the same real outlined search-field behaviour as Discover.
        // The field itself owns focus/input; LibraryController still owns all filtering logic.
        binding.librarySearchInput.doAfterTextChanged { editable ->
            val controller = router.backstack.lastOrNull()?.controller as? LibraryController
            if (controller != null && !controller.isSubClass) {
                controller.searchFromInkShelfField(editable?.toString())
            }
        }

        binding.librarySearchBar.setOnClickListener { focusLibrarySearch() }
        binding.librarySearchBar.setEndIconOnClickListener { focusLibrarySearch() }

        binding.librarySearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.librarySearchInput.clearFocus()
                ViewCompat.getWindowInsetsController(binding.librarySearchInput)
                    ?.hide(WindowInsetsCompat.Type.ime())
                true
            } else {
                false
            }
        }

        binding.searchToolbar.setNavigationOnClickListener {
            val rootSearchController = router.backstack.lastOrNull()?.controller
            if ((
                    rootSearchController is RootSearchInterface ||
                        (currentToolbar != binding.searchToolbar && binding.appBar.useLargeToolbar)
                ) &&
                rootSearchController !is SmallToolbarInterface
            ) {
                binding.searchToolbar.menu
                    .findItem(R.id.action_search)
                    ?.expandActionView()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.searchToolbar.searchItem?.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    val controller = router.backstack.lastOrNull()?.controller
                    if (controller is LibraryController && !controller.isSubClass) {
                        binding.librarySearchBar.isVisible = false
                    }
                    binding.appBar.compactSearchMode =
                        binding.appBar.useLargeToolbar &&
                        resources.configuration.screenHeightDp < 600
                    if (binding.appBar.compactSearchMode) {
                        setFloatingToolbar(true)
                        val controllerReady =
                            (controller as? BaseController<*>)?.isBindingInitialized != false
                        if (controllerReady) {
                            controller?.mainRecyclerView?.requestApplyInsets()
                            binding.appBar.updateAppBarAfterY(controller?.mainRecyclerView)
                        }
                        binding.appBar.y = 0f
                    }
                    binding.searchToolbar.menu.forEach { it.isVisible = false }
                    lifecycleScope.launchUI {
                        (controller as? BaseController<*>)?.onActionViewExpand(item)
                        (controller as? SettingsController)?.onActionViewExpand(item)
                        reEnableBackPressedCallBack()
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    val controller = router.backstack.lastOrNull()?.controller
                    binding.searchToolbar.searchView?.clearFocus()
                    ViewCompat.getWindowInsetsController(binding.searchToolbar)
                        ?.hide(WindowInsetsCompat.Type.ime())
                    if (controller is LibraryController && !controller.isSubClass) {
                        binding.librarySearchBar.isVisible = true
                    }
                    binding.appBar.compactSearchMode = false
                    val controllerReady =
                        (controller as? BaseController<*>)?.isBindingInitialized != false
                    if (controllerReady) {
                        controller?.mainRecyclerView?.requestApplyInsets()
                    }
                    setupSearchTBMenu(binding.toolbar.menu, true)
                    lifecycleScope.launchUI {
                        (controller as? BaseController<*>)?.onActionViewCollapse(item)
                        (controller as? SettingsController)?.onActionViewCollapse(item)
                        reEnableBackPressedCallBack()
                    }
                    return true
                }
            },
        )

        binding.appBar.alpha = 1f

        binding.searchToolbar.setOnClickListener {
            binding.searchToolbar.menu
                .findItem(R.id.action_search)
                ?.expandActionView()
        }

        binding.searchToolbar.setOnMenuItemClickListener {
            if (router.backstack
                    .lastOrNull()
                    ?.controller
                    ?.onOptionsItemSelected(it) == true
            ) {
                return@setOnMenuItemClickListener true
            } else {
                return@setOnMenuItemClickListener onOptionsItemSelected(it)
            }
        }

        nav.isVisible = !hideBottomNav
        updateControllersWithSideNavChanges()
        binding.bottomView?.visibility = if (hideBottomNav) View.GONE else binding.bottomView?.visibility ?: View.GONE
        nav.alpha = if (hideBottomNav) 0f else 1f
        router.addChangeListener(
            object : ControllerChangeHandler.ControllerChangeListener {
                override fun onChangeStarted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler,
                ) {
                    to?.view?.alpha = 1f
                    syncActivityViewWithController(to, from, isPush)
                    updateIncognitoBadge()
                    binding.appBar.isVisible = true
                    binding.appBar.alpha = 1f
                    if (binding.backShadow.isVisible && !isPush) {
                        val bA = ObjectAnimator.ofFloat(binding.backShadow, View.ALPHA, 0f)
                        from?.view?.let { view ->
                            bA.addUpdateListener {
                                binding.backShadow.x = view.x - binding.backShadow.width
                                if (router.backstackSize == 1) {
                                    to?.view?.let { toView ->
                                        nav.translationX = toView.translationX
                                    }
                                }
                            }
                        }
                        bA.doOnEnd {
                            binding.backShadow.alpha = 0.25f
                            binding.backShadow.isVisible = false
                            nav.translationX = 0f
                        }
                        bA.duration = 150
                        bA.interpolator = DecelerateInterpolator(backVelocity.takeIf { it != 0f } ?: 1f)
                        bA.start()
                    }
                    if (!isPush || router.backstackSize == 1) {
                        nav.translationY = 0f
                    }
                    snackBar?.dismiss()
                }

                override fun onChangeCompleted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler,
                ) {
                    to?.view?.x = 0f
                    nav.translationY = 0f
                    backVelocity = 0f
                    if (!isPush && from != null && from !is DialogController &&
                        ownsInkShelfHeader(to) && (to is LibraryController || to is BrowseController)
                    ) {
                        restoreInkShelfHeaderAfterPop(to)
                    }
                    showDLQueueTutorial()
                    if (!(from is DialogController || to is DialogController) && from != null) {
                        from.view?.alpha = 0f
                    }
                    if (router.backstackSize == 1) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !isPush) {
                            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
                        }
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        @Suppress("DEPRECATION")
                        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    }
                }
            },
        )

        syncActivityViewWithController(router.backstack.lastOrNull()?.controller)

        if (savedInstanceState != null) {
            pendingInkShelfResponsiveReset = true
        }

        val navIcon = if (router.backstackSize > 1) backDrawable else null
        binding.toolbar.navigationIcon = navIcon
        (router.backstack.lastOrNull()?.controller as? BaseController<*>)?.setTitle()
        (router.backstack.lastOrNull()?.controller as? SettingsController)?.setTitle()

        if (savedInstanceState == null && this !is SearchActivity) {
            // Reset Incognito Mode on relaunch
            preferences.incognitoMode().set(false)

            // Show changelog if needed
            if (Migrations.upgrade(preferences, Injekt.get(), lifecycleScope)) {
                if (!BuildConfig.DEBUG) {
                    content.post {
                        whatsNewSheet().show()
                    }
                }
            }
        }
        getExtensionUpdates(true)

        preferences
            .extensionUpdatesCount()
            .asImmediateFlowIn(lifecycleScope) {
                setExtensionsBadge()
            }
        preferences
            .incognitoMode()
            .asImmediateFlowIn(lifecycleScope) {
                updateIncognitoBadge()
            }
        preferences
            .sideNavIconAlignment()
            .asImmediateFlowIn(lifecycleScope) {
                binding.sideNav?.menuGravity =
                    when (it) {
                        1 -> Gravity.CENTER
                        2 -> Gravity.BOTTOM
                        else -> Gravity.TOP
                    }
            }
        preferences
            .sideNavExpanded()
            .asImmediateFlowIn(lifecycleScope) { expanded ->
                if (!isTablet()) return@asImmediateFlowIn
                val sideNav = binding.sideNav ?: return@asImmediateFlowIn

                // InkShelf: respect the user's saved rail state in portrait as well as landscape.
                // This removes J2K's old `expanded || isPortrait` override.
                syncRecentsNavSelection(expanded)
                if (sideNav.isExpanded != expanded) {
                    if (expanded) sideNav.expand() else sideNav.collapse()
                }
                sideNavToggleButton?.setIconResource(
                    if (expanded) R.drawable.ic_menu_collapse_24dp else R.drawable.ic_menu_expand_24dp,
                )
                updateControllersWithSideNavChanges()
            }
        setFloatingToolbar(canShowFloatingToolbar(router.backstack.lastOrNull()?.controller), changeBG = false)

        lifecycleScope.launchUI {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker
                    .getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { newLayoutInfo ->
                        hingeGapSize = 0
                        for (displayFeature: DisplayFeature in newLayoutInfo.displayFeatures) {
                            if (displayFeature is FoldingFeature &&
                                displayFeature.occlusionType == FoldingFeature.OcclusionType.FULL &&
                                displayFeature.isSeparating &&
                                displayFeature.orientation == FoldingFeature.Orientation.VERTICAL
                            ) {
                                hingeGapSize = displayFeature.bounds.width()
                            }
                        }
                        if (hingeGapSize > 0) {
                            (router.backstack.lastOrNull()?.controller as? HingeSupportedController)?.updateForHinge()
                        }
                    }
            }
        }
    }

    fun reEnableBackPressedCallBack() {
        val returnToStart = preferences.backReturnsToStart().get() && this !is SearchActivity
        backPressedCallback?.isEnabled = actionMode != null ||
            (binding.searchToolbar.hasExpandedActionView() && binding.cardFrame.isVisible) ||
            router.canStillGoBack() ||
            (returnToStart && !isOnStartingTab())
    }

    fun isOnStartingTab(): Boolean =
        if (startingTab() == R.id.nav_recents) {
            currentTabId() in
                listOf(
                    R.id.nav_summary,
                    R.id.nav_ungrouped,
                    R.id.nav_history,
                    R.id.nav_updates,
                    R.id.nav_recents,
                )
        } else {
            startingTab() == currentTabId()
        }

    // The nav view no longer keeps its selected id up to date, so go by what is on screen
    @IdRes
    private fun currentTabId(): Int =
        when (router.backstack.firstOrNull()?.controller) {
            is RecentsController -> R.id.nav_recents
            is BrowseController -> R.id.nav_browse
            else -> R.id.nav_library
        }

    /**
     * Mark the current All Comics sort in the expanded fold/tablet rail without taking the
     * destination selection away from Library. A small theme-accent badge keeps both states clear.
     */
    fun syncInkShelfLargeScreenLibrarySort(sortIndex: Int) {
        if (!isBindingInitialized) return
        val sideNav = binding.sideNav ?: return
        val sortItems =
            listOf(
                R.id.nav_summary,
                R.id.nav_ungrouped,
                R.id.nav_history,
                R.id.nav_updates,
            )
        val accent = getResourceColor(R.attr.colorPrimary)
        sortItems.forEachIndexed { index, itemId ->
            if (sideNav.menu.findItem(itemId) == null) return@forEachIndexed
            runCatching {
                sideNav.getOrCreateBadge(itemId).apply {
                    backgroundColor = accent
                    isVisible = index == sortIndex
                }
            }
        }
    }

    /**
     * Points the side nav at whichever recents entry is showing: the recents item while collapsed,
     * the matching submenu item while expanded. Pass [expanded] while toggling, the rail hasn't
     * taken the new state yet at that point.
     */
    fun syncRecentsNavSelection(expanded: Boolean? = null) {
        if (!isBindingInitialized || !this::router.isInitialized) return
        val sideNav = binding.sideNav ?: return

        // InkShelf's large-screen rail no longer exposes Recents. Its old submenu ids now belong
        // to the Library sort section, so never let a RecentsController steal that selection.
        if (sideNav.menu.findItem(R.id.nav_recents_group)?.title?.toString() ==
            getString(R.string.inkshelf_large_nav_sort)
        ) {
            return
        }

        val recents = router.backstack.firstOrNull()?.controller as? RecentsController ?: return
        val itemId =
            if (expanded ?: sideNav.isExpanded) {
                when (recents.getViewType()) {
                    RecentsViewType.GroupedAll -> R.id.nav_summary
                    RecentsViewType.UngroupedAll -> R.id.nav_ungrouped
                    RecentsViewType.History -> R.id.nav_history
                    RecentsViewType.Updates -> R.id.nav_updates
                }
            } else {
                R.id.nav_recents
            }
        // Asking the nav view which item is selected no longer works, but the item itself knows
        if (sideNav.menu.findItem(itemId)?.isChecked == true) return
        isSyncingNavSelection = true
        sideNav.setCheckedItemImmediately(itemId)
        isSyncingNavSelection = false
    }

    override fun onTitleChanged(
        title: CharSequence?,
        color: Int,
    ) {
        super.onTitleChanged(title, color)
        binding.searchToolbar.title = searchTitle
        val onExpandedController = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller !is SmallToolbarInterface else false
        binding.appBar.setTitle(title, onExpandedController)
    }

    var searchTitle: String?
        get() {
            return try {
                (router.backstack.lastOrNull()?.controller as? BaseController<*>)?.getSearchTitle()
                    ?: (router.backstack.lastOrNull()?.controller as? SettingsController)?.getSearchTitle()
            } catch (_: Exception) {
                binding.searchToolbar.title?.toString()
            }
        }
        set(title) {
            binding.searchToolbar.title = title
        }

    private fun ownsInkShelfHeader(controller: Controller?): Boolean =
        controller is InkShelfHeaderInterface &&
            (controller !is LibraryController || !controller.isSubClass)

    open fun setFloatingToolbar(
        show: Boolean,
        solidBG: Boolean = false,
        changeBG: Boolean = true,
        showSearchAnyway: Boolean = false,
    ) {
        val controller = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller else null
        val useLargeTB = binding.appBar.useLargeToolbar
        val onSearchController = canShowFloatingToolbar(controller)
        val onSmallerController = controller is SmallToolbarInterface || !useLargeTB
        currentToolbar =
            if (show && ((showSearchAnyway && onSearchController) || onSmallerController)) {
                binding.searchToolbar
            } else {
                binding.toolbar
            }
        binding.toolbar.isVisible = !(onSmallerController && onSearchController)
        if (ownsInkShelfHeader(controller)) {
            binding.toolbar.visibility = View.GONE
        }
        setSearchTBLongClick()
        val showSearchBar = (show || showSearchAnyway) && onSearchController
        val isAppBarVisible = binding.appBar.isVisible
        val needsAnim =
            if (showSearchBar) {
                !binding.cardFrame.isVisible || binding.cardFrame.alpha < 1f
            } else {
                binding.cardFrame.isVisible || binding.cardFrame.alpha > 0f
            }
        if (this::router.isInitialized &&
            needsAnim &&
            binding.appBar.useLargeToolbar &&
            !onSmallerController &&
            (showSearchAnyway || isAppBarVisible)
        ) {
            binding.appBar.background = null
            searchBarAnimation?.cancel()
            if (showSearchBar && !binding.cardFrame.isVisible) {
                binding.cardFrame.alpha = 0f
                binding.cardFrame.isVisible = true
            }
            val endValue = if (showSearchBar) 1f else 0f
            val tA = ValueAnimator.ofFloat(binding.cardFrame.alpha, endValue)
            tA.addUpdateListener { binding.cardFrame.alpha = it.animatedValue as Float }
            tA.doOnEnd { binding.cardFrame.isVisible = showSearchBar }
            tA.duration = (abs(binding.cardFrame.alpha - endValue) * 150).roundToLong()
            searchBarAnimation = tA
            tA.start()
        } else if (this::router.isInitialized &&
            (!binding.appBar.useLargeToolbar || onSmallerController || !isAppBarVisible)
        ) {
            binding.cardFrame.alpha = 1f
            binding.cardFrame.isVisible = showSearchBar
        }
        val bgColor = binding.appBar.backgroundColor ?: Color.TRANSPARENT
        if (changeBG && solidBG && bgColor == Color.TRANSPARENT) {
            binding.appBar.setBackgroundColor(getResourceColor(R.attr.colorSurface))
        }
        setupSearchTBMenu(binding.toolbar.menu)
        if (currentToolbar != binding.searchToolbar) {
            binding.searchToolbar.menu?.children?.toList()?.forEach {
                it.isVisible = false
            }
        }
        val onRoot = !this::router.isInitialized || router.backstackSize == 1
        if (!useLargeTB) {
            binding.searchToolbar.navigationIcon = if (onRoot) searchDrawable else backDrawable
        } else if (showSearchAnyway) {
            binding.searchToolbar.navigationIcon = if (!show || onRoot) searchDrawable else backDrawable
        }
        binding.searchToolbar.title = searchTitle

        // Modern Library uses one continuous activity-level gradient. Keep every piece of
        // Library chrome transparent so the same drawable shows through the status bar, toolbar,
        // large title, tabs and the Library content below.
        val activeController = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller else null
        if (activeController is LibraryController && !activeController.isSubClass) {
            if (getPrefTheme(preferences) == Themes.MODERN_LIBRARY) {
                binding.mainContent.background = contextCompatDrawable(R.drawable.modern_library_theme_background)
                binding.controllerContainer.background = null
                binding.appBar.modernLibraryGradientEnabled = true
                binding.appBar.background = null
                binding.statusBar.background = null
                binding.statusBar.gradientBackgroundColor = Color.TRANSPARENT
                binding.statusBar.foreground = contextCompatDrawable(R.drawable.modern_library_theme_background)
                binding.toolbar.background = null
                binding.bigToolbar.background = null
                binding.cardFrame.background = null
                binding.tabsFrameLayout.background = null
            } else {
                binding.appBar.modernLibraryGradientEnabled = false
                binding.statusBar.foreground = null
                binding.mainContent.setBackgroundColor(getResourceColor(R.attr.background))
                binding.appBar.setBackgroundColor(getResourceColor(R.attr.colorSurface))
                binding.statusBar.background = null
                binding.statusBar.gradientBackgroundColor = getColor(R.color.status_bar)
            }
        }
    }

    private fun setSearchTBLongClick() {
        binding.searchToolbar.setOnLongClickListener {
            binding.searchToolbar.menu
                .findItem(R.id.action_search)
                ?.expandActionView()
            val visibleController = router.backstack.lastOrNull()?.controller as? BaseController<*>
            val longClickQuery = visibleController?.onSearchActionViewLongClickQuery()
            if (longClickQuery != null) {
                binding.searchToolbar.searchView?.setQuery(longClickQuery, true)
                return@setOnLongClickListener true
            }
            val clipboard: ClipboardManager? = getSystemService()
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                clipboard.primaryClip?.getItemAt(0)?.text?.let { text ->
                    binding.searchToolbar.searchView?.setQuery(text, true)
                }
            }
            true
        }
    }

    private fun setNavBarColor(insets: WindowInsetsCompat?) {
        if (insets == null) return
        binding.navBar.backgroundColor =
            when {
                // if the android q+ device has gesture nav, transparent nav bar
                // this is here in case some crazy with a notch uses landscape
                insets.isBottomTappable() -> {
                    getColor(android.R.color.transparent)
                }
                // if in landscape with 2/3 button mode, fully opaque nav bar
                insets.hasSideNavBar() -> {
                    getResourceColor(R.attr.colorSurfaceContainer)
                }
                // if in portrait with 2/3 button mode, translucent nav bar
                else -> {
                    ColorUtils.setAlphaComponent(
                        getResourceColor(R.attr.colorSurfaceContainer),
                        179,
                    )
                }
            }
    }

    override fun startSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        binding.actionModeStatusBar.backgroundColor = getResourceColor(R.attr.colorSurfaceContainer)
        binding.actionModeStatusBar.animate().cancel()
        binding.actionModeStatusBar.alpha = 0f
        binding.actionModeStatusBar.isVisible = true
        binding.actionModeStatusBar
            .animate()
            .alpha(1f)
            .setDuration(ACTION_MODE_FADE_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        actionMode = super.startSupportActionMode(callback)
        reEnableBackPressedCallBack()
        return actionMode
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        binding.actionModeStatusBar.animate().cancel()
        binding.actionModeStatusBar
            .animate()
            .alpha(0f)
            .setDuration(ACTION_MODE_FADE_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { binding.actionModeStatusBar.isVisible = false }
            .start()
        actionMode = null
        reEnableBackPressedCallBack()
        super.onSupportActionModeFinished(mode)
    }

    fun setStatusBarColorTransparent(show: Boolean) {
        binding.statusBar.gradientBackgroundColor =
            if (show) {
                ColorUtils.setAlphaComponent(binding.statusBar.gradientBackgroundColor ?: Color.TRANSPARENT, 0)
            } else {
                val color = getColor(R.color.status_bar)
                ColorUtils.setAlphaComponent(binding.statusBar.gradientBackgroundColor ?: color, Color.alpha(color))
            }
    }

    private fun setExtensionsBadge() {
        val updates = preferences.extensionUpdatesCount().get()
        if (updates > 0) {
            val badge = nav.getOrCreateBadge(R.id.nav_browse)
            badge.number = updates
        } else {
            nav.removeBadge(R.id.nav_browse)
        }
    }

    override fun onResume() {
        super.onResume()
        checkForAppUpdates()
        getExtensionUpdates(false)
        setExtensionsBadge()
        DownloadJob.callListeners(downloadManager = downloadManager)
        showDLQueueTutorial()
        reEnableBackPressedCallBack()

        // Rotation, fold/unfold and some multi-window width changes can recreate or remeasure
        // the Activity while Conductor retains the current controller. Re-assert InkShelf's
        // custom masthead/tabs after the new view hierarchy has settled.
        //
        // On the first resume, also reset the app-bar position. The initial Library/Discover
        // controller can restore its scroll/app-bar state after onCreate(), which previously
        // hid the new bitmap header until a bottom-nav tap forced another toolbar sync.
        val firstInkShelfResume = initialInkShelfChromeSyncPending
        restoreInkShelfResponsiveChrome(pendingInkShelfResponsiveReset || firstInkShelfResume)
        pendingInkShelfResponsiveReset = false
        initialInkShelfChromeSyncPending = false

        if (firstInkShelfResume) {
            // One final pass after the retained controller has had time to attach/restore.
            binding.root.postDelayed(
                { restoreInkShelfResponsiveChrome(resetAppBarPosition = true) },
                140L,
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pendingInkShelfResponsiveReset = true
        restoreInkShelfResponsiveChrome(resetAppBarPosition = true)
        pendingInkShelfResponsiveReset = false
    }

    private fun showDLQueueTutorial() {
        // Recents is intentionally absent from the InkShelf large-screen rail, so do not point
        // the old download-queue tutorial at one of the repurposed Sort rows.
        if (binding.sideNav != null) return

        if (router.backstackSize == 1 &&
            this !is SearchActivity &&
            downloadManager.hasQueue() &&
            !preferences.shownDownloadQueueTutorial().get()
        ) {
            if (!isBindingInitialized) return
            val recentsItem =
                if (isTablet() && binding.sideNav != null) {
                    nav.getItemView(R.id.nav_summary)
                } else {
                    nav.getItemView(R.id.nav_recents)
                } ?: return
            preferences.shownDownloadQueueTutorial().set(true)
            TapTargetView.showFor(
                this,
                TapTarget
                    .forView(
                        recentsItem,
                        getString(R.string.manage_whats_downloading),
                        getString(R.string.visit_recents_for_download_queue),
                    ).outerCircleColorInt(getResourceColor(R.attr.colorPrimary))
                    .outerCircleAlpha(0.95f)
                    .titleTextSize(
                        20,
                    ).titleTextColorInt(getResourceColor(R.attr.colorOnPrimary))
                    .descriptionTextSize(16)
                    .descriptionTextColorInt(getResourceColor(R.attr.colorOnPrimary))
                    .icon(contextCompatDrawable(R.drawable.ic_recent_read_32dp))
                    .targetCircleColor(android.R.color.white)
                    .targetRadius(45),
                object : TapTargetView.Listener() {
                    override fun onTargetClick(view: TapTargetView) {
                        super.onTargetClick(view)
                        nav.selectedItemId = R.id.nav_recents
                    }
                },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        snackBar?.dismiss()
        setStartingTab()
        saveExtras()
    }

    private fun saveExtras() {
        mangaShortcutManager.updateShortcuts(this)
        MangaCoverMetadata.savePrefs()
    }

    private fun checkForAppUpdates() {
        if (isUpdaterEnabled) {
            lifecycleScope.launchIO {
                try {
                    val result = updateChecker.checkForUpdate(this@MainActivity)
                    if (result is AppUpdateResult.NewUpdate) {
                        val body = result.release.info
                        val url = result.release.downloadLink
                        val isBeta = result.release.preRelease == true

                        // Create confirmation window
                        withContext(Dispatchers.Main) {
                            showNotificationPermissionPrompt()
                            AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                            AboutController.NewUpdateDialogController(body, url, isBeta).showDialog(router)
                        }
                    }
                } catch (error: Exception) {
                    Timber.e(error)
                }
            }
        }
    }

    fun getExtensionUpdates(force: Boolean) {
        if ((force && extensionManager.availableExtensionsFlow.value.isEmpty()) ||
            Date().time >= preferences.lastExtCheck().get() + TimeUnit.HOURS.toMillis(6)
        ) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    extensionManager.findAvailableExtensions()
                    val pendingUpdates =
                        ExtensionApi().checkForUpdates(
                            this@MainActivity,
                            extensionManager.availableExtensionsFlow.value.takeIf { it.isNotEmpty() },
                        )
                    preferences.extensionUpdatesCount().set(pendingUpdates.size)
                    preferences.lastExtCheck().set(Date().time)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun showNotificationPermissionPrompt(showAnyway: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
        val hasPermission = ActivityCompat.checkSelfPermission(this, notificationPermission)
        if (hasPermission != PackageManager.PERMISSION_GRANTED &&
            (!preferences.hasShownNotifPermission().get() || showAnyway)
        ) {
            preferences.hasShownNotifPermission().set(true)
            requestNotificationPermissionLauncher.launch((notificationPermission))
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (!handleIntentAction(intent)) {
            super.onNewIntent(intent)
        }
    }

    protected open fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }
        when (intent.action) {
            SHORTCUT_LIBRARY -> nav.selectedItemId = R.id.nav_library
            SHORTCUT_RECENTLY_UPDATED, SHORTCUT_RECENTLY_READ, SHORTCUT_RECENTS -> {
                if (currentTabId() != R.id.nav_recents) {
                    nav.selectedItemId = R.id.nav_recents
                } else {
                    router.popToRoot()
                }
                if (intent.action == SHORTCUT_RECENTS) return true
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? RecentsController
                    controller?.tempJumpTo(
                        when (intent.action) {
                            SHORTCUT_RECENTLY_UPDATED -> RecentsViewType.Updates
                            else -> RecentsViewType.History
                        },
                    )
                }
            }
            SHORTCUT_BROWSE -> nav.selectedItemId = R.id.nav_browse
            SHORTCUT_EXTENSIONS -> {
                if (currentTabId() != R.id.nav_browse) {
                    nav.selectedItemId = R.id.nav_browse
                } else {
                    router.popToRoot()
                }
                val runUpdateAll = intent.getBooleanExtra(EXTRA_UPDATE_ALL_EXTENSIONS, false)
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? BrowseController
                    controller?.showSheet()
                    if (runUpdateAll) {
                        controller?.updateAllPendingExtensions()
                    }
                }
            }
            SHORTCUT_MANGA -> {
                val extras = intent.extras ?: return false
                if (router.backstack.isEmpty()) nav.selectedItemId = R.id.nav_library
                router.pushController(MangaDetailsController(extras).withFadeTransaction())
            }
            SHORTCUT_UPDATE_NOTES -> {
                val extras = intent.extras ?: return false
                if (router.backstack.isEmpty()) nav.selectedItemId = R.id.nav_library
                if (router.backstack.lastOrNull()?.controller !is AboutController.NewUpdateDialogController) {
                    AboutController.NewUpdateDialogController(extras).showDialog(router)
                }
            }
            SHORTCUT_SOURCE -> {
                val extras = intent.extras ?: return false
                if (router.backstack.isEmpty()) nav.selectedItemId = R.id.nav_library
                router.pushController(BrowseSourceController(extras).withFadeTransaction())
            }
            SHORTCUT_DOWNLOADS -> {
                nav.selectedItemId = R.id.nav_recents
                router.popToRoot()
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? RecentsController
                    controller?.showSheet()
                }
            }
            Intent.ACTION_VIEW -> {
                // Deep link to add extension store
                if (intent.isAddExtensionRepoIntent()) {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        if (!router.hasRootController()) goToStartingTab()
                        router.popToRoot()
                        router.pushController(RepoController(repoUrl).withFadeTransaction())
                    }
                }
            }
            else -> return false
        }
        return true
    }

    private fun Intent.isAddExtensionRepoIntent(): Boolean =
        (scheme == "tachiyomi" && data?.host == "add-repo") ||
            (scheme == "mihon" && data?.host == "extension-store")

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val controller = router.backstack.lastOrNull()?.controller) {
            is MangaDetailsController -> {
                val source = controller.presenter.source as? HttpSource ?: return
                val url =
                    try {
                        source.getMangaUrl(controller.presenter.manga)
                    } catch (_: Exception) {
                        return
                    }
                outContent.webUri = url.toUri()
            }
            is BrowseSourceController -> {
                val source = controller.presenter.source as? HttpSource ?: return
                outContent.webUri = source.baseUrl.toUri()
            }
        }
    }

    /**
     * Conductor treats [Activity.recreate] the same as a real configuration change (the
     * framework reports [isChangingConfigurations] as true during it either way), and only
     * fully destroys its Controller backstack when that's false. That's correct for a genuine
     * rotation, but when we trigger `recreate()` ourselves (e.g. from a settings change) it
     * means the entire old Controller/view graph is retained instead of released, leaking one
     * screen's worth of views per call. [recreateFully] forces the real-destroy path for those
     * self-triggered recreates while leaving rotation handling untouched.
     */
    fun recreateFully() {
        recreatingForSettingsChange = true
        recreate()
    }

    override fun isChangingConfigurations(): Boolean = !recreatingForSettingsChange && super.isChangingConfigurations()

    override fun onDestroy() {
        super.onDestroy()
        overflowDialog?.dismiss()
        overflowDialog = null
        if (isBindingInitialized) {
            binding.appBar.mainActivity = null
            binding.toolbar.setNavigationOnClickListener(null)
            binding.searchToolbar.setNavigationOnClickListener(null)
        }
    }

    private fun pressingBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ViewCompat
                .getRootWindowInsets(window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        ) {
            WindowInsetsControllerCompat(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        } else if (actionMode != null) {
            actionMode?.finish()
        } else if (binding.searchToolbar.hasExpandedActionView() && binding.cardFrame.isVisible) {
            binding.searchToolbar.collapseActionView()
        } else {
            backPress()
        }
    }

    override fun finish() {
        if (!preferences.backReturnsToStart().get() && this !is SearchActivity) {
            setStartingTab()
        }
        if (this !is SearchActivity) {
            SecureActivityDelegate.locked = true
        }
        saveExtras()
        super.finish()
    }

    @Suppress("DEPRECATION")
    protected open fun backPress() {
        val controller = router.backstack.lastOrNull()?.controller
        if (if (router.backstackSize == 1) controller?.handleBack() != true else !router.handleBack()) {
            if (preferences.backReturnsToStart().get() &&
                this !is SearchActivity &&
                !isOnStartingTab()
            ) {
                goToStartingTab()
            }
        }
    }

    protected val nav: NavigationBarView
        get() = binding.bottomNav ?: binding.sideNav!!

    private fun setStartingTab() {
        if (this is SearchActivity || !isBindingInitialized) return
        val tabId = currentTabId()
        if (tabId != R.id.nav_browse &&
            preferences.startingTab().get() >= 0
        ) {
            preferences.startingTab().set(
                when (tabId) {
                    R.id.nav_library -> 0
                    else -> 1
                },
            )
        }
    }

    @IdRes
    private fun startingTab(): Int =
        when (preferences.startingTab().get()) {
            0, -1, 1, -2 -> R.id.nav_library
            -3 -> R.id.nav_browse
            else -> R.id.nav_library
        }

    private fun goToStartingTab() {
        nav.selectedItemId = startingTab()
    }

    private fun setRoot(
        controller: Controller,
        id: Int,
    ) {
        router.setRoot(controller.withFadeInTransaction().tag(id.toString()))
        scheduleInkShelfRootChromeSync(resetAppBarPosition = true)
    }

    override fun onPreparePanel(
        featureId: Int,
        view: View?,
        menu: Menu,
    ): Boolean {
        val prepare = super.onPreparePanel(featureId, view, menu)
        if (canShowFloatingToolbar(router.backstack.lastOrNull()?.controller)) {
            val searchItem = menu.findItem(R.id.action_search)
            searchItem?.isVisible = false
        }
        setupSearchTBMenu(menu)
        return prepare
    }

    fun setSearchTBMenuIfInvalid() = setupSearchTBMenu(binding.toolbar.menu)

    private fun setupSearchTBMenu(
        menu: Menu?,
        showAnyway: Boolean = false,
    ) {
        val toolbar = binding.searchToolbar
        val currentItemsId =
            toolbar.menu.children
                .toList()
                .map { it.itemId }
        val newMenuIds =
            menu
                ?.children
                ?.toList()
                ?.map { it.itemId }
                .orEmpty()
        menu?.children?.toList()?.let { menuItems ->
            val searchActive = toolbar.isSearchExpanded
            menuItems.forEachIndexed { index, oldMenuItem ->
                if (oldMenuItem.itemId == R.id.action_search) return@forEachIndexed
                val isVisible =
                    oldMenuItem.isVisible &&
                        (currentToolbar == toolbar || !binding.appBar.useLargeToolbar) &&
                        (!searchActive || showAnyway)
                addOrUpdateMenuItem(oldMenuItem, toolbar.menu, isVisible, currentItemsId, index)
            }
        }
        toolbar.menu.children.toList().forEach {
            if (it.itemId != R.id.action_search && !newMenuIds.contains(it.itemId)) {
                toolbar.menu.removeItem(it.itemId)
            }
        }

        // Done because sometimes ActionMenuItemViews have a width/height of 0 and never update
        val actionMenuView = toolbar.findChild<ActionMenuView>()
        if (binding.appBar.isVisible &&
            toolbar.isVisible &&
            toolbar.width > 0 &&
            actionMenuView?.children?.any { it.width == 0 } == true
        ) {
            actionMenuView.children.forEach {
                if (it !is ActionMenuItemView) return@forEach
                it.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = actionButtonSize.first
                    height = actionButtonSize.second
                }
            }
            actionMenuView.requestLayout()
        }

        val controller = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller else null
        if (canShowFloatingToolbar(controller)) {
            binding.toolbar.menu.removeItem(R.id.action_search)
        }
    }

    private fun addOrUpdateMenuItem(
        oldMenuItem: MenuItem,
        menu: Menu,
        isVisible: Boolean,
        currentItemsId: List<Int>,
        index: Int,
    ) {
        if (currentItemsId.contains(oldMenuItem.itemId)) {
            val newItem = menu.findItem(oldMenuItem.itemId) ?: return
            if (newItem.icon != oldMenuItem.icon) {
                newItem.icon = oldMenuItem.icon
            }
            if (newItem.isVisible != isVisible) {
                newItem.isVisible = isVisible
            }
            updateSubMenu(oldMenuItem, newItem)
            return
        }
        val menuItem =
            if (oldMenuItem.hasSubMenu()) {
                menu
                    .addSubMenu(
                        oldMenuItem.groupId,
                        oldMenuItem.itemId,
                        index,
                        oldMenuItem.title,
                    ).item
            } else {
                menu.add(
                    oldMenuItem.groupId,
                    oldMenuItem.itemId,
                    index,
                    oldMenuItem.title,
                )
            }
        menuItem.isVisible = isVisible
        menuItem.actionView = oldMenuItem.actionView
        menuItem.icon = oldMenuItem.icon
        menuItem.isChecked = oldMenuItem.isChecked
        updateSubMenu(oldMenuItem, menuItem)
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    @SuppressLint("RestrictedApi")
    private fun updateSubMenu(
        oldMenuItem: MenuItem,
        menuItem: MenuItem,
    ) {
        if (oldMenuItem.hasSubMenu()) {
            val oldSubMenu = oldMenuItem.subMenu ?: return
            val newMenuIds = oldSubMenu.children.toList().map { it.itemId }
            val currentItemsId =
                menuItem.subMenu
                    ?.children
                    ?.toList()
                    ?.map { it.itemId } ?: return
            var isExclusiveCheckable = false
            var isCheckable = false
            oldSubMenu.children.toList().forEachIndexed { index, oldSubMenuItem ->
                val isSubVisible = oldSubMenuItem.isVisible
                addOrUpdateMenuItem(oldSubMenuItem, menuItem.subMenu!!, isSubVisible, currentItemsId, index)
                if (!isExclusiveCheckable) {
                    isExclusiveCheckable = (oldSubMenuItem as? MenuItemImpl)?.isExclusiveCheckable ?: false
                }
                if (!isCheckable) {
                    isCheckable = oldSubMenuItem.isCheckable
                }
            }
            menuItem.subMenu?.setGroupCheckable(oldSubMenu.children.first().groupId, isCheckable, isExclusiveCheckable)
            menuItem.subMenu?.children?.toList()?.forEach {
                if (!newMenuIds.contains(it.itemId)) {
                    menuItem.subMenu?.removeItem(it.itemId)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_more -> {
                if (overflowDialog != null) return false
                val overflowDialog = OverflowDialog(this)
                this.overflowDialog = overflowDialog
                overflowDialog.blurBehindWindow(
                    window,
                    onDismiss = {
                        this.overflowDialog = null
                    },
                )
                overflowDialog.show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    fun showInkShelfSetup(force: Boolean = false) {
        if (!force && InkShelfSetupDialog.isSetupComplete(this)) return
        if (inkShelfSetupDialog?.isShowing == true) return

        val dialog =
            InkShelfSetupDialog(
                activity = this,
                firstRun = !InkShelfSetupDialog.isSetupComplete(this),
            )
        inkShelfSetupDialog = dialog
        dialog.setOnDismissListener {
            if (inkShelfSetupDialog === dialog) {
                inkShelfSetupDialog = null
            }
        }
        dialog.show()
    }

    fun openDiscoverAfterInkShelfSetup() {
        nav.selectedItemId = R.id.nav_browse
    }

    fun showSettings() {
        router.pushController(SettingsMainController().withFadeTransaction())
    }

    fun showAbout() {
        router.pushController(AboutController().withFadeTransaction())
    }

    fun showStats() {
        router.pushController(StatsController().withFadeTransaction())
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            gestureDetector?.onTouchEvent(it)
            (router.backstack.lastOrNull()?.controller as? LibraryController)?.handleGeneralEvent(it)
        }
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            if (snackBar != null && snackBar!!.isShown) {
                val sRect = Rect()
                snackBar!!.view.getGlobalVisibleRect(sRect)

                val extRect: Rect? = if (extraViewForUndo != null) Rect() else null
                extraViewForUndo?.getGlobalVisibleRect(extRect)
                // This way the snackbar will only be dismissed if
                // the user clicks outside it.
                if (canDismissSnackBar &&
                    !sRect.contains(ev.x.toInt(), ev.y.toInt()) &&
                    (extRect == null || !extRect.contains(ev.x.toInt(), ev.y.toInt()))
                ) {
                    snackBar?.dismiss()
                    snackBar = null
                    extraViewForUndo = null
                }
            } else if (snackBar != null) {
                snackBar = null
                extraViewForUndo = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    protected fun canShowFloatingToolbar(controller: Controller?) = (controller is FloatingSearchInterface && controller.showFloatingBar())

    /**
     * Resyncs the app bar's toolbar mode and the floating search toolbar's menu with the
     * currently visible controller. Needed after a config change (e.g. entering/exiting
     * split screen or a smaller freeform window) since [ExpandedAppBarLayout] can't reach
     * [router] itself to know which controller is actually showing.
     */
    private fun scheduleInkShelfRootChromeSync(resetAppBarPosition: Boolean) {
        if (!isBindingInitialized || !this::router.isInitialized) return

        binding.root.post {
            if (!isBindingInitialized || !this::router.isInitialized) return@post

            val controller = router.backstack.lastOrNull()?.controller
            if (!ownsInkShelfHeader(controller)) return@post

            // Force the new bitmap-backed header to be ready on the very first root frame.
            // Previously this was reliably re-applied only after a bottom-nav interaction.
            refreshToolbarMode()
            binding.inkshelfHeaderContainer.alpha = 1f
            binding.inkshelfHeaderContainer.isVisible = true
            binding.inkshelfHeaderArt.alpha = 1f
            binding.inkshelfHeaderArt.isVisible = true
            binding.inkshelfHeaderContainer.requestLayout()
            binding.inkshelfHeaderArt.requestLayout()
            binding.inkshelfHeaderContainer.invalidate()
            binding.inkshelfHeaderArt.invalidate()

            restoreInkShelfResponsiveChrome(resetAppBarPosition)
        }
    }

    private fun restoreInkShelfHeaderAfterPop(controller: Controller) {
        val destinationView = controller.view ?: return
        // Run after the controllers finish their POP callbacks and restore their own chrome.
        destinationView.post {
            if (isFinishing || isDestroyed || !isBindingInitialized ||
                router.backstack.lastOrNull()?.controller !== controller ||
                controller.view !== destinationView || !destinationView.isAttachedToWindow
            ) return@post

            refreshToolbarMode()
            binding.appBar.doOnNextLayout {
                if (isFinishing || isDestroyed ||
                    router.backstack.lastOrNull()?.controller !== controller ||
                    controller.view !== destinationView || !destinationView.isAttachedToWindow
                ) return@doOnNextLayout

                // scrollViewWith recalculates content padding in its inset listener. A header
                // layout alone only refreshes its cached height, leaving retained padding stale.
                val recycler = controller.mainRecyclerView ?: return@doOnNextLayout
                recycler.doOnNextLayout {
                    if (!isFinishing && !isDestroyed &&
                        router.backstack.lastOrNull()?.controller === controller &&
                        controller.view === destinationView && destinationView.isAttachedToWindow
                    ) {
                        binding.appBar.updateAppBarAfterY(recycler)
                    }
                }
                recycler.requestApplyInsets()
                recycler.requestLayout()
            }
            binding.appBar.requestLayout()
        }
    }

    private fun applyInkShelfHeaderAppearance() {
        if (!isBindingInitialized) return

        val primary = getResourceColor(R.attr.colorPrimary)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(primary, hsl)

        // The approved InkShelf artwork's dominant violet is approximately 275 degrees.
        // Rotate that hue to the selected Appearance accent while preserving the artwork's
        // saturation, luminance, deep blacks and white lettering.
        val angle = Math.toRadians((hsl[0] - INKSHELF_HEADER_SOURCE_HUE).toDouble())
        val c = cos(angle).toFloat()
        val sn = sin(angle).toFloat()

        val matrix =
            ColorMatrix(
                floatArrayOf(
                    0.213f + c * 0.787f - sn * 0.213f,
                    0.715f - c * 0.715f - sn * 0.715f,
                    0.072f - c * 0.072f + sn * 0.928f,
                    0f,
                    0f,
                    0.213f - c * 0.213f + sn * 0.143f,
                    0.715f + c * 0.285f + sn * 0.140f,
                    0.072f - c * 0.072f - sn * 0.283f,
                    0f,
                    0f,
                    0.213f - c * 0.213f - sn * 0.787f,
                    0.715f - c * 0.715f + sn * 0.715f,
                    0.072f + c * 0.928f + sn * 0.072f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                ),
            )

        binding.inkshelfHeaderArt.colorFilter = ColorMatrixColorFilter(matrix)
        binding.inkshelfHeaderArt.imageAlpha = 255
    }

    private fun applyInkShelfMainTitleTreatment(controller: Controller?) {
        if (ownsInkShelfHeader(controller)) {
            applyInkShelfHeaderAppearance()
        }

        val isRootLibrary = controller is LibraryController && !controller.isSubClass

        binding.inkshelfPageKicker.isVisible = isRootLibrary
        binding.inkshelfPageTitleUnderline.isVisible = isRootLibrary

        if (isRootLibrary) {
            val headerHeight = resources.getDimensionPixelSize(R.dimen.inkshelf_header_art_height)

            binding.inkshelfPageKicker.text = getString(R.string.inkshelf_library_kicker)
            binding.inkshelfPageKicker.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = headerHeight + 12.dpToPx
            }

            binding.bigTitle.text = getString(R.string.inkshelf_library_title)
            binding.bigTitle.alpha = 1f
            binding.bigTitle.minimumHeight = 0
            binding.bigTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                topMargin = headerHeight + 30.dpToPx
            }

            // Padding is included in ExpandedAppBarLayout's existing height calculation, so
            // the underline gets room without covering the Library search/tabs below.
            binding.bigToolbar.updatePadding(bottom = 16.dpToPx)
        } else {
            binding.bigTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = 0
            }
            binding.bigToolbar.updatePadding(bottom = 6.dpToPx)
        }
    }

    private fun restoreInkShelfResponsiveChrome(resetAppBarPosition: Boolean) {
        if (!isBindingInitialized || !this::router.isInitialized) return

        binding.root.post {
            if (!isBindingInitialized || !this::router.isInitialized) return@post

            val controller = router.backstack.lastOrNull()?.controller
            if (!ownsInkShelfHeader(controller)) return@post

            // Re-assert the views/resources selected for the new orientation/width.
            refreshToolbarMode()
            binding.inkshelfHeaderContainer.alpha = 1f
            binding.inkshelfHeaderContainer.isVisible = true
            binding.appBar.requestLayout()

            if (resetAppBarPosition) {
                // A collapsed app-bar translation can survive a retained controller and make
                // the entire InkShelf header/tabs look as though they vanished after rotation.
                binding.appBar.lockYPos = false
                binding.appBar.y = 0f
            }

            if (controller is LibraryController) {
                controller.refreshInkShelfResponsiveLayout(resetAppBarPosition)
            }
        }
    }

    fun refreshToolbarMode() {
        val controller = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller else null
        val ownsHeader = ownsInkShelfHeader(controller)
        binding.appBar.setMainToolbarSuppressed(ownsHeader)
        binding.librarySearchBar.isVisible = controller is LibraryController && !controller.isSubClass && !binding.searchToolbar.isSearchExpanded
        binding.inkshelfHeaderContainer.isVisible = ownsHeader
        if (ownsHeader) {
            // Keep the original large-title view as an invisible fixed-height spacer.
            // J2K's existing app-bar/inset code measures this view, so the Library recycler
            // keeps exactly the same positioning logic as v12.35 instead of being pushed away.
            binding.bigTitle.isVisible = true
            binding.bigTitle.alpha = 0f
            val inkShelfHeaderHeight = resources.getDimensionPixelSize(R.dimen.inkshelf_header_min_height)
            binding.bigTitle.minimumHeight = inkShelfHeaderHeight
            binding.bigTitle.updateLayoutParams<ViewGroup.LayoutParams> {
                height = inkShelfHeaderHeight
            }
            binding.bigSubtitle.isVisible = false
            binding.bigIconLayout.isVisible = false
            binding.bigToolbar.background = null
        } else {
            binding.bigTitle.alpha = 1f
            binding.bigTitle.minimumHeight = 0
            binding.bigTitle.updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.bigToolbar.background = null
        }
        applyInkShelfMainTitleTreatment(controller)
        binding.appBar.setToolbarModeBy(controller)
        setFloatingToolbar(canShowFloatingToolbar(controller), changeBG = false)
    }

    /**
     * Updates the toolbar incognito badge, taking into account both the global incognito
     * toggle and any per-extension incognito state for the source currently on screen.
     */
    private fun updateIncognitoBadge() {
        if (!isBindingInitialized || !this::router.isInitialized) return
        val sourceId = (router.backstack.lastOrNull()?.controller as? BaseController<*>)?.getIncognitoSourceId()
        val incognito = isIncognitoModeForSource(sourceId, preferences)
        binding.toolbar.setIncognitoMode(incognito)
        binding.searchToolbar.setIncognitoMode(incognito)
        SecureActivityDelegate.setSecure(this, sourceId)
    }

    protected open fun syncActivityViewWithController(
        to: Controller?,
        from: Controller? = null,
        isPush: Boolean = false,
    ) {
        if (from is DialogController || to is DialogController) {
            return
        }
        reEnableBackPressedCallBack()

        // The inline Library search owns the IME while Library is visible.
        // Drop focus when navigating away without touching the working POP/header repair.
        if (
            from is LibraryController &&
            !from.isSubClass &&
            to !is LibraryController
        ) {
            binding.librarySearchInput.clearFocus()
            ViewCompat.getWindowInsetsController(binding.librarySearchInput)
                ?.hide(WindowInsetsCompat.Type.ime())
        }

        // Do not let a Library search stay expanded after opening another screen.
        // With InkShelf's custom Library header the stock expanded search toolbar is hidden,
        // so preserving it across a push can leave the user with filtered results and no
        // visible way to clear the query when they come back.
        if (
            from is LibraryController &&
            !from.isSubClass &&
            to !is LibraryController &&
            binding.searchToolbar.isSearchExpanded
        ) {
            binding.searchToolbar.searchView?.setQuery("", false)
            binding.searchToolbar.searchView?.clearFocus()
            ViewCompat.getWindowInsetsController(binding.searchToolbar)
                ?.hide(WindowInsetsCompat.Type.ime())
            binding.searchToolbar.collapseActionView()
            binding.librarySearchBar.isVisible = true
        }

        val ownsHeader = ownsInkShelfHeader(to)
        binding.appBar.setMainToolbarSuppressed(ownsHeader)
        binding.librarySearchBar.isVisible = to is LibraryController && !to.isSubClass && !binding.searchToolbar.isSearchExpanded
        binding.inkshelfHeaderContainer.isVisible = ownsHeader
        if (ownsHeader) {
            // Use the stock large-title slot only as a spacer so all existing Library content
            // insets remain on the proven v12.35 path. The visible title is our InkShelf masthead.
            binding.bigTitle.isVisible = true
            binding.bigTitle.alpha = 0f
            val inkShelfHeaderHeight = resources.getDimensionPixelSize(R.dimen.inkshelf_header_min_height)
            binding.bigTitle.minimumHeight = inkShelfHeaderHeight
            binding.bigTitle.updateLayoutParams<ViewGroup.LayoutParams> {
                height = inkShelfHeaderHeight
            }
            binding.bigSubtitle.isVisible = false
            binding.bigIconLayout.isVisible = false
            binding.bigToolbar.background = null
        } else {
            binding.bigTitle.alpha = 1f
            binding.bigTitle.minimumHeight = 0
            binding.bigTitle.updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.bigToolbar.background = null
        }
        applyInkShelfMainTitleTreatment(to)
        setFloatingToolbar(canShowFloatingToolbar(to))

        val isRootModernLibrary = to is LibraryController && !to.isSubClass
        if (isRootModernLibrary && getPrefTheme(preferences) == Themes.MODERN_LIBRARY) {
            // The Library owns a full-screen gradient. ExpandedAppBarLayout also hard-draws the
            // same gradient so later Material/J2K colorSurface updates cannot turn the header black.
            binding.mainContent.background = contextCompatDrawable(R.drawable.modern_library_theme_background)
            binding.controllerContainer.background = null
            binding.appBar.modernLibraryGradientEnabled = true
            binding.appBar.background = null
            binding.statusBar.background = null
            binding.statusBar.gradientBackgroundColor = Color.TRANSPARENT
            binding.statusBar.foreground = contextCompatDrawable(R.drawable.modern_library_theme_background)
            binding.toolbar.background = null
            binding.bigToolbar.background = null
            binding.cardFrame.background = null
            binding.tabsFrameLayout.background = null
        } else {
            // Never leak the Library gradient into manga details, Browse, Settings, etc.
            binding.appBar.modernLibraryGradientEnabled = false
            binding.statusBar.foreground = null
            binding.mainContent.setBackgroundColor(getResourceColor(R.attr.background))
            binding.appBar.setBackgroundColor(getResourceColor(R.attr.colorSurface))
            binding.statusBar.background = null
            binding.statusBar.gradientBackgroundColor = getColor(R.color.status_bar)
            binding.bigSubtitle.isVisible = false
        }

        // If a destination does not own tabs, immediately remove any Library tabs left from the
        // previous controller. This prevents ALL / READING / COMPLETED... appearing on detail pages.
        if ((to as? TabbedInterface)?.showTabs() != true) {
            showTabBar(show = false, animate = false)
            binding.mainTabs.clearOnTabSelectedListeners()
            binding.mainTabs.removeAllTabs()
        }

        val onRoot = router.backstackSize == 1
        val navIcon = if (onRoot) searchDrawable else backDrawable
        binding.toolbar.navigationIcon = if (onRoot) null else backDrawable
        if (ownsHeader) {
            binding.toolbar.visibility = View.GONE
        }
        binding.searchToolbar.navigationIcon = if (binding.appBar.useLargeToolbar) searchDrawable else navIcon
        binding.searchToolbar.subtitle = null

        nav.visibility = if (!hideBottomNav) View.VISIBLE else nav.visibility
        if (nav == binding.sideNav) {
            nav.isVisible = !hideBottomNav
            updateControllersWithSideNavChanges(from)
            nav.alpha = 1f
        } else {
            animationSet?.cancel()
            animationSet = AnimatorSet()
            val alphaAnimation =
                ValueAnimator.ofFloat(
                    nav.alpha,
                    if (hideBottomNav) 0f else 1f,
                )
            alphaAnimation.addUpdateListener { valueAnimator ->
                nav.alpha = valueAnimator.animatedValue as Float
            }
            alphaAnimation.doOnEnd {
                nav.isVisible = !hideBottomNav
                binding.bottomView?.visibility =
                    if (hideBottomNav) {
                        View.GONE
                    } else {
                        binding.bottomView?.visibility
                            ?: View.GONE
                    }
            }
            alphaAnimation.duration = 150
            animationSet?.playTogether(alphaAnimation)
            animationSet?.start()
        }
    }

    private fun updateControllersWithSideNavChanges(extraController: Controller? = null) {
        if (!isBindingInitialized || !this::router.isInitialized || this is SearchActivity) return
        binding.sideNav?.let { sideNav ->
            val controllers =
                (router.backstack.map { it?.controller } + extraController)
                    .filterNotNull()
                    .distinct()
            val navWidth = sideNav.width.takeIf { it != 0 } ?: 80.dpToPx
            controllers.forEach { controller ->
                val isRootController = controller is RootSearchInterface
                if (controller.view?.layoutParams !is ViewGroup.MarginLayoutParams) return@forEach
                controller.view?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart =
                        if (sideNav.isVisible) {
                            if (isRootController) 0 else -navWidth
                        } else {
                            if (isRootController) navWidth else 0
                        }
                }
                when (controller) {
                    // The library grid fits its columns to the width it has been given
                    is LibraryController -> controller.onSideNavWidthChanged()
                    // The recents submenu stands in for the tabs, but only while it is showing
                    is RecentsController -> if (controller.isControllerVisible) controller.setupTabs(false)
                    else -> Unit
                }
            }
        }
    }

    fun showTabBar(
        show: Boolean,
        animate: Boolean = true,
    ) {
        // A controller that does not implement TabbedInterface must never inherit another
        // controller's tabs. This fixes the modern Library tabs appearing on manga/detail pages.
        val activeController = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller else null
        val effectiveShow = show && (activeController as? TabbedInterface)?.showTabs() == true

        tabAnimation?.cancel()
        if (animate) {
            if (effectiveShow && !binding.tabsFrameLayout.isVisible) {
                binding.tabsFrameLayout.alpha = 0f
                binding.tabsFrameLayout.isVisible = true
            }
            val tA =
                ValueAnimator.ofFloat(
                    binding.tabsFrameLayout.alpha,
                    if (effectiveShow) 1f else 0f,
                )
            tA.addUpdateListener { valueAnimator ->
                binding.tabsFrameLayout.alpha = valueAnimator.animatedValue as Float
            }
            tA.doOnEnd {
                binding.tabsFrameLayout.isVisible = effectiveShow
                if (!effectiveShow) {
                    binding.mainTabs.clearOnTabSelectedListeners()
                    binding.mainTabs.removeAllTabs()
                }
            }
            tA.duration = 100
            tabAnimation = tA
            tA.start()
        } else {
            binding.tabsFrameLayout.alpha = effectiveShow.toInt().toFloat()
            binding.tabsFrameLayout.isVisible = effectiveShow
            if (!effectiveShow) {
                binding.mainTabs.clearOnTabSelectedListeners()
                binding.mainTabs.removeAllTabs()
            }
        }
    }

    private fun downloadStatusChanged(downloading: Boolean) {
        lifecycleScope.launchUI {
            val hasQueue = downloading || downloadManager.hasQueue()
            if (hasQueue) {
                nav.getOrCreateBadge(R.id.nav_recents)
                showDLQueueTutorial()
            } else {
                nav.removeBadge(R.id.nav_recents)
            }
        }
    }

    private fun whatsNewSheet() =
        MaterialMenuSheet(
            this,
            listOf(
                MaterialMenuSheet.MenuSheetItem(
                    0,
                    textRes = R.string.whats_new_this_release,
                    drawable = R.drawable.ic_new_releases_outline_24dp,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    1,
                    textRes = R.string.close,
                    drawable = R.drawable.ic_close_24dp,
                ),
            ),
            title = getString(R.string.updated_to_, BuildConfig.VERSION_NAME),
            showDivider = true,
            selectedId = 0,
            onMenuItemClicked = { _, item ->
                if (item == 0) {
                    try {
                        val intent =
                            Intent(
                                Intent.ACTION_VIEW,
                                RELEASE_URL.toUri(),
                            )
                        startActivity(intent)
                    } catch (e: Throwable) {
                        toast(e.message)
                    }
                }
                true
            },
        )

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private var startingX = 0f
        private var startingY = 0f

        override fun onDown(e: MotionEvent): Boolean {
            startingX = e.x
            startingY = e.y
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            var result = false
            val diffY = e2.y - startingY
            val diffX = e2.x - startingX
            if (abs(diffX) <= abs(diffY)) {
                val sheetRect = Rect()
                nav.getGlobalVisibleRect(sheetRect)
                if (sheetRect.contains(startingX.toInt(), startingY.toInt()) &&
                    abs(diffY) > Companion.SWIPE_THRESHOLD &&
                    abs(velocityY) > Companion.SWIPE_VELOCITY_THRESHOLD &&
                    diffY <= 0
                ) {
                    val bottomSheetController =
                        router.backstack.lastOrNull()?.controller as? BottomSheetController
                    bottomSheetController?.showSheet()
                } else if (nav == binding.sideNav &&
                    sheetRect.contains(startingX.toInt(), startingY.toInt()) &&
                    abs(diffY) > Companion.SWIPE_THRESHOLD &&
                    abs(velocityY) > Companion.SWIPE_VELOCITY_THRESHOLD &&
                    diffY > 0
                ) {
                    val bottomSheetController =
                        router.backstack.lastOrNull()?.controller as? BottomSheetController
                    bottomSheetController?.hideSheet()
                }
                result = true
            }
            return result
        }
    }

    companion object {
        private const val INKSHELF_HEADER_SOURCE_HUE = 275f

        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100

        // Matches AppCompat's unset ViewPropertyAnimator default (ValueAnimator.DURATION) used to fade mActionModeView
        private const val ACTION_MODE_FADE_DURATION = 300L

        const val MAIN_ACTIVITY = "eu.kanade.tachiyomi.ui.main.MainActivity"

        // Shortcut actions
        const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
        const val SHORTCUT_RECENTS = "eu.kanade.tachiyomi.SHOW_RECENTS"
        const val SHORTCUT_RECENTLY_UPDATED = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_RECENTLY_READ = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_BROWSE = "eu.kanade.tachiyomi.SHOW_BROWSE"
        const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"
        const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"
        const val SHORTCUT_MANGA_BACK = "eu.kanade.tachiyomi.SHOW_MANGA_BACK"
        const val SHORTCUT_UPDATE_NOTES = "eu.kanade.tachiyomi.SHOW_UPDATE_NOTES"
        const val SHORTCUT_SOURCE = "eu.kanade.tachiyomi.SHOW_SOURCE"
        const val SHORTCUT_READER_SETTINGS = "eu.kanade.tachiyomi.READER_SETTINGS"
        const val SHORTCUT_EXTENSIONS = "eu.kanade.tachiyomi.EXTENSIONS"
        const val EXTRA_UPDATE_ALL_EXTENSIONS = "eu.kanade.tachiyomi.UPDATE_ALL_EXTENSIONS"

        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"

        var chapterIdToExitTo = 0L
        var backVelocity = 0f
    }
}

/** Marker for root InkShelf screens that own their visible header instead of J2K's shared compact toolbar. */
interface InkShelfHeaderInterface

interface BottomNavBarInterface {
    fun canChangeTabs(block: () -> Unit): Boolean
}

interface RootSearchInterface {
    fun expandSearch() {
        if (this is Controller) {
            val mainActivity = activity as? MainActivity ?: return
            mainActivity.binding.searchToolbar.menu
                .findItem(R.id.action_search)
                ?.expandActionView()
        }
    }
}

interface TabbedInterface {
    fun showTabs(): Boolean = true
}

interface HingeSupportedController {
    fun updateForHinge()
}

interface SearchControllerInterface :
    FloatingSearchInterface,
    SmallToolbarInterface

interface FloatingSearchInterface {
    fun searchTitle(title: String?): String? {
        if (this is Controller) {
            return activity?.getString(R.string.search_, title)
        }
        return title
    }

    fun showFloatingBar() = true
}

interface BottomSheetController {
    fun showSheet()

    fun hideSheet()

    fun toggleSheet()
}
