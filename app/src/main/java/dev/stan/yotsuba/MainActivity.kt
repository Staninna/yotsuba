package dev.stan.yotsuba

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.stan.yotsuba.core.designsystem.theme.YotsubaTheme
import dev.stan.yotsuba.core.widget.WidgetDeepLink
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.navigation.AppNavHost
import dev.stan.yotsuba.navigation.ShellViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val shell: ShellViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // Runs the one-time legacy media migration as soon as storage access exists.
        viewModel.onResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WidgetDeepLink.consume(intent)
        shell.onIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Widget tap: park the (board, threadNo) extras for navigation to pick up.
        WidgetDeepLink.consume(intent)
        // Browser link or shared URL: parked until the nav graph is up.
        if (savedInstanceState == null) shell.onIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            YotsubaTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                AppNavHost(shell = shell)
            }
        }
    }
}
