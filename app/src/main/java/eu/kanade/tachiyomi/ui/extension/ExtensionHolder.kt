package eu.kanade.tachiyomi.ui.extension

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import coil.dispose
import coil.load
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import eu.kanade.tachiyomi.databinding.ExtensionCardItemBinding
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.view.applyStyle
import eu.kanade.tachiyomi.util.view.applyStyleFromAttr
import eu.kanade.tachiyomi.util.view.makeContainerShape
import java.util.Locale

class ExtensionHolder(
    view: View,
    val adapter: ExtensionAdapter,
) : BaseFlexibleViewHolder(view, adapter) {
    private val binding = ExtensionCardItemBinding.bind(view)

    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(flexibleAdapterPosition)
        }
        binding.cancelButton.setOnClickListener {
            adapter.buttonClickListener.onCancelClick(flexibleAdapterPosition)
        }
        binding.webviewButton.setOnClickListener {
            adapter.buttonClickListener.onWebViewClick(flexibleAdapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension

        // Set source name

        val infoText = mutableListOf(extension.versionName)
        binding.date.isVisible = false
        binding.extDivider.isVisible = extension is Extension.Installed && extension.hasUpdate
        if (extension is Extension.Installed && !extension.hasUpdate) {
            when (InstalledExtensionsOrder.fromValue(adapter.installedSortOrder)) {
                InstalledExtensionsOrder.RecentlyUpdated -> {
                    ExtensionLoader
                        .extensionUpdateDate(itemView.context, extension)
                        .takeUnless { it == 0L }
                        ?.let {
                            binding.date.isVisible = true
                            binding.date.text = itemView.context.timeSpanFromNow(R.string.updated_, it)
                            infoText.add("")
                        }
                }
                InstalledExtensionsOrder.RecentlyInstalled -> {
                    ExtensionLoader
                        .extensionInstallDate(itemView.context, extension)
                        .takeUnless { it == 0L }
                        ?.let {
                            binding.date.isVisible = true
                            binding.date.text =
                                itemView.context.timeSpanFromNow(
                                    if (extension.isShared) {
                                        R.string.installed_
                                    } else {
                                        R.string.added_
                                    },
                                    it,
                                )
                            infoText.add("")
                        }
                }
                else -> binding.date.isVisible = false
            }
        } else {
            binding.date.isVisible = false
        }
        binding.lang.isVisible = binding.date.isGone && extension !is Extension.Untrusted
        binding.extTitle.text =
            if (infoText.size > 1) {
                buildSpannedString {
                    append(extension.name + " ")
                    color(binding.extTitle.context.getResourceColor(android.R.attr.textColorSecondary)) {
                        scale(0.75f) {
                            append(LocaleHelper.getDisplayName(extension.lang))
                        }
                    }
                }
            } else {
                extension.name
            }

        binding.version.text = infoText.joinToString(" • ")
        binding.lang.text = LocaleHelper.getDisplayName(extension.lang)
        binding.warning.text =
            when {
                (extension as? Extension.Installed)?.isObsolete == true ->
                    itemView.context.getString(R.string.orphaned)
                extension.isNsfw -> itemView.context.getString(R.string.nsfw_short)
                else -> ""
            }.uppercase(Locale.ROOT)
        binding.installProgress.progress = item.sessionProgress ?: 0
        binding.installProgress.isVisible = item.sessionProgress != null
        binding.cancelButton.isVisible = item.sessionProgress != null
        binding.webviewButton.isVisible =
            extension is Extension.Available &&
            extension.sources.any { it.baseUrl.isNotBlank() }

        binding.sourceImage.dispose()

        binding.sourceImage.imageTintList = null
        when (extension) {
            is Extension.Available ->
                binding.sourceImage.load(extension.iconUrl) {
                    target(CoverViewTarget(binding.sourceImage))
                }
            is Extension.Installed -> binding.sourceImage.load(extension.icon)
            is Extension.Untrusted -> {
                binding.sourceImage.imageTintList =
                    ColorStateList.valueOf(itemView.context.getResourceColor(R.attr.colorError))
                binding.sourceImage.setImageResource(R.drawable.ic_app_untrusted_24dp)
            }
        }
        bindButton(item)
    }

    @Suppress("ResourceType")
    fun bindButton(item: ExtensionItem) =
        with(binding.extButton) {
            if (item.installStep == InstallStep.Done) return@with
            val installStep = item.installStep
            val extension = item.extension
            isEnabled = true
            isClickable = true

            binding.installProgress.progress = item.sessionProgress ?: 0
            binding.cancelButton.isVisible = item.sessionProgress != null
            binding.installProgress.isVisible = item.sessionProgress != null

            binding.webviewButton.isVisible =
                extension is Extension.Available &&
                extension.sources.any { it.baseUrl.isNotBlank() } &&
                item.sessionProgress == null
            if (installStep != null) {
                applyStyle(R.style.Widget_Tachiyomi_Button_TextButton)
                setText(
                    when (installStep) {
                        InstallStep.Pending -> R.string.pending
                        InstallStep.Downloading -> R.string.downloading
                        InstallStep.Loading -> R.string.loading
                        InstallStep.Installing -> R.string.installing
                        InstallStep.Installed -> R.string.installed
                        InstallStep.Error -> R.string.retry
                        InstallStep.Done -> return@with
                    },
                )
                if (installStep != InstallStep.Error) {
                    isEnabled = false
                    isClickable = false
                }
            } else if (extension is Extension.Installed) {
                when {
                    extension.hasUpdate -> {
                        applyStyleFromAttr(R.attr.materialButtonStyle)
                        setText(R.string.update)
                    }
                    else -> {
                        applyStyle(R.style.Widget_Tachiyomi_Button_TextButton)
                        setText(R.string.settings)
                    }
                }
            } else if (extension is Extension.Untrusted) {
                applyStyleFromAttr(R.attr.materialButtonOutlinedStyle)
                setText(R.string.trust)
            } else {
                applyStyleFromAttr(R.attr.materialButtonOutlinedStyle)
                setText(if (adapter.installPrivately) R.string.add else R.string.install)
            }
            updateLayoutParams { width = ViewGroup.LayoutParams.WRAP_CONTENT }
        }

    fun setCorners(
        top: Boolean,
        bottom: Boolean,
    ) {
        binding.extCard.shapeAppearanceModel = binding.extCard.makeContainerShape(top, bottom)
    }
}
