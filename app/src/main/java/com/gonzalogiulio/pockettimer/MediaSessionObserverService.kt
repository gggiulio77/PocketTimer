package com.gonzalogiulio.pockettimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.gonzalogiulio.pockettimer.AppConstants.KEY_APP_RUNNING
import com.gonzalogiulio.pockettimer.AppConstants.KEY_LAST_FINISH_TIME
import com.gonzalogiulio.pockettimer.AppConstants.PREFS_NAME

class MediaSessionObserverService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaSessionObserver"
        private const val PAUSE_PLAY_THRESHOLD_MS = 1000L
        private const val SKIP_DEBOUNCE_MS = 1500L
        private const val FINISH_IGNORE_WINDOW_MS = 1000L
    }

    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName
    private val activeControllers = mutableMapOf<MediaController, MediaController.Callback>()
    private var lastSkipTriggerTime = 0L

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        componentName = ComponentName(this, this.javaClass)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
            updateControllers(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification access permission", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
    }

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateControllers(controllers)
        }

    private fun updateControllers(controllers: List<MediaController>?) {
        // CRITICAL: Ignore media sessions from OUR OWN APP to prevent feedback loops
        val filtered = controllers?.filter { it.packageName != packageName }
        val active = filtered?.toSet() ?: emptySet()

        val toRemove = activeControllers.keys.filterNot { it in active }
        toRemove.forEach {
            activeControllers[it]?.let { cb -> it.unregisterCallback(cb) }
            activeControllers.remove(it)
        }

        filtered?.forEach { controller ->
            if (!activeControllers.containsKey(controller)) {
                val callback = createCallback()
                controller.registerCallback(callback)
                activeControllers[controller] = callback
            }
        }
    }

    private fun createCallback(): MediaController.Callback {
        return object : MediaController.Callback() {
            private val gestureDetector = PausePlayGestureDetector(PAUSE_PLAY_THRESHOLD_MS)

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (state == null) return
                val now = SystemClock.elapsedRealtime()
                if (gestureDetector.onPlaybackStateChanged(state.state, now)) {
                    maybeTriggerSkip(now)
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                gestureDetector.onMetadataChanged(extractMediaId(metadata))
            }
        }
    }

    private fun maybeTriggerSkip(now: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APP_RUNNING, false)) return

        if (now - lastSkipTriggerTime < SKIP_DEBOUNCE_MS) return

        // Secondary safety check: ignore triggers if the timer recently finished
        val lastFinishTime = prefs.getLong(KEY_LAST_FINISH_TIME, 0L)
        if (System.currentTimeMillis() - lastFinishTime < FINISH_IGNORE_WINDOW_MS) {
            Log.d(TAG, "Ignoring skip trigger (recently finished)")
            return
        }

        lastSkipTriggerTime = now
        Log.d(TAG, "User skip inferred -> starting timer")
        startTimer()
    }

    private fun extractMediaId(metadata: MediaMetadata?): String? {
        return metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    }

    private fun startTimer() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER
        }
        startForegroundService(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopService(Intent(this, TimerService::class.java))
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_APP_RUNNING, false).apply()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeControllers.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else null
    }
}

internal class PausePlayGestureDetector(
    private val pausePlayThresholdMs: Long
) {
    private var pausedAtMs: Long? = null
    private var currentMediaId: String? = null
    private var hasObservedPlaying = false

    fun onPlaybackStateChanged(state: Int, nowMs: Long): Boolean = when (state) {
        PlaybackState.STATE_PAUSED -> {
            // A newly registered controller can initially report PAUSED. Only a
            // pause observed after playback in this same session can arm the gesture.
            pausedAtMs = if (hasObservedPlaying) nowMs else null
            false
        }
        PlaybackState.STATE_PLAYING -> {
            val pausedAt = pausedAtMs
            pausedAtMs = null
            val shouldTrigger = hasObservedPlaying && pausedAt != null &&
                nowMs - pausedAt in 0 until pausePlayThresholdMs
            hasObservedPlaying = true
            shouldTrigger
        }
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_ERROR -> {
            pausedAtMs = null
            hasObservedPlaying = false
            false
        }
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> {
            pausedAtMs = null
            false
        }
        else -> false
    }

    fun onMetadataChanged(mediaId: String?) {
        // A missing ID is not enough evidence of a track change. Some players
        // temporarily clear metadata while transitioning between playback states.
        if (mediaId == null) return
        if (currentMediaId != null && currentMediaId != mediaId) {
            pausedAtMs = null
        }
        currentMediaId = mediaId
    }
}
