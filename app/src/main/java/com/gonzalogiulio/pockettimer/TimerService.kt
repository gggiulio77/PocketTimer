package com.gonzalogiulio.pockettimer

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
import com.gonzalogiulio.pockettimer.AppConstants.KEY_APP_RUNNING
import com.gonzalogiulio.pockettimer.AppConstants.KEY_ARM_SOUND
import com.gonzalogiulio.pockettimer.AppConstants.KEY_END_SOUND
import com.gonzalogiulio.pockettimer.AppConstants.KEY_LAST_MINUTES
import com.gonzalogiulio.pockettimer.AppConstants.KEY_LAST_SECONDS
import com.gonzalogiulio.pockettimer.AppConstants.KEY_RESET_SOUND
import com.gonzalogiulio.pockettimer.AppConstants.PREFS_NAME
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
    private var endTimeMillis = 0L
    private var endHandled = false

    // --- Sound & Alarm Components ---
    private lateinit var audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var eventPlayer: MediaPlayer? = null
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

    private val _armSound = MutableStateFlow(SoundCatalog.BUILTIN)
    val armSound: StateFlow<String> = _armSound.asStateFlow()

    private val _endSound = MutableStateFlow(SoundCatalog.BUILTIN)
    val endSound: StateFlow<String> = _endSound.asStateFlow()

    private val _resetSound = MutableStateFlow(SoundCatalog.BUILTIN)
    val resetSound: StateFlow<String> = _resetSound.asStateFlow()

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
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketTimer:TimerWakeLock")

        val intent = Intent(this, TimerService::class.java).apply { action = ACTION_PLAY_SOUND }
        alarmIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _minutes.value = prefs.getString(KEY_LAST_MINUTES, "2") ?: "2"
        _seconds.value = prefs.getString(KEY_LAST_SECONDS, "0") ?: "0"
        _armSound.value = prefs.getString(KEY_ARM_SOUND, SoundCatalog.BUILTIN) ?: SoundCatalog.BUILTIN
        _endSound.value = prefs.getString(KEY_END_SOUND, SoundCatalog.BUILTIN) ?: SoundCatalog.BUILTIN
        _resetSound.value = prefs.getString(KEY_RESET_SOUND, SoundCatalog.BUILTIN) ?: SoundCatalog.BUILTIN
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
                handleTimerFinished()
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

    fun onArmSoundChanged(soundId: String) = onSoundChanged(_armSound, KEY_ARM_SOUND, soundId)

    fun onEndSoundChanged(soundId: String) = onSoundChanged(_endSound, KEY_END_SOUND, soundId)

    fun onResetSoundChanged(soundId: String) = onSoundChanged(_resetSound, KEY_RESET_SOUND, soundId)

    private fun onSoundChanged(flow: MutableStateFlow<String>, key: String, soundId: String) {
        flow.value = soundId
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(key, soundId) }
        playEventSound(soundId)
    }

    private fun setTimer() {
        val m = _minutes.value.toLongOrNull() ?: 0
        val s = _seconds.value.toLongOrNull() ?: 0
        _remainingTime.value = (m * 60 + s) * 1000
    }

    fun startTimer() {
        Log.d("TimerService", "Starting timer...")
        cancelTimerInternals()
        endHandled = false
        setTimer()

        if (_remainingTime.value <= 0) {
            endHandled = true
            _isRunning.value = false
            return
        }

        endTimeMillis = System.currentTimeMillis() + _remainingTime.value
        val triggerAtMillis = endTimeMillis

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
        playEventSound(_armSound.value)

        uiUpdateJob = serviceScope.launch {
            var lastNotificationText = formatTime(_remainingTime.value)
            while (_isRunning.value) {
                val remaining = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
                if (remaining <= 0) {
                    handleTimerFinished()
                    break
                }
                _remainingTime.value = remaining
                val text = formatTime(remaining)
                if (text != lastNotificationText) {
                    updateNotification("Timer Running")
                    lastNotificationText = text
                }
                delay((remaining - 1) % 1000 + 1)
            }
        }
    }

    private fun cancelTimerInternals() {
        endHandled = true
        alarmManager.cancel(alarmIntent)
        uiUpdateJob?.cancel()
        uiUpdateJob = null
        stopSound()
        stopEventSound()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun resetTimer() {
        Log.d("TimerService", "Resetting timer")
        cancelTimerInternals()
        setTimer()
        _isRunning.value = false
        playEventSound(_resetSound.value)
    }

    private fun handleTimerFinished() {
        if (endHandled) return
        endHandled = true

        alarmManager.cancel(alarmIntent)
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

    private fun playSound() {
        stopSound()

        val player = createPlayer(_endSound.value)
        if (player == null) {
            finishAndReset()
            return
        }

        wakeLock?.acquire(10_000)
        mediaPlayer = player
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener { finishAndReset() }
        player.setOnErrorListener { _, _, _ -> finishAndReset(); true }
        try {
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("TimerService", "Error preparing end sound", e)
            finishAndReset()
        }
    }

    private fun playEventSound(soundId: String) {
        stopEventSound()

        val player = createPlayer(soundId) ?: return

        wakeLock?.acquire(10_000)
        eventPlayer = player
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener { stopEventSound() }
        player.setOnErrorListener { _, _, _ -> stopEventSound(); true }
        try {
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("TimerService", "Error preparing event sound '$soundId'", e)
            stopEventSound()
        }
    }

    private fun createPlayer(soundId: String): MediaPlayer? {
        if (soundId == SoundCatalog.NONE) return null

        val afd = try {
            SoundCatalog.resolve(this, soundId)
        } catch (e: Exception) {
            Log.e("TimerService", "Error opening sound '$soundId'", e)
            return null
        } ?: return null

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        return try {
            MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error preparing sound '$soundId'", e)
            null
        } finally {
            try {
                afd.close()
            } catch (_: Exception) {
            }
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
                setOnPreparedListener(null)
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

    private fun stopEventSound() {
        val player = eventPlayer ?: return
        eventPlayer = null
        try {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            if (player.isPlaying) player.stop()
            player.release()
        } catch (e: Exception) {
        }
        if (wakeLock?.isHeld == true) wakeLock?.release()
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
            .setSmallIcon(R.drawable.ic_notification)
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
        const val ACTION_PLAY_SOUND = "com.gonzalogiulio.pockettimer.PLAY_SOUND"
        const val ACTION_START_TIMER = "com.gonzalogiulio.pockettimer.START_TIMER"
        const val ACTION_CANCEL_TIMER = "com.gonzalogiulio.pockettimer.CANCEL_TIMER"
    }
}
