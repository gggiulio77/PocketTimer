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

class MediaSessionObserverService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaSessionObserver"

        // How close to the end we consider a "natural ending"
        private const val END_THRESHOLD_MS = 15000L

        // Prevent duplicate triggers
        private const val SKIP_DEBOUNCE_MS = 1500L
    }

    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName

    private val activeControllers =
        mutableMapOf<MediaController, MediaController.Callback>()

    private var lastSkipTriggerTime = 0L

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        componentName = ComponentName(this, this.javaClass)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionListener,
                componentName
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

    private fun createCallback(controller: MediaController)
            : MediaController.Callback {

        return object : MediaController.Callback() {

            private var lastSnapshot: TrackSnapshot? = null

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (state == null) return

                val metadata = controller.metadata

                lastSnapshot = TrackSnapshot(
                    mediaId = extractMediaId(metadata),
                    duration = metadata?.getLong(
                        MediaMetadata.METADATA_KEY_DURATION
                    ) ?: 0L,
                    lastPosition = state.position,
                    lastState = state.state,
                    stateUpdateTime = state.lastPositionUpdateTime
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

                val now = SystemClock.elapsedRealtime()

                val realPosition = computeRealPosition(previous, now)

                val isNaturalEnd =
                    previous.duration > 0 &&
                            realPosition >=
                            previous.duration - END_THRESHOLD_MS

                if (!isNaturalEnd) {
                    maybeTriggerSkip(now)
                }

                // Update snapshot to new track
                lastSnapshot = TrackSnapshot(
                    mediaId = newMediaId,
                    duration = metadata?.getLong(
                        MediaMetadata.METADATA_KEY_DURATION
                    ) ?: 0L,
                    lastPosition = 0L,
                    lastState = PlaybackState.STATE_NONE,
                    stateUpdateTime = now
                )
            }
        }
    }

    private fun computeRealPosition(
        snapshot: TrackSnapshot,
        now: Long
    ): Long {

        return if (snapshot.lastState == PlaybackState.STATE_PLAYING) {
            val delta = now - snapshot.stateUpdateTime
            snapshot.lastPosition + delta
        } else {
            snapshot.lastPosition
        }
    }

    private fun maybeTriggerSkip(now: Long) {
        if (now - lastSkipTriggerTime < SKIP_DEBOUNCE_MS) {
            return
        }

        lastSkipTriggerTime = now
        Log.d(TAG, "User skip inferred → starting timer")
        startTimer()
    }

    private fun extractMediaId(metadata: MediaMetadata?): String? {
        return metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    }

    private fun startTimer() {
        val intent = Intent(
            this,
            TimerService::class.java
        ).apply {
            action = TimerService.ACTION_START_TIMER
        }
        startForegroundService(intent)
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
