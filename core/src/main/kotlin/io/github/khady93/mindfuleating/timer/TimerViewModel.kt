package io.github.khady93.mindfuleating.timer

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thin relay between the Compose UI and [TimerService], which owns the actual countdown/
 * vibration/session-recording logic as a foreground Service so it keeps running when the
 * screen is off or the app is backgrounded. Binding here is only for observing live state;
 * calling [Context.startForegroundService] is what keeps the service alive independent of
 * whether this binding survives (e.g. across screen-off or the app being swiped from Recents).
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var service: TimerService? = null
    private var stateCollectionJob: Job? = null
    private var pendingStart = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val boundService = (binder as TimerService.LocalBinder).getService()
            service = boundService
            stateCollectionJob = viewModelScope.launch {
                boundService.uiState.collect { _uiState.value = it }
            }
            if (pendingStart) {
                pendingStart = false
                boundService.startEating()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stateCollectionJob?.cancel()
            service = null
        }
    }

    init {
        val context = getApplication<Application>()
        context.bindService(Intent(context, TimerService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun startEating() {
        val context = getApplication<Application>()
        context.startForegroundService(Intent(context, TimerService::class.java))
        val boundService = service
        if (boundService != null) {
            boundService.startEating()
        } else {
            pendingStart = true
        }
    }

    fun pause() {
        service?.pause()
    }

    fun resume() {
        service?.resume()
    }

    fun stopSession() {
        service?.stopSession()
    }

    override fun onCleared() {
        getApplication<Application>().unbindService(connection)
        super.onCleared()
    }
}
