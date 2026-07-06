package io.github.khady93.mindfuleating.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.khady93.mindfuleating.MainActivity
import io.github.khady93.mindfuleating.R
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
private const val NOTIFICATION_ID = 1
private const val CHANNEL_ID = "eating_session"

/**
 * Owns the countdown/vibration/session-recording logic as a foreground Service rather than a
 * plain ViewModel-scoped coroutine. A ViewModel's `viewModelScope` gets suspended once the
 * screen turns fully off or the app is backgrounded (ambient mode alone only guarantees ~once/
 * minute updates, not a precise per-cycle vibration) — a foreground Service is what Wear OS
 * requires for reliable execution in that state.
 */
class TimerService : LifecycleService() {

    private val binder = LocalBinder()

    private val settingsRepository = SettingsRepository(this)
    private val historyRepository = HistoryRepository(this)

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

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        lifecycleScope.launch {
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
        lifecycleScope.launch {
            settingsRepository.accentColorFlow.collect { color ->
                _uiState.update { it.copy(accentColor = color) }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
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
        startForeground(NOTIFICATION_ID, buildNotification())
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
            lifecycleScope.launch { historyRepository.addSession(record) }
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
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun launchTicker() {
        tickJob?.cancel()
        tickJob = lifecycleScope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val remainingMillis = targetElapsedRealtime - now
                if (remainingMillis <= 0) {
                    VibrationUtil.vibrateBiteAlert(this@TimerService)
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Eating session",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Session in progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(contentIntent)
            .build()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        super.onDestroy()
    }
}
