package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * InkShelf 2.0 shared visual language. Home is the approved reference.
 *
 * Screens keep their own compositions, but all main destinations use the same
 * ambient lighting, button sizing, text hierarchy, margins and control states.
 * This contains NO legacy J2K views, navigation, database logic or overlays.
 * The approved cover cards and bottom navigation intentionally remain unchanged.
 */
internal object InkShelf2Metrics {
    val PageGutter = 16.dp
    val SectionGap = 12.dp
    val ButtonHeight = 40.dp
    val ButtonCorner = RoundedCornerShape(50)
    val ContentRadius = 12.dp
}

/**
 * The two diffuse purple light pools from approved Home. Applied directly to
 * each new Compose destination's own scroll/container, not over old UI.
 */
internal fun Modifier.inkShelf2Atmosphere(): Modifier = this
    .background(InkShelf2Colors.Background)
    .drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFF70288C).copy(alpha = 0.22f),
                0.48f to Color(0xFF3F165A).copy(alpha = 0.11f),
                1f to Color.Transparent,
            ),
            center = Offset(size.width * 0.85f, size.height * 0.17f),
            radius = size.width * 1.2f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFF6F239C).copy(alpha = 0.10f),
                1f to Color.Transparent,
            ),
            center = Offset(size.width * 0.06f, size.height * 0.78f),
            radius = size.width * 1.15f,
        )
    }

/** Exact approved Home button styling, reused in the other destinations. */
@Composable
internal fun InkShelf2ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val shape = InkShelf2Metrics.ButtonCorner
    val background = when {
        !enabled -> Brush.horizontalGradient(listOf(InkShelf2Colors.Surface, InkShelf2Colors.Surface))
        outlined -> Brush.horizontalGradient(listOf(Color(0xD010081A), Color(0xD0140B20)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFB543EE), Color(0xFFE46EF6)))
    }
    Box(
        modifier = modifier
            .height(InkShelf2Metrics.ButtonHeight)
            .shadow(
                elevation = if (enabled && !outlined) 5.dp else 0.dp,
                shape = shape,
                ambientColor = InkShelf2Colors.Accent.copy(alpha = 0.35f),
                spotColor = InkShelf2Colors.AccentLight.copy(alpha = 0.38f),
            )
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = when {
                    !enabled -> InkShelf2Colors.Border
                    outlined -> InkShelf2Colors.AccentLight
                    else -> Color(0x88FFE5FF)
                },
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            val fg = when {
                !enabled -> InkShelf2Colors.Muted
                outlined -> Color.White
                else -> Color(0xFF130A1A)
            }
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = fg,
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun InkShelf2PageHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = InkShelf2Colors.Text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun InkShelf2SectionHeading(text: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkShelf2Metrics.PageGutter)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = InkShelf2Colors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (onClick != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "See all " + text,
                tint = InkShelf2Colors.Text,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun InkShelf2EmptyShelf(message: String) {
    Text(
        text = message,
        color = InkShelf2Colors.Muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = InkShelf2Metrics.PageGutter, vertical = 14.dp),
    )
}

/** Single-size, well-contrasted search field. Caller owns search behaviour. */
@Composable
internal fun InkShelf2SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = InkShelf2Colors.Text)
        },
        placeholder = { Text("Filter recommended comics", fontSize = 13.sp) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = InkShelf2Colors.Text,
            unfocusedTextColor = InkShelf2Colors.Text,
            focusedBorderColor = InkShelf2Colors.AccentLight,
            unfocusedBorderColor = InkShelf2Colors.Border,
            focusedContainerColor = InkShelf2Colors.Surface,
            unfocusedContainerColor = InkShelf2Colors.Surface,
            cursorColor = InkShelf2Colors.AccentLight,
        ),
    )
}

/** Static display chip until the associated real filter is implemented. */
@Composable
internal fun InkShelf2StatusPill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(InkShelf2Colors.Accent, InkShelf2Colors.AccentLight),
                ),
            )
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
