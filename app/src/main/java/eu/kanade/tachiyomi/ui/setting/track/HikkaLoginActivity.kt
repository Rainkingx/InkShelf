package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri

class HikkaLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(data: Uri?) {
        val reference = data?.getQueryParameter("reference")
        if (reference != null) {
            login { trackManager.hikka.login(reference) }
        } else {
            trackManager.hikka.logout()
            returnToSettings()
        }
    }
}
