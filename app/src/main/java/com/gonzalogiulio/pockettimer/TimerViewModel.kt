package com.gonzalogiulio.pockettimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {

    private var timerService: TimerService? = null
    private var collectionJob: Job? = null

    // --- UI State ---
    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _minutes = MutableStateFlow("")
    val minutes: StateFlow<String> = _minutes.asStateFlow()

    private val _seconds = MutableStateFlow("")
    val seconds: StateFlow<String> = _seconds.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun setTimerService(service: TimerService) {
        if (timerService === service) return
        
        timerService = service
        collectionJob?.cancel()
        
        collectionJob = viewModelScope.launch {
            // Wait for the service to load its state from SharedPreferences
            service.isReady.first { it }

            // Once the service is ready, start collecting its state
            launch { service.remainingTime.collect { _remainingTime.value = it } }
            launch { service.isRunning.collect { _isRunning.value = it } }
            launch { service.minutes.collect { _minutes.value = it } }
            launch { service.seconds.collect { _seconds.value = it } }

            // Signal that the ViewModel is now ready and has the correct state
            _isReady.value = true
        }
    }

    // --- Timer Actions ---
    fun onMinutesChanged(newMinutes: String) {
        timerService?.onMinutesChanged(newMinutes)
    }

    fun onSecondsChanged(newSeconds: String) {
        timerService?.onSecondsChanged(newSeconds)
    }

    fun startTimer() {
        timerService?.startTimer()
    }

    fun resetTimer() {
        timerService?.resetTimer()
    }
}
