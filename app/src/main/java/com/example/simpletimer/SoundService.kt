package com.example.simpletimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class SoundService : Service(), AudioManager.OnAudioFocusChangeListener {

    private lateinit var audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var focusRequest: AudioFocusRequest
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimpleTimer:SoundWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Playing sound")
            .setContentText("Timer finished")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(this)
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            wakeLock?.acquire(10 * 1000L /* 10 seconds */)
            startSound()
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startSound() {
        mediaPlayer = MediaPlayer.create(this, R.raw.beep).apply {
            setOnCompletionListener {
                audioManager.abandonAudioFocusRequest(focusRequest)
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                stopSelf()
            }
            start()
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        // Not handling any changes, as the sound is very short
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        mediaPlayer?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val name = "Sound Service Channel"
        val descriptionText = "Channel for Sound Service"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "SoundServiceChannel"
        const val NOTIFICATION_ID = 2
    }
}
