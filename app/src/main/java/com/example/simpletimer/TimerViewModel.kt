package com.example.simpletimer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var timerService: TimerService? = null

    // These are now just mirrors of the service's state
    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _minutes = MutableStateFlow("")
    val minutes: StateFlow<String> = _minutes.asStateFlow()

    private val _seconds = MutableStateFlow("")
    val seconds: StateFlow<String> = _seconds.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            // Start collecting all states from the service
            viewModelScope.launch {
                timerService?.remainingTime?.collect { _remainingTime.value = it }
            }
            viewModelScope.launch {
                timerService?.isRunning?.collect { _isRunning.value = it }
            }
            viewModelScope.launch {
                timerService?.minutes?.collect { _minutes.value = it }
            }
            viewModelScope.launch {
                timerService?.seconds?.collect { _seconds.value = it }
            }
            viewModelScope.launch {
                timerService?.isDirty?.collect { _isDirty.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
        }
    }

    fun bindService(context: Context) {
        Intent(context, TimerService::class.java).also {
            context.bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        context.unbindService(connection)
    }

    // --- All public methods are now just simple pass-through calls to the service --- 

    fun onMinutesChanged(newMinutes: String) {
        timerService?.onMinutesChanged(newMinutes)
    }

    fun onSecondsChanged(newSeconds: String) {
        timerService?.onSecondsChanged(newSeconds)
    }

    fun setTimer() {
        timerService?.setTimerFromInputs()
    }

    fun startTimer() {
        timerService?.startTimer()
    }

    fun resetToDefault() {
        timerService?.stopAndResetToSetTime()
    }
}
