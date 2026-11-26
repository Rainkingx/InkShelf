package eu.kanade.tachiyomi.data.preference

import android.content.Context
import androidx.work.NetworkType
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal object InkShelfDataSaver {
    fun enabled(): Boolean = Injekt.get<PreferencesHelper>().inkShelfDataSaver().get()
    fun allowsAutomaticNetwork(context: Context): Boolean =
        !enabled() || context.isConnectedToWifi()
    fun networkType(): NetworkType =
        if (enabled()) NetworkType.UNMETERED else NetworkType.CONNECTED
}
