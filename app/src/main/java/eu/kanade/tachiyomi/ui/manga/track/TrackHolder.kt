package eu.kanade.tachiyomi.ui.manga.track

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.TextViewCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.TrackItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import uy.kohesive.injekt.injectLazy
import java.text.DateFormat

class TrackHolder(
    view: View,
    adapter: TrackAdapter,
) : BaseViewHolder(view) {
    private val preferences: PreferencesHelper by injectLazy()
    private val binding = TrackItemBinding.bind(view)
    private val listener = adapter.rowClickListener
    private val dateFormat: DateFormat by lazy {
        preferences.dateFormat()
    }

    init {
        binding.logoContainer.setOnClickListener { listener.onLogoClick(bindingAdapterPosition) }
        binding.addTracking.setOnClickListener { listener.onTitleClick(bindingAdapterPosition) }
        binding.addTrackingPrivate.setOnClickListener { listener.onAddPrivatelyClick(bindingAdapterPosition) }
        binding.trackTitle.setOnClickListener { listener.onTitleClick(bindingAdapterPosition) }
        binding.trackTitle.setOnLongClickListener {
            listener.onTitleLongClick(bindingAdapterPosition)
            true
        }
        binding.trackStatus.setOnClickListener { listener.onStatusClick(bindingAdapterPosition) }
        binding.trackChapters.setOnClickListener { listener.onChaptersClick(bindingAdapterPosition) }
        binding.scoreContainer.setOnClickListener { listener.onScoreClick(bindingAdapterPosition) }
        binding.trackStartDate.setOnClickListener { listener.onStartDateClick(it, bindingAdapterPosition) }
        binding.trackFinishDate.setOnClickListener { listener.onFinishDateClick(it, bindingAdapterPosition) }
    }

    private fun setupOverflowMenu(
        item: TrackItem,
        track: Track,
    ) {
        val popup = PopupMenu(binding.trackOverflow.context, binding.trackOverflow)
        popup.menu.add(0, 0, 0, R.string.open_in_browser)
        popup.menu.add(0, 1, 1, R.string.copy_link)
        if (item.service.supportsPrivateTracking) {
            popup.menu.add(0, 2, 2, if (track.private) R.string.track_publicly else R.string.track_privately)
        }
        popup.menu.add(0, 3, 3, R.string.remove)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                0 -> listener.onOpenInBrowserClick(bindingAdapterPosition)
                1 -> listener.onCopyLinkClick(bindingAdapterPosition)
                2 -> listener.onTogglePrivateClick(bindingAdapterPosition)
                3 -> listener.onRemoveClick(bindingAdapterPosition)
            }
            true
        }
        binding.trackOverflow.setOnTouchListener(popup.dragToOpenListener)
        binding.trackOverflow.setOnClickListener { popup.show() }
    }

    @SuppressLint("SetTextI18n")
    fun bind(item: TrackItem) {
        val track = item.track
        binding.trackLogo.setImageResource(item.service.getLogo())
        val bgColor = ColorUtils.setAlphaComponent(item.service.getLogoColor(), 255)
        binding.logoContainer.setBackgroundColor(bgColor)
        binding.logoContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToBottom = if (track != null) binding.divider.id else binding.trackDetails.id
        }
        val serviceName = binding.trackLogo.context.getString(item.service.nameRes())
        binding.trackLogo.contentDescription = serviceName
        binding.trackGroup.isVisible = track != null
        binding.addTracking.isVisible = track == null
        binding.addTrackingPrivateDivider.isVisible = track == null && item.service.supportsPrivateTracking
        binding.addTrackingPrivate.isVisible = track == null && item.service.supportsPrivateTracking
        if (track != null) {
            binding.trackTitle.text = track.title
            binding.trackPrivateBadge.isVisible = item.service.supportsPrivateTracking && track.private
            binding.trackTitle.updatePaddingRelative(if (binding.trackPrivateBadge.isVisible) 24.dpToPx else 16.dpToPx)
            setupOverflowMenu(item, track)
            with(binding.trackChapters) {
                text =
                    when {
                        track.total_chapters > 0 && track.last_chapter_read.toInt() == track.total_chapters ->
                            context.getString(
                                R.string.all_chapters_read,
                            )
                        track.total_chapters > 0 ->
                            context.getString(
                                R.string.chapter_x_of_y,
                                track.last_chapter_read.toInt(),
                                track.total_chapters,
                            )
                        track.last_chapter_read > 0 ->
                            context.getString(
                                R.string.chapter_,
                                track.last_chapter_read.toInt().toString(),
                            )
                        else -> context.getString(R.string.not_started)
                    }
                setTextColor(enabledTextColor(true))
            }
            val status = item.service.getStatus(track.status)
            with(binding.trackStatus) {
                if (status.isEmpty()) {
                    setText(R.string.unknown_status)
                } else {
                    text = item.service.getStatus(track.status)
                }
                setTextColor(enabledTextColor(status.isNotEmpty()))
            }
            val supportsScoring = item.service.getScoreList().isNotEmpty()
            if (supportsScoring) {
                with(binding.trackScore) {
                    text =
                        if (track.score == 0f) {
                            binding.trackScore.context.getString(R.string.score)
                        } else {
                            item.service.displayScore(track)
                        }
                    setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        0,
                        starIcon(track),
                        0,
                    )
                    setTextColor(enabledTextColor(track.score != 0f))
                    TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(enabledTextColor(track.score != 0f)))
                }
            }
            binding.scoreContainer.isVisible = supportsScoring
            binding.vertDivider2.isVisible = supportsScoring

            binding.dateGroup.isVisible = item.service.supportsReadingDates
            if (item.service.supportsReadingDates) {
                with(binding.trackStartDate) {
                    text =
                        if (track.started_reading_date != 0L) {
                            dateFormat.format(track.started_reading_date)
                        } else {
                            context.getString(R.string.started_reading_date)
                        }
                    setTextColor(enabledTextColor(track.started_reading_date != 0L))
                }
                with(binding.trackFinishDate) {
                    text =
                        if (track.finished_reading_date != 0L) {
                            dateFormat.format(track.finished_reading_date)
                        } else {
                            context.getString(R.string.finished_reading_date)
                        }
                    setTextColor(enabledTextColor(track.finished_reading_date != 0L))
                }
            }
        }
    }

    fun enabledTextColor(enabled: Boolean): Int =
        binding.root.context.getResourceColor(
            if (enabled) {
                android.R.attr.textColorPrimary
            } else {
                android.R.attr.textColorHint
            },
        )

    private fun starIcon(track: Track): Int =
        if (track.score == 0f ||
            binding.trackScore.text
                .toString()
                .toFloatOrNull() != null
        ) {
            R.drawable.ic_star_12dp
        } else {
            0
        }

    fun setProgress(enabled: Boolean) {
        binding.progress.isVisible = enabled
        binding.trackLogo.isVisible = !enabled
    }
}
