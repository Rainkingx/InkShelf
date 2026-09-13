package eu.kanade.tachiyomi.ui.reader.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderGeneralLayoutBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.widget.BaseReaderSettingsView
import kotlin.collections.toMutableSet
import kotlin.math.roundToInt

class ReaderGeneralView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : BaseReaderSettingsView<ReaderGeneralLayoutBinding>(context, attrs) {
        lateinit var sheet: TabbedReaderSettingsSheet

        override fun inflateBinding() = ReaderGeneralLayoutBinding.bind(this)

        override fun initGeneralPreferences() {
            binding.viewerSeries.onItemSelectedListener = { position ->
                val readingModeType = ReadingModeType.fromSpinner(position)
                (context as ReaderActivity).viewModel.setMangaReadingMode(readingModeType.flagValue)

                val mangaViewer = activity.viewModel.getMangaReadingMode()
                if (mangaViewer == ReadingModeType.WEBTOON.flagValue || mangaViewer == ReadingModeType.CONTINUOUS_VERTICAL.flagValue) {
                    initWebtoonPreferences()
                } else {
                    initPagerPreferences()
                }
                val selectedModes = preferences.readerVerticalSeekbarModes().get()
                binding.verticalSeekbarExtraSettings.isVisible = currentModeSelected(selectedModes)
            }
            binding.viewerSeries.setSelection(
                (context as? ReaderActivity)?.viewModel?.state?.value?.manga?.readingModeType?.let {
                    ReadingModeType.fromPreference(it).prefValue
                } ?: 0,
            )
            binding.rotationMode.onItemSelectedListener = { position ->
                val rotationType = OrientationType.fromSpinner(position)
                (context as ReaderActivity).viewModel.setMangaOrientationType(rotationType.flagValue)
            }
            binding.rotationMode.setSelection(
                (context as ReaderActivity).viewModel.manga?.orientationType?.let {
                    OrientationType.fromPreference(it).prefValue
                } ?: 0,
            )

            binding.backgroundColor.setEntries(
                ReaderBackgroundColor.entries
                    .map { context.getString(it.stringRes) },
            )
            val selection = ReaderBackgroundColor.indexFromPref(preferences.readerTheme().get())
            binding.backgroundColor.setSelection(selection)
            binding.backgroundColor.onItemSelectedListener = { position ->
                val backgroundColor = ReaderBackgroundColor.entries[position]
                preferences.readerTheme().set(backgroundColor.prefValue)
            }
            binding.showPageNumber.bindToPreference(preferences.showPageNumber())
            binding.fullscreen.bindToPreference(preferences.fullscreen())
            binding.keepscreen.bindToPreference(preferences.keepScreenOn())
            binding.alwaysShowChapterTransition.bindToPreference(preferences.alwaysShowChapterTransition())

            initVerticalSeekbarPreferences()
        }

        fun currentModeSelected(modes: Set<String>): Boolean {
            val activity = context as? ReaderActivity ?: return true
            val currentMode = ReadingModeType.fromPreference(activity.viewModel.getMangaReadingMode())
            return currentMode.prefValue.toString() in modes
        }

        private fun initVerticalSeekbarPreferences() {
            val modesPref = preferences.readerVerticalSeekbarModes()
            val selectedModes = modesPref.get()

            ReadingModeType.entries.filter { it != ReadingModeType.DEFAULT }.forEach { mode ->
                val chip =
                    LayoutInflater.from(context).inflate(
                        R.layout.filter_button,
                        binding.verticalSeekbarModes,
                        false,
                    ) as MaterialButton
                chip.id = generateViewId()
                chip.setText(mode.stringRes)
                chip.isChecked = mode.prefValue.toString() in selectedModes
                chip.addOnCheckedChangeListener { _, isChecked ->
                    val current = modesPref.get().toMutableSet()
                    val key = mode.prefValue.toString()
                    if (isChecked) current.add(key) else current.remove(key)
                    modesPref.set(current)
                    binding.verticalSeekbarExtraSettings.isVisible = currentModeSelected(current)
                }
                binding.verticalSeekbarModes.addView(chip)
            }
            binding.verticalSeekbarExtraSettings.isVisible = currentModeSelected(selectedModes)

            val heightPref = preferences.readerVerticalSeekbarHeightPercent()
            binding.verticalSeekbarHeight.value = heightPref.get().toFloat()
            binding.verticalSeekbarHeight.setLabelFormatter { value -> "${value.roundToInt()}%" }
            updateVerticalSeekbarHeightText(heightPref.get())
            binding.verticalSeekbarHeight.addOnChangeListener { _, value, fromUser ->
                updateVerticalSeekbarHeightText(value.roundToInt())
                if (fromUser) heightPref.set(value.roundToInt())
            }

            val dockLeftPref = preferences.readerVerticalSeekbarDockLeft()
            binding.verticalSeekbarPlacement.check(
                if (dockLeftPref.get()) binding.placeLeftButton.id else binding.placeRightButton.id,
            )
            binding.verticalSeekbarPlacement.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                dockLeftPref.set(checkedId == binding.placeLeftButton.id)
            }
        }

        private fun updateVerticalSeekbarHeightText(percent: Int) {
            binding.verticalSeekbarHeightText.text =
                context
                    .getString(R.string.vertical_seekbar_height)
                    .withSubtitle(context, "$percent%")
        }

        /**
         * Init the preferences for the webtoon reader.
         */
        private fun initWebtoonPreferences() {
            sheet.updateTabs(true)
        }

        private fun initPagerPreferences() {
            sheet.updateTabs(false)
        }
    }
