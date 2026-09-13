package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri

class MyAnimeListLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            login { trackManager.myAnimeList.login(code) }
        } else {
            trackManager.myAnimeList.logout()
            returnToSettings()
        }
    }
}
