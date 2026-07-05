package io.github.khady93.mindfuleating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import io.github.khady93.mindfuleating.history.HistoryRepository
import io.github.khady93.mindfuleating.settings.SettingsRepository
import io.github.khady93.mindfuleating.theme.MindfulEatingTheme
import io.github.khady93.mindfuleating.timer.TimerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
