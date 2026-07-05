package io.github.khady93.eatslower.timer

import androidx.compose.ui.graphics.Color
import io.github.khady93.eatslower.settings.DEFAULT_DURATION_SECONDS
import io.github.khady93.eatslower.theme.DefaultAccentColor

enum class TimerPhase { Idle, Running, Paused }

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.Idle,
    val progress: Float = 1f,
    val remainingSeconds: Int = DEFAULT_DURATION_SECONDS,
    val biteCount: Int = 0,
    val durationSeconds: Int = DEFAULT_DURATION_SECONDS,
    val accentColor: Color = DefaultAccentColor,
)
