package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.kanade.tachiyomi.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * New InkShelf 2.0 visual system. It is independent of MainActivity's
 * XML toolbar, Conductor app bar, bottom nav and existing themes.
 *
 * New destinations plug their content into [InkShelf2Shell]. The J2K
 * domain/data/reader services remain available through separate adapters.
 * Do not mount this above the old activity chrome as an overlay.
 */
object InkShelf2Colors {
    // Snapshot-backed getters mean every existing 2.0 Compose screen responds
    // to a theme change without a separate toolbar or duplicated old UI.
    val Background: Color get() = InkShelf2Themes.current.background
    val Surface: Color get() = InkShelf2Themes.current.surface
    val SurfaceRaised: Color get() = InkShelf2Themes.current.surfaceRaised
    val Accent: Color get() = InkShelf2Themes.current.accent
    val AccentLight: Color get() = InkShelf2Themes.current.highlight
    val Text: Color get() = InkShelf2Themes.eventSkin?.text ?: InkShelf2Themes.current.text
    val Muted: Color get() = InkShelf2Themes.eventSkin?.muted ?: InkShelf2Themes.current.muted
    val OnAccent: Color get() = InkShelf2Themes.current.onAccent
    val Trim: Color get() = InkShelf2Themes.eventSkin?.gold ?: AccentLight
    val PulseTitle: String get() = InkShelf2Themes.eventSkin?.pulseTitle ?: "The Pulse"
    val Border: Color get() = InkShelf2Themes.current.border
    val AccentGradient: Brush
        get() = Brush.horizontalGradient(listOf(Accent, AccentLight))
}

enum class InkShelf2Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    DISCOVER("Discover", Icons.Outlined.Search),
    LIBRARY("Library", Icons.Outlined.LibraryBooks),
    GUIDES("Events", Icons.Outlined.AutoStories),
    ACCOUNT("Menu", Icons.Outlined.Menu),
}

// Guides is a primary tab; Menu remains a header destination.
private val visibleInkShelf2Tabs = listOf(
    InkShelf2Tab.HOME,
    InkShelf2Tab.DISCOVER,
    InkShelf2Tab.LIBRARY,
    InkShelf2Tab.GUIDES,
)

/**
 * The single, offline InkShelf 2.0 font family. All UI text uses Oxanium to
 * echo the geometric, technical style of the approved uppercase wordmark.
 * Comic art, extension screens supplied by third parties and the original
 * J2K reader are intentionally not re-themed here.
 */
private val InkShelf2FontFamily = FontFamily(
    Font(R.font.oxanium_regular, weight = FontWeight.Normal),
    Font(R.font.oxanium_medium, weight = FontWeight.Medium),
    Font(R.font.oxanium_semibold, weight = FontWeight.SemiBold),
    Font(R.font.oxanium_bold, weight = FontWeight.Bold),
    Font(R.font.oxanium_extrabold, weight = FontWeight.ExtraBold),
)

private val InkShelf2AppTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = InkShelf2FontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = InkShelf2FontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = InkShelf2FontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = InkShelf2FontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = InkShelf2FontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = InkShelf2FontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = InkShelf2FontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = InkShelf2FontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = InkShelf2FontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = InkShelf2FontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = InkShelf2FontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = InkShelf2FontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = InkShelf2FontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = InkShelf2FontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = InkShelf2FontFamily),
    )
}

@Composable
fun InkShelf2Theme(content: @Composable () -> Unit) {
    // Rebuild Material 3's scheme from the active appearance on every change.
    // Reading InkShelf2Themes.current registers a Compose state dependency.
    val palette = InkShelf2Themes.current
    val uiMode = LocalConfiguration.current.uiMode
    val selected = InkShelf2Themes.selectedId
    LaunchedEffect(uiMode, selected) { InkShelf2Themes.updateSystem(uiMode) }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }
    val scheme = if (palette.isLight) lightColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            primaryContainer = palette.surfaceRaised, onPrimaryContainer = palette.text,
            secondary = palette.highlight, onSecondary = Color.White,
            secondaryContainer = palette.surfaceRaised, onSecondaryContainer = palette.text,
            tertiary = palette.highlight, onTertiary = Color.White,
            background = palette.background, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.surfaceRaised, onSurfaceVariant = palette.muted,
            outline = palette.border, surfaceTint = palette.accent,
        ) else darkColorScheme(
            primary = palette.accent,
            onPrimary = InkShelf2Colors.Text,
            secondary = palette.highlight,
            background = palette.background,
            onBackground = InkShelf2Colors.Text,
            surface = palette.surface,
            onSurface = InkShelf2Colors.Text,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = InkShelf2Colors.Muted,
            outline = palette.border,
        )
    MaterialTheme(
        colorScheme = scheme,
        typography = InkShelf2AppTypography,
        content = content,
    )
}

