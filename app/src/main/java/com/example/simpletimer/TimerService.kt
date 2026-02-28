package com.example.simpletimer

import android.app.AlarmManager
import android.app.Notification
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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.example.simpletimer.AppConstants.KEY_APP_RUNNING
import com.example.simpletimer.AppConstants.KEY_LAST_MINUTES
import com.example.simpletimer.AppConstants.KEY_LAST_SECONDS
import com.example.simpletimer.AppConstants.PREFS_NAME
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
    private var focusRequest: AudioFocusRequest? = null

    // --- State Management ---
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

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimpleTimer:TimerWakeLock")

        val intent = Intent(this, TimerService::class.java).apply { action = ACTION_PLAY_SOUND }
        alarmIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _minutes.value = prefs.getString(KEY_LAST_MINUTES, "2") ?: "2"
        _seconds.value = prefs.getString(KEY_LAST_SECONDS, "0") ?: "0"
        setTimer()
        _isReady.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                Log.d("TimerService", "ACTION_START_TIMER received")
                if (isRunning.value) {
                    resetTimer()
                } else {
                    startTimer()
                }
            }
            ACTION_PLAY_SOUND -> {
                Log.d("TimerService", "ACTION_PLAY_SOUND received")
                _isRunning.value = false
                uiUpdateJob?.cancel()
                uiUpdateJob = null
                
                // Immediately save the finish time to block the observer from auto-restarting
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
                    putLong(AppConstants.KEY_LAST_FINISH_TIME, System.currentTimeMillis())
                }

                _remainingTime.value = 0 // Ensure it shows 00:00
                startForeground(NOTIFICATION_ID, createNotification("Timer Finished"))
                playSound()
            }
            ACTION_CANCEL_TIMER -> {
                resetTimer()
            }
        }
        return START_NOT_STICKY
    }

    fun onMinutesChanged(newMinutes: String) {
        val filtered = newMinutes.filter { it.isDigit() }
        _minutes.value = if ((filtered.toIntOrNull() ?: 0) > 59) "59" else filtered
        setTimer()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LAST_MINUTES, _minutes.value) }
    }

    fun onSecondsChanged(newSeconds: String) {
        val filtered = newSeconds.filter { it.isDigit() }
        _seconds.value = if ((filtered.toIntOrNull() ?: 0) > 59) "59" else filtered
        setTimer()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LAST_SECONDS, _seconds.value) }
    }

    private fun setTimer() {
        val m = _minutes.value.toLongOrNull() ?: 0
        val s = _seconds.value.toLongOrNull() ?: 0
        _remainingTime.value = (m * 60 + s) * 1000
    }

    fun startTimer() {
        Log.d("TimerService", "Starting timer...")
        cancelTimerInternals()
        setTimer()

        if (_remainingTime.value <= 0) {
            _isRunning.value = false
            return
        }

        val triggerAtMillis = System.currentTimeMillis() + _remainingTime.value

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
            }
        } catch (_: SecurityException) {
            _isRunning.value = false
            return
        }

        _isRunning.value = true
        startForeground(NOTIFICATION_ID, createNotification("Timer Running"))

        uiUpdateJob = serviceScope.launch {
            while (_isRunning.value && _remainingTime.value > 0) {
                delay(1000)
                if (!_isRunning.value) break
                _remainingTime.value = (_remainingTime.value - 1000).coerceAtLeast(0)
                updateNotification("Timer Running")
            }
        }
    }

    private fun cancelTimerInternals() {
        alarmManager.cancel(alarmIntent)
        uiUpdateJob?.cancel()
        uiUpdateJob = null
        stopSound()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun resetTimer() {
        Log.d("TimerService", "Resetting timer")
        cancelTimerInternals()
        setTimer()
        _isRunning.value = false
    }

    private fun playSound() {
        stopSound() 

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        wakeLock?.acquire(10_000)

        try {
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
        } catch (e: Exception) {
            Log.e("TimerService", "Error playing sound", e)
            finishAndReset()
        }
    }

    private fun finishAndReset() {
        Log.d("TimerService", "finishAndReset called")
        if (_isRunning.value) {
            stopSound()
            return
        }

        stopSound()
        stopForeground(STOP_FOREGROUND_REMOVE)
        setTimer()
        _isRunning.value = false
    }

    private fun stopSound() {
        mediaPlayer?.apply {
            try {
                setOnCompletionListener(null)
                setOnErrorListener(null)
                if (isPlaying) stop()
                release()
            } catch (e: Exception) { }
        }
        mediaPlayer = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    override fun onAudioFocusChange(focusChange: Int) {}

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timer Service Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).apply { action = ACTION_CANCEL_TIMER },
            PendingIntent.FLAG_IMMUTABLE
        )
        val activityIntent = Intent(this, MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Time: ${formatTime(remainingTime.value)}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(activityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(title: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(title))
    }

    private fun formatTime(timeInMillis: Long): String {
        val totalSeconds = (timeInMillis.coerceAtLeast(0) + 999) / 1000
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_APP_RUNNING, false).apply()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimerInternals()
    }

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_SOUND = "com.example.simpletimer.PLAY_SOUND"
        const val ACTION_START_TIMER = "com.example.simpletimer.START_TIMER"
        const val ACTION_CANCEL_TIMER = "com.example.simpletimer.CANCEL_TIMER"
    }
}
