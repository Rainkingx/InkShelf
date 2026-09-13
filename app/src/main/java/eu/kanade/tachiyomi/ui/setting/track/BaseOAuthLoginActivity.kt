package eu.kanade.tachiyomi.ui.setting.track

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.ui.base.activity.BaseThemedActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.launchIO
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

abstract class BaseOAuthLoginActivity : BaseThemedActivity() {
    internal val trackManager: TrackManager by injectLazy()

    abstract fun handleResult(data: Uri?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = ProgressBar(this)
        setContentView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        handleResult(intent.data)
    }

    /**
     * Runs [block] (the tracker's `login` call) and always returns to settings afterward,
     * regardless of outcome. Centralized here so a failing/throwing login can't crash the
     * activity if a subclass forgets to handle it itself.
     */
    internal fun login(block: suspend () -> Unit) {
        lifecycleScope.launchIO {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e)
            }
            returnToSettings()
        }
    }

    internal fun returnToSettings() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finishAfterTransition()
    }
}
