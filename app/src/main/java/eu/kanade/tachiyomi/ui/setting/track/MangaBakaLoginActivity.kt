package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri

class MangaBakaLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(data: Uri?) {
        val state = data?.getQueryParameter("state")
        val code = data?.getQueryParameter("code")
        if (code != null && state != null && trackManager.mangaBaka.verifyOAuthState(state)) {
            login { trackManager.mangaBaka.login(code) }
        } else {
            trackManager.mangaBaka.logout()
            returnToSettings()
        }
    }
}
