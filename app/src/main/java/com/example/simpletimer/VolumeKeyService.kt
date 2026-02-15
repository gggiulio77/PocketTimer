package com.example.simpletimer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VolumeKeyService : AccessibilityService() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + job)
    private var volumeUpJob: Job? = null
    private var volumeDownJob: Job? = null

    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimpleTimer::VolumeKeyWakeLock")
        wakeLock?.acquire()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (volumeUpJob?.isActive == true) {
                        // Second press, cancel single-press job and run double-press action
                        volumeUpJob?.cancel()
                        volumeUpJob = null
                        val intent = Intent(this, TimerService::class.java).apply {
                            action = TimerService.ACTION_START
                        }
                        startService(intent)
                    } else {
                        // First press, schedule single-press action
                        volumeDownJob?.cancel() // A different key was pressed, cancel its job
                        volumeUpJob = serviceScope.launch {
                            delay(400) // Wait for a potential second press
                            // If we're here, it was a single press
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        }
                    }
                    return true // Consume event to prevent immediate volume change
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (volumeDownJob?.isActive == true) {
                        // Second press
                        volumeDownJob?.cancel()
                        volumeDownJob = null
                        val intent = Intent(this, TimerService::class.java).apply {
                            action = TimerService.ACTION_RESET
                        }
                        startService(intent)
                    } else {
                        // First press
                        volumeUpJob?.cancel()
                        volumeDownJob = serviceScope.launch {
                            delay(400)
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        }
                    }
                    return true // Consume event
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

    override fun onInterrupt() {
        job.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        job.cancel()
    }
}
