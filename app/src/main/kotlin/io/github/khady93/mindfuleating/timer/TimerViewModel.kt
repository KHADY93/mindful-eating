package io.github.khady93.mindfuleating.timer

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.khady93.mindfuleating.history.HistoryRepository
import io.github.khady93.mindfuleating.history.SessionRecord
import io.github.khady93.mindfuleating.settings.DEFAULT_DURATION_SECONDS
import io.github.khady93.mindfuleating.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TICK_INTERVAL_MILLIS = 64L

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val historyRepository = HistoryRepository(application)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    // Latest settings value, applied at the start of the next cycle so an in-flight
    // countdown is never disrupted by a mid-session settings change.
    private var latestDurationSeconds: Int = DEFAULT_DURATION_SECONDS

    private var tickJob: Job? = null
    private var targetElapsedRealtime: Long = 0L
    private var currentCycleDurationMillis: Long = DEFAULT_DURATION_SECONDS * 1000L
    private var remainingMillisAtPause: Long = 0L

    // Wall-clock time is recorded for display in history; elapsed-realtime is used to
    // compute duration since it's immune to clock adjustments. Both are set only when a
    // session truly begins (Idle -> Running), not on resume-from-pause.
    private var sessionStartWallMillis: Long = 0L
    private var sessionStartElapsedRealtime: Long = 0L

    init {
        viewModelScope.launch {
            settingsRepository.durationSecondsFlow.collect { seconds ->
                latestDurationSeconds = seconds
                _uiState.update { state ->
                    if (state.phase == TimerPhase.Idle) {
                        state.copy(durationSeconds = seconds, remainingSeconds = seconds)
                    } else {
                        state.copy(durationSeconds = seconds)
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.accentColorFlow.collect { color ->
                _uiState.update { it.copy(accentColor = color) }
            }
        }
    }

    fun startEating() {
        if (_uiState.value.phase == TimerPhase.Running) return
        if (_uiState.value.phase == TimerPhase.Idle) {
            sessionStartWallMillis = System.currentTimeMillis()
            sessionStartElapsedRealtime = SystemClock.elapsedRealtime()
        }
        currentCycleDurationMillis = latestDurationSeconds * 1000L
        targetElapsedRealtime = SystemClock.elapsedRealtime() + currentCycleDurationMillis
        _uiState.update { it.copy(phase = TimerPhase.Running) }
        launchTicker()
    }

    fun pause() {
        if (_uiState.value.phase != TimerPhase.Running) return
        tickJob?.cancel()
        remainingMillisAtPause = (targetElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        _uiState.update { it.copy(phase = TimerPhase.Paused) }
    }

    fun resume() {
        if (_uiState.value.phase != TimerPhase.Paused) return
        targetElapsedRealtime = SystemClock.elapsedRealtime() + remainingMillisAtPause
        _uiState.update { it.copy(phase = TimerPhase.Running) }
        launchTicker()
    }

    /** Ends the current eating session, recording it to history and resetting the bite counter. */
    fun stopSession() {
        tickJob?.cancel()
        val currentState = _uiState.value
        val biteCount = currentState.biteCount
        if (biteCount > 0) {
            val durationSeconds = (SystemClock.elapsedRealtime() - sessionStartElapsedRealtime) / 1000
            val record = SessionRecord(
                startTimeMillis = sessionStartWallMillis,
                biteCount = biteCount,
                durationSeconds = durationSeconds,
                accentColorArgb = currentState.accentColor.toArgb(),
            )
            viewModelScope.launch { historyRepository.addSession(record) }
        }
        _uiState.update {
            it.copy(
                phase = TimerPhase.Idle,
                progress = 1f,
                remainingSeconds = latestDurationSeconds,
                biteCount = 0,
                durationSeconds = latestDurationSeconds,
            )
        }
    }

    private fun launchTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val remainingMillis = targetElapsedRealtime - now
                if (remainingMillis <= 0) {
                    VibrationUtil.vibrateBiteAlert(getApplication())
                    currentCycleDurationMillis = latestDurationSeconds * 1000L
                    // Advance by a fixed duration to stay drift-free across cycles; if the
                    // process was stalled long enough to fall a full cycle behind, resync to
                    // "now" instead so we don't fire a burst of catch-up vibrations.
                    val nextTarget = targetElapsedRealtime + currentCycleDurationMillis
                    targetElapsedRealtime = if (nextTarget <= now) {
                        now + currentCycleDurationMillis
                    } else {
                        nextTarget
                    }
                    _uiState.update { state ->
                        state.copy(
                            progress = 1f,
                            remainingSeconds = latestDurationSeconds,
                            biteCount = state.biteCount + 1,
                            durationSeconds = latestDurationSeconds,
                        )
                    }
                } else {
                    val progress = (remainingMillis.toFloat() / currentCycleDurationMillis).coerceIn(0f, 1f)
                    val remainingSeconds = ((remainingMillis + 999) / 1000).toInt()
                    _uiState.update { it.copy(progress = progress, remainingSeconds = remainingSeconds) }
                }
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }
}
