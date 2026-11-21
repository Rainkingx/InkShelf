package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
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
    ACCOUNT("Menu", Icons.Outlined.Menu),
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
        InkShelf2BookMark()
        Text(
            text = "InkShelf",
            modifier = Modifier.padding(start = 7.dp).weight(1f),
            color = InkShelf2Colors.Text,
            fontSize = 24.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
        )
        IconButton(onClick = onNotifications) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = "Notifications",
                tint = InkShelf2Colors.Text,
            )
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

/** Original vector book mark, rather than a bitmap borrowed from the old app bar. */
@Composable
private fun InkShelf2BookMark() {
    Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val left = Path().apply {
            moveTo(w * 0.49f, h * 0.20f)
            cubicTo(w * 0.32f, h * 0.12f, w * 0.15f, h * 0.10f, w * 0.05f, h * 0.18f)
            lineTo(w * 0.05f, h * 0.82f)
            cubicTo(w * 0.23f, h * 0.75f, w * 0.39f, h * 0.77f, w * 0.49f, h * 0.86f)
            close()
        }
        val right = Path().apply {
            moveTo(w * 0.53f, h * 0.20f)
            cubicTo(w * 0.71f, h * 0.11f, w * 0.85f, h * 0.13f, w * 0.95f, h * 0.20f)
            lineTo(w * 0.95f, h * 0.83f)
            cubicTo(w * 0.77f, h * 0.76f, w * 0.64f, h * 0.78f, w * 0.53f, h * 0.86f)
            close()
        }
        drawPath(left, InkShelf2Colors.AccentLight)
        drawPath(right, InkShelf2Colors.Accent)
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
                            onClick = { if (!selected) onTabSelected(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val image = when (tab) {
                        InkShelf2Tab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                        InkShelf2Tab.LIBRARY -> if (selected) Icons.Filled.LibraryBooks else Icons.Outlined.LibraryBooks
                        InkShelf2Tab.ACCOUNT -> if (selected) Icons.Filled.Menu else Icons.Outlined.Menu
                        InkShelf2Tab.DISCOVER -> Icons.Outlined.Search
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
