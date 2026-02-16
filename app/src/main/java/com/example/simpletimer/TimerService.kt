package com.example.simpletimer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
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

class TimerService : Service(), AudioManager.OnAudioFocusChangeListener {

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var uiUpdateJob: Job? = null

    // --- Sound & Alarm Components ---
    private lateinit var audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent
    private var focusRequest: AudioFocusRequest? = null // Use modern request

    // --- State Management ---
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

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // --- Initialize Components ---
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimpleTimer:TimerWakeLock")

        // Create a single, reusable PendingIntent for the alarm
        val intent = Intent(this, TimerService::class.java).apply { action = ACTION_PLAY_SOUND }
        alarmIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        resetToDefault()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            ACTION_START_TIMER -> {
                startTimer()
            }

            ACTION_PLAY_SOUND -> {
                _isRunning.value = false
                startForeground(NOTIFICATION_ID, createNotification("Timer Finished"))
                playSound()
            }
        }

        return START_NOT_STICKY
    }

    // --- Public API for the ViewModel ---
    fun onMinutesChanged(newMinutes: String) {
        val filtered = newMinutes.filter { it.isDigit() }
        _minutes.value = if ((filtered.toIntOrNull() ?: 0) > 59) "59" else filtered
        _isDirty.value = true
    }

    fun onSecondsChanged(newSeconds: String) {
        val filtered = newSeconds.filter { it.isDigit() }
        _seconds.value = if ((filtered.toIntOrNull() ?: 0) > 59) "59" else filtered
        _isDirty.value = true
    }

    fun setTimer() {
        val m = _minutes.value.toLongOrNull() ?: 0
        val s = _seconds.value.toLongOrNull() ?: 0
        _remainingTime.value = (m * 60 + s) * 1000
        _isDirty.value = false
    }

    fun startTimer() {
        // --- This is now a true "restart" function ---
        cancelTimerInternals()
        setTimer()

        if (_remainingTime.value <= 0) {
            _isRunning.value = false
            return
        }

        val triggerAtMillis = System.currentTimeMillis() + _remainingTime.value

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
        } catch (_: SecurityException) {
            _isRunning.value = false
            return
        }

        _isRunning.value = true
        startForeground(NOTIFICATION_ID, createNotification("Timer Running"))

        // --- Coroutine for UI updates ONLY ---
        uiUpdateJob = serviceScope.launch {
            while (_remainingTime.value > 1000) {
                delay(1000)
                _remainingTime.value -= 1000
                updateNotification("Timer Running")
            }
        }
    }

    private fun cancelTimerInternals() {
        alarmManager.cancel(alarmIntent)
        uiUpdateJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun resetToDefault() {
        cancelTimerInternals()
        _minutes.value = "2"
        _seconds.value = "0"
        setTimer()
        _isRunning.value = false
    }

    // --- Sound Playback ---
    private fun playSound() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        wakeLock?.acquire(10_000)

        val afd = resources.openRawResourceFd(R.raw.beep)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            setOnCompletionListener { finishAndReset() }
            setOnErrorListener { _, _, _ -> finishAndReset(); true }
            prepare()
            start()
        }

        afd.close()
    }

    private fun finishAndReset() {
        mediaPlayer?.release()
        mediaPlayer = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        setTimer() // Reset timer display to the configured time
        _isRunning.value = false // CRITICAL: Ensure the start button is re-enabled
    }

    override fun onAudioFocusChange(focusChange: Int) { /* No-op */ }

    // --- Notification ---
    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Timer Service Channel", NotificationManager.IMPORTANCE_HIGH)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText("Time: ${formatTime(remainingTime.value)}")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOnlyAlertOnce(true)
        .build()

    private fun updateNotification(title: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(title))
    }

    private fun formatTime(timeInMillis: Long): String {
        val totalSeconds = timeInMillis / 1000
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimerInternals()
        mediaPlayer?.release()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_SOUND = "com.example.simpletimer.PLAY_SOUND"
        const val ACTION_START_TIMER = "com.example.simpletimer.START_TIMER"
    }
}
