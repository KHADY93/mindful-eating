package io.github.khady93.mindfuleating

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import io.github.khady93.mindfuleating.history.HistoryRepository
import io.github.khady93.mindfuleating.settings.SettingsRepository
import io.github.khady93.mindfuleating.theme.MindfulEatingTheme
import io.github.khady93.mindfuleating.timer.TimerViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val ambientModeManager = rememberAmbientModeManager()
            CompositionLocalProvider(LocalAmbientModeManager provides ambientModeManager) {
                MindfulEatingTheme {
                    val timerViewModel: TimerViewModel = viewModel()
                    val settingsRepository = remember { SettingsRepository(applicationContext) }
                    val historyRepository = remember { HistoryRepository(applicationContext) }
                    WatchAppNavigation(
                        timerViewModel = timerViewModel,
                        settingsRepository = settingsRepository,
                        historyRepository = historyRepository,
                    )
                }
            }
        }
    }
}
