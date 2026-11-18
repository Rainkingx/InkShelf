package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
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
    val Background = Color(0xFF08060D)
    val Surface = Color(0xFF150E21)
    val SurfaceRaised = Color(0xFF21132D)
    val Accent = Color(0xFFB72AF2)
    val AccentLight = Color(0xFFE169F7)
    val Text = Color(0xFFFBF8FF)
    val Muted = Color(0xFFBCB1C7)
    val Border = Color(0xFF744686)
    val AccentGradient: Brush
        get() = Brush.horizontalGradient(listOf(Color(0xFF8734D8), Color(0xFFE24AF3)))
}

enum class InkShelf2Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    DISCOVER("Discover", Icons.Outlined.Explore),
    LIBRARY("Library", Icons.Outlined.CollectionsBookmark),
    ACCOUNT("Account", Icons.Outlined.PersonOutline),
}

private val inkShelf2Scheme = darkColorScheme(
    primary = InkShelf2Colors.Accent,
    onPrimary = InkShelf2Colors.Text,
    secondary = InkShelf2Colors.AccentLight,
    background = InkShelf2Colors.Background,
    onBackground = InkShelf2Colors.Text,
    surface = InkShelf2Colors.Surface,
    onSurface = InkShelf2Colors.Text,
    surfaceVariant = InkShelf2Colors.SurfaceRaised,
    onSurfaceVariant = InkShelf2Colors.Muted,
    outline = InkShelf2Colors.Border,
)

@Composable
fun InkShelf2Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = inkShelf2Scheme, content = content)
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
            .height(70.dp)
            .padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkShelf2BookMark()
        Text(
            text = "InkShelf",
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            color = InkShelf2Colors.Text,
            fontSize = 26.sp,
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

@Composable
private fun InkShelf2BottomNavigation(
    currentTab: InkShelf2Tab,
    onTabSelected: (InkShelf2Tab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(InkShelf2Colors.SurfaceRaised, InkShelf2Colors.Background),
                ),
            ),
    ) {
        HorizontalDivider(thickness = 1.dp, color = InkShelf2Colors.Border.copy(alpha = 0.6f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(76.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkShelf2Tab.entries.forEach { tab ->
                val selected = currentTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .background(
                            brush = if (selected) InkShelf2Colors.AccentGradient
                            else Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Transparent),
                            ),
                            shape = RoundedCornerShape(26.dp),
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { if (!selected) onTabSelected(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(23.dp),
                        tint = InkShelf2Colors.Text,
                    )
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = InkShelf2Colors.Text,
                    )
                }
            }
        }
    }
}
