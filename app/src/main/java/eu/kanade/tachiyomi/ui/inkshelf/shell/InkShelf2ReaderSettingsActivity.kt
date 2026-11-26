package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate

/** A focused settings destination above the active reader, without starting
 * the main shell, its startup work or either onboarding flow. */
class InkShelf2ReaderSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureActivityDelegate.setSecure(this)
        InkShelf2Themes.restore(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            InkShelf2Theme {
                Scaffold(
                    containerColor = InkShelf2Colors.Background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { padding ->
                    InkShelf2AdvancedSettingsScreen(
                        padding = padding,
                        onBack = { finish() },
                        onThemes = {},
                        onDownloads = {},
                        onBackup = {},
                        readerOnly = true,
                    )
                }
            }
        }
    }
}
