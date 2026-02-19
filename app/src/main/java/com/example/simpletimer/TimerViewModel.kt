package com.example.simpletimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {

    private var timerService: TimerService? = null

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
        timerService = service
        viewModelScope.launch {
            // Wait for the service to load its state from SharedPreferences
            service.isReady.first { it }

            // Once the service is ready, start collecting its state
            launch { service.remainingTime.collect(_remainingTime) }
            launch { service.isRunning.collect(_isRunning) }
            launch { service.minutes.collect(_minutes) }
            launch { service.seconds.collect(_seconds) }

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
