package com.example.simpletimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {

    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    // This is true if the user has changed the inputs
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private val _minutes = MutableStateFlow("")
    val minutes: StateFlow<String> = _minutes

    private val _seconds = MutableStateFlow("")
    val seconds: StateFlow<String> = _seconds

    private var timerJob: Job? = null

    init {
        resetToDefault()
    }

    fun onMinutesChanged(newMinutes: String) {
        val filtered = newMinutes.filter { it.isDigit() }
        val value = filtered.toIntOrNull()
        if (value != null && value > 59) {
            _minutes.value = "59"
        } else {
            _minutes.value = filtered
        }
        _isDirty.value = true
    }

    fun onSecondsChanged(newSeconds: String) {
        val filtered = newSeconds.filter { it.isDigit() }
        val value = filtered.toIntOrNull()
        if (value != null && value > 59) {
            _seconds.value = "59"
        } else {
            _seconds.value = filtered
        }
        _isDirty.value = true
    }

    fun setTimer() {
        val m = _minutes.value.toLongOrNull() ?: 0
        val s = _seconds.value.toLongOrNull() ?: 0
        _remainingTime.value = (m * 60 + s) * 1000
        _isDirty.value = false // We've now set the new time
    }

    fun startTimer() {
        if (_isRunning.value || _remainingTime.value <= 0) return

        _isRunning.value = true
        timerJob = viewModelScope.launch {
            while (_remainingTime.value > 0) {
                delay(1000)
                _remainingTime.value -= 1000
            }
            // When timer finishes, reset to default
            resetToDefault()
        }
    }

    fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
    }

    // The Reset button will now just go back to the default state
    fun resetToDefault() {
        timerJob?.cancel()
        _isRunning.value = false
        _minutes.value = "2"
        _seconds.value = ""
        _remainingTime.value = 2 * 60 * 1000 // 2 minutes
        _isDirty.value = false
    }
}
