package eu.kanade.tachiyomi.crash

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CrashActivityBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat

class CrashActivity : BaseActivity<CrashActivityBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CrashActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.doOnApplyWindowInsetsCompat { v, insets, _ ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            binding.scrollView.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.buttonContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = bars.left
                rightMargin = bars.right
                bottomMargin = bars.bottom
            }
        }

        val stackTrace = GlobalExceptionHandler.getStackTraceFromIntent(intent)

        binding.crashSubtitle.text = getString(R.string.crash_screen_description, getString(R.string.app_name))
        binding.crashStackTrace.text = stackTrace.orEmpty()

        binding.btnShareCrashLogs.setOnClickListener {
            lifecycleScope.launchIO {
                CrashLogUtil(this@CrashActivity).shareLogs(stackTrace)
            }
        }
        binding.btnRestart.setOnClickListener {
            finishAffinity()
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}
