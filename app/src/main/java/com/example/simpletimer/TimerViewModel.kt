package com.example.simpletimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {

    private var timerService: TimerService? = null

    // --- Mutable State Flows for UI state ---
    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _minutes = MutableStateFlow("2")
    val minutes: StateFlow<String> = _minutes.asStateFlow()

    private val _seconds = MutableStateFlow("0")
    val seconds: StateFlow<String> = _seconds.asStateFlow()

    fun setTimerService(service: TimerService) {
        timerService = service
        // --- Sync ViewModel state with Service state after binding ---
        viewModelScope.launch {
            service.remainingTime.collect { _remainingTime.value = it }
        }
        viewModelScope.launch {
            service.isRunning.collect { _isRunning.value = it }
        }
        viewModelScope.launch {
            service.minutes.collect { _minutes.value = it }
        }
        viewModelScope.launch {
            service.seconds.collect { _seconds.value = it }
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