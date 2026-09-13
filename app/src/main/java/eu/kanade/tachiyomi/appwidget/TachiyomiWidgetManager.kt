package eu.kanade.tachiyomi.appwidget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.setWidgetPreviews
import timber.log.Timber

class TachiyomiWidgetManager {
    @SuppressLint("CheckResult")
    suspend fun Context.init() {
        val manager = GlanceAppWidgetManager(this)
        if (manager.getGlanceIds(UpdatesGridGlanceWidget::class.java).isNotEmpty()) {
            UpdatesGridGlanceWidget().loadData()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                manager.setWidgetPreviews<UpdatesGridGlanceReceiver>()
            } catch (e: IllegalArgumentException) {
                // Try/catch block as sometime this fails and crashes on certain devices
                Timber.e(e, "Failed to set widget previews")
            }
        }
    }
}
