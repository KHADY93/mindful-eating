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
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import io.github.khady93.mindfuleating.history.HistoryRepository
import io.github.khady93.mindfuleating.history.SessionRecord
import io.github.khady93.mindfuleating.settings.DEFAULT_DURATION_SECONDS
import io.github.khady93.mindfuleating.settings.SettingsRepository
import io.github.khady93.mindfuleating.sync.HistorySync
import io.github.khady93.mindfuleating.sync.SettingsSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
@OptIn(FlowPreview::class)
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
        pullCurrentRemoteState()

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

        // Push local settings/history changes to the paired device via the Data Layer API.
        // debounce() lets both halves of a just-applied remote update settle locally before
        // deciding whether to push (otherwise a torn intermediate read of the two flows could
        // be mistaken for a new local change and echoed straight back out).
        lifecycleScope.launch {
            combine(settingsRepository.durationSecondsFlow, settingsRepository.accentColorFlow) { seconds, color ->
                seconds to color
            }.debounce(100).collect { (seconds, color) ->
                SettingsSync.pushIfChanged(this@TimerService, seconds, color)
            }
        }
        lifecycleScope.launch {
            combine(historyRepository.sessionsFlow, historyRepository.tombstonesFlow) { records, tombstones ->
                records to tombstones
            }.debounce(100).collect { (records, tombstones) ->
                HistorySync.pushIfChanged(this@TimerService, records, tombstones)
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

    /**
     * [DataLayerListenerService.onDataChanged] only fires for changes made *after* this app is
     * listening — a freshly-installed companion app never gets a retroactive callback for
     * whatever the other device already stored before it existed. This does a one-time pull of
     * whatever's currently on the Data Layer at startup so a new install catches up immediately
     * instead of waiting for the next unrelated change on the other device.
     */
    private fun pullCurrentRemoteState() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataItems = Tasks.await(Wearable.getDataClient(this@TimerService).dataItems)
                for (item in dataItems) {
                    when (item.uri.path) {
                        SettingsSync.PATH -> SettingsSync.onRemoteDataChanged(this@TimerService, item)
                        HistorySync.PATH -> HistorySync.onRemoteDataChanged(this@TimerService, item)
                    }
                }
                dataItems.release()
            } catch (e: Exception) {
                // Best-effort catch-up; live sync via DataLayerListenerService still works
                // going forward even if this initial pull fails.
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
        // Resolve the host app's own launcher Activity generically (rather than a hardcoded
        // MainActivity reference) since this Service is shared between the watch and phone apps.
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mindful Eating")
            .setContentText("Session in progress")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
