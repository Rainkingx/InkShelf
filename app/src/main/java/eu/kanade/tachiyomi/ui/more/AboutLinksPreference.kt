package eu.kanade.tachiyomi.ui.more

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.checkHeightThen

class AboutLinksPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        init {
            layoutResource = R.layout.pref_about_links
            isSelectable = false
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)

            (holder.itemView as LinearLayout).apply {
                checkHeightThen {
                    val childCount = (this.getChildAt(0) as ViewGroup).childCount
                    val childCount2 = (this.getChildAt(1) as ViewGroup).childCount
                    val fullCount = childCount + childCount2
                    orientation =
                        if (width >= (56 * fullCount).dpToPx) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                }
            }
            holder.findViewById(R.id.btn_website).apply {
                tooltipText = contentDescription
                setOnClickListener { context.openInBrowser("https://mihon.app") }
            }
            holder.findViewById(R.id.btn_discord).apply {
                tooltipText = contentDescription
                setOnClickListener { context.openInBrowser("https://discord.gg/mihon") }
            }
            holder.findViewById(R.id.btn_x).apply {
                tooltipText = contentDescription
                setOnClickListener { context.openInBrowser("https://x.com/mihonapp") }
            }
            holder.findViewById(R.id.btn_facebook).apply {
                tooltipText = contentDescription
                setOnClickListener { context.openInBrowser("https://facebook.com/mihonapp") }
            }
            holder.findViewById(R.id.btn_reddit).apply {
                tooltipText = contentDescription
                setOnClickListener { context.openInBrowser("https://www.reddit.com/r/mihonapp") }
            }
            holder.findViewById(R.id.btn_github).apply {
                tooltipText = contentDescription
                setOnClickListener { context.openInBrowser("https://github.com/Jays2Kings/tachiyomiJ2K") }
            }
            holder.findViewById(R.id.btn_mihon).apply {
                tooltipText = contentDescription
                setOnClickListener { context.openInBrowser("https://github.com/mihonapp") }
            }
        }
    }
