package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
    val Text = Color(0xFFFBF8FF)
    val Muted = Color(0xFFBCB1C7)
    val Border: Color get() = InkShelf2Themes.current.border
    val AccentGradient: Brush
        get() = Brush.horizontalGradient(listOf(Accent, AccentLight))
}

enum class InkShelf2Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    DISCOVER("Discover", Icons.Outlined.Search),
    LIBRARY("Library", Icons.Outlined.LibraryBooks),
    GUIDES("Guides", Icons.Outlined.AutoStories),
    ACCOUNT("Menu", Icons.Outlined.Menu),
}

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
    MaterialTheme(
        colorScheme = darkColorScheme(
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
        ),
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
    notificationCount: Int = 0,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showBottomNav: Boolean = true,
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
                        onNotifications = onNotifications,
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
            content = content,
        )
    }
}

@Composable
private fun InkShelf2Header(
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    notificationCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkShelf2Colors.Background)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            // Load the PNG foreground instead of the adaptive-icon XML: painterResource
            // cannot inflate adaptive icons on Android 26+ (including Android 17).
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "InkShelf logo",
            modifier = Modifier.size(40.dp),
        )
        Row(
            modifier = Modifier.padding(start = 5.dp).weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "INK",
                color = InkShelf2Colors.Text,
                fontFamily = InkShelf2FontFamily,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                maxLines = 1,
            )
            Text(
                text = "SHELF",
                color = InkShelf2Colors.AccentLight,
                fontFamily = InkShelf2FontFamily,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                maxLines = 1,
            )
        }
        IconButton(onClick = onNotifications) {
            Box(modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = if (notificationCount > 0) {
                        "Notifications, " + notificationCount + " extension updates"
                    } else {
                        "Notifications"
                    },
                    modifier = Modifier.align(Alignment.Center),
                    tint = InkShelf2Colors.Text,
                )
                if (notificationCount > 0) {
                    Text(
                        text = if (notificationCount > 99) "99+" else notificationCount.toString(),
                        modifier = Modifier.align(Alignment.TopEnd)
                            .offset(x = 9.dp, y = (-6).dp)
                            .clip(RoundedCornerShape(50))
                            .background(InkShelf2Colors.Accent)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search comics",
                tint = InkShelf2Colors.Text,
            )
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
    val barShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(
                Brush.verticalGradient(
                    listOf(InkShelf2Colors.SurfaceRaised, InkShelf2Colors.Background),
                ),
            ),
    ) {
        HorizontalDivider(thickness = 1.dp, color = InkShelf2Colors.Border.copy(alpha = 0.7f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp)
                .padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkShelf2Tab.entries.forEach { tab ->
                val selected = tab == currentTab
                val selectedBrush = Brush.verticalGradient(
                    listOf(
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
                            shape = RoundedCornerShape(25.dp),
                        )
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