/**
 * Only this root owns the main-screen header and bottom navigation.
 *
 * Content must apply the provided [PaddingValues] to its scroll container;
 * those values include the real system insets and the measured bars, so no
 * hardcoded spacer or padding behind the bottom navigation is required.
 *
 * Comic Details and the reader can opt out of shared bars. Their immersive
 * variants must be owned by their own route, not duplicated by an activity.
 */
@Composable
fun InkShelf2Shell(
    currentTab: InkShelf2Tab,
    onTabSelected: (InkShelf2Tab) -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onPulse: () -> Unit,
    onGuides: () -> Unit,
    guidesActive: Boolean = false,
    pulseActive: Boolean = false,
    pulseHasNewStories: Boolean = false,
    notificationCount: Int = 0,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showBottomNav: Boolean = true,
    motionRoute: String = currentTab.name,
    content: @Composable (PaddingValues) -> Unit,
) {
    InkShelf2Theme {
        Scaffold(
            modifier = modifier,
            containerColor = InkShelf2Colors.Background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (showHeader) {
                    InkShelf2Header(
                        onSearch = onSearch,
                        onPulse = onPulse,
                        onMenu = { onTabSelected(InkShelf2Tab.ACCOUNT) },
                        menuActive = currentTab == InkShelf2Tab.ACCOUNT,
                        pulseActive = pulseActive,
                        pulseHasNewStories = pulseHasNewStories,
                        notificationCount = notificationCount,
                    )
                }
            },
            bottomBar = {
                if (showBottomNav) {
                    InkShelf2BottomNavigation(
                        currentTab = currentTab,
                        onTabSelected = onTabSelected,
                    )
                }
            },
            content = { padding ->
                InkShelf2PageMotion(motionRoute) { content(padding) }
            },
        )
    }
}

/**
 * Only animate the incoming page. Never keep two live route trees, duplicate
 * SaveableStateProviders or competing BackHandlers during a transition.
 * The first frame stays still so startup and the cinematic splash are unchanged.
 */
@Composable
private fun InkShelf2PageMotion(route: String, content: @Composable () -> Unit) {
    val motionAllowed = android.os.Build.VERSION.SDK_INT < 26 ||
        android.animation.ValueAnimator.areAnimatorsEnabled()
    var previousRoute by remember { mutableStateOf(route) }
    val progress = remember(route, motionAllowed) {
        Animatable(if (motionAllowed && previousRoute != route) 0f else 1f)
    }
    LaunchedEffect(route, motionAllowed) {
        previousRoute = route
        if (motionAllowed && progress.value < 1f) {
            progress.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    // Read animated values only in the graphics layer, not composition.
    // A modest offset and opacity keep touch targets and comic covers legible.
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val remaining = 1f - progress.value
                alpha = 1f - remaining * 0.22f
                translationY = 8.dp.toPx() * remaining
            },
        ) {
            content()
        }
    }
}

