package eu.kanade.tachiyomi.ui.inkshelf.shell

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Only mounted for actual in-flight loads; no persistent animation on
 * completed or failed images. Draw-phase state avoids recomposing each frame.
 */
@Composable
private fun inkShelf2SkeletonPaint(): Modifier {
    val surface = InkShelf2Colors.SurfaceRaised
    val accent = InkShelf2Colors.AccentLight
    val motion = android.os.Build.VERSION.SDK_INT < 26 ||
        android.animation.ValueAnimator.areAnimatorsEnabled()
    val pulse = if (motion) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0.035f,
            targetValue = 0.16f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 950),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    } else null
    return Modifier.drawBehind {
        drawRect(surface)
        drawRect(accent.copy(alpha = pulse?.value ?: 0.07f))
    }
}

@Composable
internal fun InkShelf2LoadingArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onError: (() -> Unit)? = null,
) {
    var loading by remember(model) { mutableStateOf(model != null) }
    // Preserve Coil's existing request, size, cache and error handling.
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.then(if (loading) inkShelf2SkeletonPaint() else Modifier),
        contentScale = contentScale,
        onLoading = { loading = true },
        onSuccess = { loading = false },
        onError = { loading = false; onError?.invoke() },
    )
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).then(inkShelf2SkeletonPaint())) {}
}

/** Non-interactive placeholders sized like the three-cover shelves. */
@Composable
internal fun InkShelf2SkeletonShelf(message: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(0.71f))
                    SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(10.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.48f).height(8.dp))
                }
            }
        }
        if (message != null) Text(message, color = InkShelf2Colors.Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun InkShelf2SkeletonIssues(grid: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (grid) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(0.71f))
                        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(12.dp))
                    }
                }
            }
        } else {
            repeat(3) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBlock(Modifier.size(54.dp, 76.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.74f).height(13.dp))
                        SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(9.dp))
                    }
                    SkeletonBlock(Modifier.size(28.dp))
                }
            }
        }
        Text("Loading issues from the installed comic source…", color = InkShelf2Colors.Muted, fontSize = 12.sp)
    }
}
