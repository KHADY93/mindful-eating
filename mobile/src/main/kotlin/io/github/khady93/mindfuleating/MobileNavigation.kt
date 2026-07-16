package io.github.khady93.mindfuleating

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.khady93.mindfuleating.history.HistoryRepository
import io.github.khady93.mindfuleating.history.HistoryScreen
import io.github.khady93.mindfuleating.settings.SettingsRepository
import io.github.khady93.mindfuleating.settings.SettingsScreen
import io.github.khady93.mindfuleating.timer.TimerScreen
import io.github.khady93.mindfuleating.timer.TimerViewModel

private const val ROUTE_TIMER = "timer"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"

@Composable
fun MobileNavigation(
    timerViewModel: TimerViewModel,
    settingsRepository: SettingsRepository,
    historyRepository: HistoryRepository,
) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_TIMER) {
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
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(
                repository = historyRepository,
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}
