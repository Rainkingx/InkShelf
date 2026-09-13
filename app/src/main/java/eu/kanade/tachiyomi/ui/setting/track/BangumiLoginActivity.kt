package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri

class BangumiLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            login { trackManager.bangumi.login(code) }
        } else {
            trackManager.bangumi.logout()
            returnToSettings()
        }
    }
}
