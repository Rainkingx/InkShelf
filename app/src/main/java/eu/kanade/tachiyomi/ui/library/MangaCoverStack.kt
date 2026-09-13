package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.MangaCoverStackItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlin.math.abs
import kotlin.math.roundToInt

/** How much each cover overlaps the one before it, as a fraction of the cover's width. */
internal const val STACK_OVERLAP_FRACTION = 0.25f

private const val LOOP_MIN_ITEMS = 4
private const val STATIC_STACK_START_PADDING_DP = 40

/**
 * Sets up this [MangaStackRecyclerView] to preview [mangas] - a cascading, snap-to-front, looping
 * carousel for enough items to scroll through, or a locked static row when there are too few to
 * ever need scrolling.
 */
fun MangaStackRecyclerView.setupMangaCoverStack(mangas: List<Manga>) {
    val looping = mangas.size > LOOP_MIN_ITEMS

    val layoutManager = PeekingLinearLayoutManager(context)
    this.layoutManager = layoutManager
    adapter = MangaCoverStackAdapter(mangas, looping)
    StartSnapHelper().attachToRecyclerView(this)

    if (looping) {
        val midpoint = Int.MAX_VALUE / 2
        // scrollToPosition alone doesn't guarantee it lands flush at the start edge.
        layoutManager.scrollToPositionWithOffset(midpoint - midpoint % mangas.size, 0)
    } else {
        // Nothing to scroll to with this few items - lock it and give it more breathing
        // room than the tighter padding meant to let the next card peek in while scrolling.
        isScrollingEnabled = false
        updatePaddingRelative(start = STATIC_STACK_START_PADDING_DP.dpToPx)
    }
}

/**
 * Lays out a couple of extra items beyond each edge of the viewport so covers are already
 * measured/positioned before they scroll into view, instead of popping in at full size the
 * moment they cross into the visible bounds. Kept modest, not a full extra screen, since
 * [MangaStackRecyclerView]'s overlap pull could otherwise yank a far-off item back into view.
 */
private class PeekingLinearLayoutManager(
    context: Context,
) : LinearLayoutManager(context, HORIZONTAL, false) {
    override fun calculateExtraLayoutSpace(
        state: RecyclerView.State,
        extraLayoutSpace: IntArray,
    ) {
        val extra = width / 2
        extraLayoutSpace[0] = extra
        extraLayoutSpace[1] = extra
    }
}

private class MangaCoverStackAdapter(
    private val mangas: List<Manga>,
    private val looping: Boolean,
) : RecyclerView.Adapter<MangaCoverStackAdapter.ViewHolder>() {
    override fun getItemCount() = if (looping) Int.MAX_VALUE else mangas.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val coverBinding = MangaCoverStackItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val layoutParams = coverBinding.root.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.marginStart = -(layoutParams.width * STACK_OVERLAP_FRACTION).roundToInt()
        return ViewHolder(coverBinding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.binding.coverImage.loadManga(mangas[position % mangas.size])
    }

    class ViewHolder(
        val binding: MangaCoverStackItemBinding,
    ) : RecyclerView.ViewHolder(binding.root)
}

/**
 * Snaps items to the start edge instead of [LinearSnapHelper]'s default of centering them.
 * [findSnapView] picks whichever child is closest to the start rather than
 * [LinearLayoutManager.findFirstVisibleItemPosition], which (with overlapping items) can return
 * an almost-fully-scrolled-past sliver - snapping that back to start caused a runaway
 * back-and-forth as each correction changed what counted as "first visible."
 */
private class StartSnapHelper : LinearSnapHelper() {
    override fun calculateDistanceToFinalSnap(
        layoutManager: RecyclerView.LayoutManager,
        targetView: View,
    ): IntArray {
        val out = IntArray(2)
        if (layoutManager.canScrollHorizontally()) {
            out[0] = targetView.left - layoutManager.paddingLeft
        }
        return out
    }

    override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
        if (layoutManager !is LinearLayoutManager) return super.findSnapView(layoutManager)
        var closest: View? = null
        var closestDistance = Int.MAX_VALUE
        for (i in 0 until layoutManager.childCount) {
            val child = layoutManager.getChildAt(i) ?: continue
            val distance = abs(child.left - layoutManager.paddingLeft)
            if (distance < closestDistance) {
                closestDistance = distance
                closest = child
            }
        }
        return closest
    }

    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int,
        velocityY: Int,
    ): Int {
        if (layoutManager !is LinearLayoutManager) return RecyclerView.NO_POSITION
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION
        return if (velocityX > 0) firstVisible + 1 else firstVisible
    }
}
