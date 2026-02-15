package com.example.simpletimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class TimerService : Service() {

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    // --- State and Logic ---
    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _minutes = MutableStateFlow("")
    val minutes: StateFlow<String> = _minutes.asStateFlow()

    private val _seconds = MutableStateFlow("")
    val seconds: StateFlow<String> = _seconds.asStateFlow()

    // --- Service Lifecycle ---
    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        resetToDefault()
    }

    // --- Public API for the ViewModel ---
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

    fun setTimerFromInputs() {
        val m = _minutes.value.toLongOrNull() ?: 0
        val s = _seconds.value.toLongOrNull() ?: 0
        _remainingTime.value = (m * 60 + s) * 1000
        _isDirty.value = false
    }

    fun startTimer() {
        if (_isRunning.value) {
            // If timer is running and a song changes, stop and reset to set time.
            stopAndResetToSetTime()
            return
        }

        // This is a fresh start, either from UI or for the first time.
        setTimerFromInputs()

        if (_remainingTime.value <= 0) return

        _isRunning.value = true
        startForeground(NOTIFICATION_ID, createNotification())

        timerJob = serviceScope.launch {
            while (_remainingTime.value > 0) {
                delay(1000)
                _remainingTime.value -= 1000
                updateNotification()
            }
            // Timer finished normally
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)

            val soundIntent = Intent(this@TimerService, SoundService::class.java)
            startService(soundIntent)
            setTimerFromInputs()
        }
    }

    fun stopAndResetToSetTime() {
        timerJob?.cancel()
        _isRunning.value = false
        setTimerFromInputs() // Resets timer to the value in the text fields
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun resetToDefault() {
        timerJob?.cancel()
        _isRunning.value = false
        _minutes.value = "2"
        _seconds.value = "0"
        _isDirty.value = false
        setTimerFromInputs()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // --- Notification ---
    private fun createNotificationChannel() {
        val name = "Timer Service Channel"
        val descriptionText = "Channel for Timer Foreground Service"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Timer Running")
        .setContentText("Time: ${formatTime(remainingTime.value)}")
        .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a real icon
        .setOnlyAlertOnce(true)
        .build()

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun formatTime(timeInMillis: Long): String {
        val totalSeconds = timeInMillis / 1000
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 1
    }
}