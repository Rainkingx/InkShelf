package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
        val skin = InkShelf2Themes.eventSkin
        if (skin != null && size.width > 0f && size.height > 0f) {
            drawRect(Brush.verticalGradient(listOf(skin.metal.copy(alpha = 0.48f),
                InkShelf2Colors.Background, skin.metal.copy(alpha = 0.20f))))
            val cx = size.width * 0.82f
            val cy = size.width * 0.38f
            val r = size.width * 0.38f
            val rune = Path().apply {
                moveTo(cx, cy-r); lineTo(cx+r*0.86f, cy-r*0.5f)
                lineTo(cx+r*0.86f, cy+r*0.5f); lineTo(cx, cy+r)
                lineTo(cx-r*0.86f, cy+r*0.5f); lineTo(cx-r*0.86f, cy-r*0.5f); close()
                moveTo(cx, cy-r*0.63f); lineTo(cx+r*0.63f,cy)
                lineTo(cx,cy+r*0.63f); lineTo(cx-r*0.63f,cy); close()
                moveTo(cx-r*0.86f,cy-r*0.5f); lineTo(cx+r*0.86f,cy+r*0.5f)
                moveTo(cx+r*0.86f,cy-r*0.5f); lineTo(cx-r*0.86f,cy+r*0.5f)
                moveTo(cx,cy-r); lineTo(cx,cy+r)
            }
            drawPath(rune, skin.gold.copy(alpha = 0.09f), style = Stroke(1.2.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(skin.energy.copy(alpha = 0.11f), Color.Transparent),
                center = Offset(cx, cy), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(cx, cy))
            repeat(12) { i ->
                val y = size.height * i / 12f
                drawLine(skin.muted.copy(alpha = 0.018f), Offset(0f,y),
                    Offset(size.width,y+size.width*0.12f),1.dp.toPx())
            }
            return@drawBehind
        }
        drawCircle(
            brush = Brush.radialGradient(
                0f to InkShelf2Colors.Accent.copy(alpha = 0.22f),
                0.48f to InkShelf2Colors.AccentLight.copy(alpha = 0.11f),
                1f to Color.Transparent,
            ),
            center = Offset(size.width * 0.85f, size.height * 0.17f),
            radius = size.width * 1.2f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to InkShelf2Colors.Accent.copy(alpha = 0.10f),
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
    val skin = InkShelf2Themes.eventSkin
    val shape = if (skin != null) RoundedCornerShape(8.dp) else InkShelf2Metrics.ButtonCorner
    val background = when {
        skin != null && enabled && outlined -> Brush.verticalGradient(listOf(skin.metalLight, skin.metal, InkShelf2Colors.Background))
        skin != null && enabled -> Brush.verticalGradient(listOf(Color(0xFF9BF4BF), InkShelf2Colors.Accent, Color(0xFF249F63)))
        !enabled -> Brush.horizontalGradient(listOf(InkShelf2Colors.Surface, InkShelf2Colors.Surface))
        outlined -> Brush.horizontalGradient(listOf(InkShelf2Colors.Surface, InkShelf2Colors.SurfaceRaised))
        else -> InkShelf2Colors.AccentGradient
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
                    skin != null && outlined -> skin.gold
                    skin != null -> skin.metalLight
                    outlined -> InkShelf2Colors.AccentLight
                    else -> InkShelf2Colors.AccentLight.copy(alpha = 0.75f)
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
                outlined -> InkShelf2Colors.Text
                else -> Color(0xFF08060D)
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
    val skin = InkShelf2Themes.eventSkin
    if (skin != null) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 24.dp).background(skin.gold))
            Spacer(Modifier.width(8.dp))
            Text(text, color = skin.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(text = text, modifier = modifier, color = InkShelf2Colors.Text,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun InkShelf2SectionHeading(
    text: String,
    onClick: (() -> Unit)?,
    showSeeAll: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkShelf2Metrics.PageGutter)
            // In compact mode the heading remains clickable, as before. For
            // Discover-style headings, only the explicit See all action opens.
            .then(
                if (onClick != null && !showSeeAll) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            color = InkShelf2Themes.eventSkin?.gold ?: InkShelf2Colors.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            if (showSeeAll) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "See all",
                        color = InkShelf2Colors.AccentLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "See all " + text,
                        tint = InkShelf2Colors.AccentLight,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "See all " + text,
                    tint = InkShelf2Colors.Text,
                    modifier = Modifier.size(24.dp),
                )
            }
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

/** Recoverable failures share the same visual treatment across the shell. */
@Composable
internal fun InkShelf2RetryCard(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Try Again",
    retryIcon: ImageVector = Icons.Outlined.Refresh,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.fillMaxWidth().clip(shape)
            .background(InkShelf2Colors.Surface)
            .border(1.dp, InkShelf2Colors.Border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null,
                tint = InkShelf2Colors.AccentLight, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = InkShelf2Colors.Text, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold)
        }
        Text(message, color = InkShelf2Colors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        InkShelf2ActionButton(
            label = actionLabel,
            onClick = onRetry,
            outlined = true,
            leadingIcon = retryIcon,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Single-size, well-contrasted search field. Caller owns search behaviour. */
@Composable
internal fun InkShelf2SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "Filter recommended comics",
    onSearch: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = InkShelf2Colors.Text)
        },
        placeholder = { Text(placeholderText, fontSize = 13.sp) },
        trailingIcon = if (onSearch != null) {
            {
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search installed sources",
                        tint = InkShelf2Colors.AccentLight,
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (InkShelf2Themes.eventSkin != null) 8.dp else 24.dp),
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


/** Keep the normal loading indicator; add a rune to event-only loading states. */
@Composable
internal fun InkShelf2LoadingIndicator(modifier: Modifier = Modifier, color: Color = InkShelf2Colors.AccentLight, strokeWidth: Dp = 2.dp) {
    if (InkShelf2Themes.eventSkin == null) {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.matchParentSize(), color = color, strokeWidth = strokeWidth)
            Image(
                painter = painterResource(eu.kanade.tachiyomi.R.drawable.inkshelf_doom_rune),
                contentDescription = null,
                modifier = Modifier.matchParentSize().padding(4.dp),
            )
        }
    }
}
