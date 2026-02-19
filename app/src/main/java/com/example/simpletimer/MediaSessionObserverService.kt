package com.example.simpletimer

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
import com.example.simpletimer.AppConstants.KEY_APP_RUNNING
import com.example.simpletimer.AppConstants.PREFS_NAME

class MediaSessionObserverService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaSessionObserver"

        // How close to the end we consider a "natural ending"
        private const val PAUSE_PLAY_THRESHOLD_MS = 1000L

        // Prevent duplicate triggers
        private const val SKIP_DEBOUNCE_MS = 1500L
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
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionListener, componentName
            )
            updateControllers(
                mediaSessionManager.getActiveSessions(componentName)
            )
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
        val active = controllers?.toSet() ?: emptySet()

        // Remove stale
        val toRemove = activeControllers.keys.filterNot { it in active }
        toRemove.forEach {
            activeControllers[it]?.let { cb -> it.unregisterCallback(cb) }
            activeControllers.remove(it)
        }

        // Add new
        controllers?.forEach { controller ->
            if (!activeControllers.containsKey(controller)) {
                val callback = createCallback(controller)
                controller.registerCallback(callback)
                activeControllers[controller] = callback
            }
        }
    }

    private fun createCallback(controller: MediaController): MediaController.Callback {

        return object : MediaController.Callback() {

            private var lastSnapshot: TrackSnapshot? = null

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (state == null) return

                val metadata = controller.metadata
                val now = SystemClock.elapsedRealtime()

                if (lastSnapshot?.lastState == PlaybackState.STATE_PAUSED && state.state == PlaybackState.STATE_PLAYING) {
                    val timeSincePause = now - (lastSnapshot?.stateUpdateTime ?: 0)
                    if (timeSincePause < PAUSE_PLAY_THRESHOLD_MS) {
                        maybeTriggerSkip(now)
                    }
                }

                lastSnapshot = TrackSnapshot(
                    mediaId = extractMediaId(metadata),
                    duration = metadata?.getLong(
                        MediaMetadata.METADATA_KEY_DURATION
                    ) ?: 0L,
                    lastPosition = state.position,
                    lastState = state.state,
                    stateUpdateTime = now
                )
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                val newMediaId = extractMediaId(metadata)
                val previous = lastSnapshot ?: run {
                    // First track — just initialize
                    lastSnapshot = TrackSnapshot(
                        mediaId = newMediaId,
                        duration = metadata?.getLong(
                            MediaMetadata.METADATA_KEY_DURATION
                        ) ?: 0L,
                        lastPosition = 0L,
                        lastState = PlaybackState.STATE_NONE,
                        stateUpdateTime = SystemClock.elapsedRealtime()
                    )
                    return
                }

                // Same track → ignore
                if (previous.mediaId == newMediaId) return

                // Update snapshot to new track
                lastSnapshot = TrackSnapshot(
                    mediaId = newMediaId,
                    duration = metadata?.getLong(
                        MediaMetadata.METADATA_KEY_DURATION
                    ) ?: 0L,
                    lastPosition = 0L,
                    lastState = PlaybackState.STATE_NONE,
                    stateUpdateTime = SystemClock.elapsedRealtime()
                )
            }
        }
    }

    private fun maybeTriggerSkip(now: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAppRunning = prefs.getBoolean(KEY_APP_RUNNING, false)

        if (!isAppRunning) return

        if (now - lastSkipTriggerTime < SKIP_DEBOUNCE_MS) {
            return
        }

        lastSkipTriggerTime = now
        Log.d(TAG, "User skip inferred → starting timer")
        startTimer()
    }

    private fun extractMediaId(metadata: MediaMetadata?): String? {
        return metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: metadata?.getString(
            MediaMetadata.METADATA_KEY_MEDIA_URI
        ) ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    }

    private fun startTimer() {
        val intent = Intent(
            this, TimerService::class.java
        ).apply {
            action = TimerService.ACTION_START_TIMER
        }
        startForegroundService(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Stop the timer service
        val timerIntent = Intent(this, TimerService::class.java)
        stopService(timerIntent)

        // Set the app running flag to false
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_APP_RUNNING, false).apply()

        // Stop this service
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeControllers.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        mediaSessionManager.removeOnActiveSessionsChangedListener(
            sessionListener
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
        } else null
    }
}

private data class TrackSnapshot(
    val mediaId: String?,
    val duration: Long,
    val lastPosition: Long,
    val lastState: Int,
    val stateUpdateTime: Long
)