@Composable
private fun InkShelf2Header(
    onSearch: () -> Unit,
    onPulse: () -> Unit,
    onMenu: () -> Unit,
    menuActive: Boolean,
    pulseActive: Boolean,
    pulseHasNewStories: Boolean,
    notificationCount: Int,
) {
    val skin = InkShelf2Themes.eventSkin
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(if (skin != null)
                listOf(skin.metal, InkShelf2Colors.Background)
                else listOf(InkShelf2Colors.Background, InkShelf2Colors.Background)))
            .drawBehind {
                if (skin != null) drawLine(skin.gold.copy(alpha = 0.70f),
                    Offset(0f, size.height - 1.dp.toPx()),
                    Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
            }
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ARC",
            modifier = Modifier.weight(1f),
            color = InkShelf2Colors.Trim,
            fontFamily = InkShelf2FontFamily,
            fontSize = 31.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
        IconButton(
            onClick = onPulse,
            modifier = Modifier.size(40.dp).semantics {
                contentDescription = if (pulseHasNewStories) {
                    "Open The Pulse, new stories available"
                } else {
                    "Open The Pulse"
                }
            },
        ) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                // Distinct ECG zigzag: not a comics/library symbol.
                Canvas(modifier = Modifier.size(23.dp)) {
                    val shape = Path().apply {
                        moveTo(size.width * 0.04f, size.height * 0.55f)
                        lineTo(size.width * 0.26f, size.height * 0.55f)
                        lineTo(size.width * 0.39f, size.height * 0.32f)
                        lineTo(size.width * 0.50f, size.height * 0.76f)
                        lineTo(size.width * 0.62f, size.height * 0.16f)
                        lineTo(size.width * 0.75f, size.height * 0.55f)
                        lineTo(size.width * 0.96f, size.height * 0.55f)
                    }
                    drawPath(
                        path = shape,
                        color = if (pulseActive) InkShelf2Colors.AccentLight
                            else InkShelf2Colors.Text,
                        style = Stroke(
                            width = 2.1.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                if (pulseHasNewStories) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd)
                            .offset(x = (-1).dp, y = 1.dp)
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(InkShelf2Colors.AccentLight),
                    )
                }
            }
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search comics",
                tint = InkShelf2Colors.Text,
            )
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Menu, contentDescription = if (notificationCount > 0)
                    "Menu, $notificationCount extension updates" else "Menu",
                    tint = if (menuActive) InkShelf2Colors.AccentLight else InkShelf2Colors.Text)
                if (notificationCount > 0) Box(Modifier.align(Alignment.TopEnd)
                    .offset(x = (-1).dp, y = 1.dp).size(7.dp)
                    .clip(RoundedCornerShape(50)).background(InkShelf2Colors.AccentLight))
            }
        }
    }
}

/**
 * Mock-up faithful navigation: dark rounded tray, outlined inactive glyphs,
 * purple selected glyph and a concentrated soft-glow capsule rather than a
 * full-strength neon button. One navigation owner for every rebuilt page.
 */
@Composable
private fun InkShelf2BottomNavigation(
    currentTab: InkShelf2Tab,
    onTabSelected: (InkShelf2Tab) -> Unit,
) {
    val skin = InkShelf2Themes.eventSkin
    val barShape = if (skin != null) RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)
        else RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(
                Brush.verticalGradient(
                    if (skin != null) listOf(skin.metalLight, skin.metal, InkShelf2Colors.Background)
                    else listOf(InkShelf2Colors.SurfaceRaised, InkShelf2Colors.Background),
                ),
            ),
    ) {
        HorizontalDivider(thickness = 1.dp, color = if (skin != null) skin.gold else InkShelf2Colors.Border.copy(alpha = 0.7f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp)
                .padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleInkShelf2Tabs.forEach { tab ->
                val selected = tab == currentTab
                val selectedShape = RoundedCornerShape(if (skin != null) 8.dp else 25.dp)
                val selectedBrush = Brush.verticalGradient(
                    if (skin != null) listOf(skin.metalLight, skin.metal, skin.energy.copy(alpha = 0.18f))
                    else listOf(
                        InkShelf2Colors.Accent.copy(alpha = 0.36f),
                        InkShelf2Colors.SurfaceRaised.copy(alpha = 0.95f),
                        InkShelf2Colors.Surface.copy(alpha = 0.92f),
                    ),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(
                            brush = if (selected) selectedBrush
                            else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                            shape = selectedShape,
                        )
                        .then(if (selected && skin != null) Modifier.border(1.dp, skin.gold.copy(alpha = 0.72f), selectedShape) else Modifier)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            // Home can be reselected to exit an open Home subpage
                            // (for example The Pulse), while other tabs keep
                            // their existing selected-tab behavior.
                            onClick = {
                                if (!selected || tab == InkShelf2Tab.HOME) {
                                    onTabSelected(tab)
                                }
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val image = when (tab) {
                        InkShelf2Tab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                        InkShelf2Tab.LIBRARY -> if (selected) Icons.Filled.LibraryBooks else Icons.Outlined.LibraryBooks
                        InkShelf2Tab.ACCOUNT -> if (selected) Icons.Filled.Menu else Icons.Outlined.Menu
                        InkShelf2Tab.DISCOVER -> Icons.Outlined.Search
                        InkShelf2Tab.GUIDES -> Icons.Outlined.AutoStories
                    }
                    Icon(
                        imageVector = image,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = if (selected) InkShelf2Colors.AccentLight else InkShelf2Colors.Text,
                    )
                    Text(
                        text = tab.title,
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = InkShelf2Colors.Text,
                    )
                }
            }
        }
    }
}
