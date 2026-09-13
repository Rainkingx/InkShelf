package eu.kanade.tachiyomi.widget.preference

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackLoginDialog(
    @StringRes usernameLabelRes: Int? = null,
    bundle: Bundle? = null,
) : LoginDialogPreference(usernameLabelRes, bundle) {
    private val service = Injekt.get<TrackManager>().getService(args.getInt("key"))!!

    override var canLogout = true

    constructor(
        service: TrackService,
        @StringRes usernameLabelRes: Int?,
    ) :
        this(usernameLabelRes, Bundle().apply { putInt("key", service.id) })

    override fun setCredentialsOnView(view: View) =
        with(view) {
            val serviceName = context.getString(service.nameRes())
            binding.dialogTitle.text = context.getString(R.string.log_in_to_, serviceName)
            if (service is Kitsu) {
                binding.username.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            binding.username.setText(service.getUsername())
            binding.password.setText(service.getPassword())
        }

    override fun checkLogin() {
        v?.apply {
            binding.login.isEnabled = false
            binding.loginProgress.show()
            if (binding.username.text.isNullOrBlank() || binding.password.text.isNullOrBlank()) {
                errorResult()
                context.toast(R.string.username_must_not_be_blank)
                return
            }

            dialog?.setCancelable(false)
            dialog?.setCanceledOnTouchOutside(false)
            val user = binding.username.text.toString()
            val pass = binding.password.text.toString()
            scope.launch {
                try {
                    binding.login.text = activity!!.getText(R.string.logging_in)
                    withIOContext { service.login(user, pass) }
                    binding.loginProgress.isVisible = false
                    binding.loginProgress.hide()
                    dialog?.dismiss()
                    context.toast(R.string.successfully_logged_in)
                } catch (error: Exception) {
                    Timber.e(error)
                    errorResult()
                    error.message?.let { context.toast(it) }
                }
            }
        }
    }

    private fun errorResult() {
        v?.apply {
            dialog?.setCancelable(true)
            dialog?.setCanceledOnTouchOutside(true)
            binding.loginProgress.hide()
            binding.loginProgress.isVisible = false
            binding.login.isEnabled = true
            binding.login.text = activity!!.getText(R.string.log_in)
        }
    }

    override fun onDialogClosed() {
        super.onDialogClosed()
        (targetController as? Listener)?.trackLoginDialogClosed(service)
    }

    interface Listener {
        fun trackLoginDialogClosed(service: TrackService)
    }
}
