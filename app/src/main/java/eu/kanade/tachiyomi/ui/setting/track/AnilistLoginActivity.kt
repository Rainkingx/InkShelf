package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri

class AnilistLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(data: Uri?) {
        val regex = "(?:access_token=)(.*?)(?:&)".toRegex()
        val matchResult = regex.find(data?.fragment.toString())
        if (matchResult?.groups?.get(1) != null) {
            login { trackManager.aniList.login(matchResult.groups[1]!!.value) }
        } else {
            trackManager.aniList.logout()
            returnToSettings()
        }
    }
}
