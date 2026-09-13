package eu.kanade.tachiyomi.ui.source

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.dispose
import coil.request.CachePolicy
import coil.request.ImageRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.databinding.DiscoverRecommendationItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import java.util.Locale

data class DiscoverRecommendation(
    val manga: Manga,
    val reason: String,
)

class DiscoverRecommendationAdapter(
    private val onClick: (Manga) -> Unit,
    private val mysteryMode: Boolean = false,
) : RecyclerView.Adapter<DiscoverRecommendationAdapter.Holder>() {

    private val items = mutableListOf<DiscoverRecommendation>()

    fun submitList(newItems: List<DiscoverRecommendation>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val binding =
            DiscoverRecommendationItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )

        // Picked for You now uses a 3-column GridLayoutManager. Let each mystery
        // card fill its grid span, while normal cards keep the XML's 136dp width
        // for Recently Updated's horizontal carousel.
        if (mysteryMode) {
            binding.root.layoutParams =
                binding.root.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                }
        }

        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.bind(items[position], position)
    }

    inner class Holder(
        private val binding: DiscoverRecommendationItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(items[position].manga)
                }
            }
        }

        fun bind(
            item: DiscoverRecommendation,
            position: Int,
        ) {
            val manga = item.manga
            val context = binding.root.context

            binding.itemImage.dispose()

            if (mysteryMode) {
                bindMystery(item, position)
                return
            }

            // Normal card mode - used by Recently Updated.
            binding.mysteryCard.visibility = View.GONE
            binding.itemImage.visibility = View.VISIBLE
            binding.itemImage.alpha = 1f
            binding.title.text = manga.title
            binding.reason.text = item.reason
            binding.title.setTextColor(context.getResourceColor(R.attr.colorOnBackground))
            binding.reason.setTextColor(context.getResourceColor(R.attr.colorOnSurfaceVariant))

            if (!manga.thumbnail_url.isNullOrEmpty()) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(manga)
                        .placeholder(android.R.color.transparent)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .target(
                            CoverViewTarget(
                                binding.itemImage,
                                binding.progress,
                            ),
                        ).setParameter(
                            MangaCoverFetcher.useCustomCover,
                            false,
                        ).crossfade(false)
                        .build()

                Coil.imageLoader(context).enqueue(request)
            } else {
                binding.progress.visibility = View.GONE
            }
        }

        private fun bindMystery(
            item: DiscoverRecommendation,
            position: Int,
        ) {
            val context = binding.root.context
            val manga = item.manga

            binding.itemImage.dispose()
            binding.itemImage.setImageDrawable(null)
            binding.itemImage.visibility = View.INVISIBLE
            binding.progress.visibility = View.GONE
            binding.mysteryCard.visibility = View.VISIBLE

            // Three-card Discover teaser: Purple - Gold - Purple.
            val goldPack = position == 1
            val accent =
                if (goldPack) {
                    Color.parseColor("#D8B35A")
                } else {
                    context.getResourceColor(R.attr.colorPrimary)
                }

            binding.mysteryCard.strokeColor = accent
            binding.mysteryQuestion.setTextColor(accent)
            binding.mysteryPackLabel.setTextColor(accent)

            // Keep the middle card gold, but all three use the same pack label.
            binding.mysteryPackLabel.text = "MYSTERY PACK"

            binding.title.text = "MYSTERY PICK"
            binding.title.setTextColor(accent)
            binding.title.gravity = Gravity.CENTER_HORIZONTAL
            binding.title.textAlignment = View.TEXT_ALIGNMENT_CENTER

            val genre =
                manga
                    .getGenres()
                    .orEmpty()
                    .map { it.trim() }
                    .firstOrNull {
                        it.isNotBlank() &&
                            it.lowercase(Locale.ROOT) !in
                            setOf("comic", "comics", "manga")
                    }

            binding.reason.text =
                genre
                    ?: item.reason
                        .substringBefore(" • ")
                        .trim()
                        .ifBlank { "Surprise pick" }

            binding.reason.setTextColor(
                context.getResourceColor(R.attr.colorOnSurfaceVariant),
            )
            binding.reason.gravity = Gravity.CENTER_HORIZONTAL
            binding.reason.textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
    }
}
