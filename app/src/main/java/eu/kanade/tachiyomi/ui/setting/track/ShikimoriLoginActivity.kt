package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri

class ShikimoriLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            login { trackManager.shikimori.login(code) }
        } else {
            trackManager.shikimori.logout()
            returnToSettings()
        }
    }
}
