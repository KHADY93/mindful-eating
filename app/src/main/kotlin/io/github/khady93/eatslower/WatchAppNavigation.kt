package io.github.khady93.eatslower

import androidx.compose.runtime.Composable
import io.github.khady93.eatslower.history.HistoryRepository
import io.github.khady93.eatslower.history.HistoryScreen
import io.github.khady93.eatslower.settings.SettingsRepository
import io.github.khady93.eatslower.settings.SettingsScreen
import io.github.khady93.eatslower.timer.TimerScreen
import io.github.khady93.eatslower.timer.TimerViewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private const val ROUTE_TIMER = "timer"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"

@Composable
fun WatchAppNavigation(
    timerViewModel: TimerViewModel,
    settingsRepository: SettingsRepository,
    historyRepository: HistoryRepository,
) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = ROUTE_TIMER,
    ) {
        composable(ROUTE_TIMER) {
            TimerScreen(
                viewModel = timerViewModel,
                onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                repository = settingsRepository,
                onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(repository = historyRepository)
        }
    }
}
