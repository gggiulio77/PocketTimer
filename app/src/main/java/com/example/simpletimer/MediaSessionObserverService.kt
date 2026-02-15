package com.example.simpletimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.util.Log

class MediaSessionObserverService : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private val activeMediaControllers = mutableMapOf<MediaController, MediaController.Callback>()
    private lateinit var componentName: ComponentName

    private var timerService: TimerService? = null
    private var isTimerServiceBound = false

    private val timerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            isTimerServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isTimerServiceBound = false
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        Log.d("MediaSessionObserver", "Active sessions changed. Found ${controllers?.size ?: 0} sessions.")
        updateMediaControllers(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        componentName = ComponentName(this, this.javaClass)
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, timerServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaSessionObserver", "Notification Listener connected. Your breakpoints should now be hit.")
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
            // Also process existing sessions
            updateMediaControllers(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.e("MediaSessionObserver", "Failed to add listener, missing permission?", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("MediaSessionObserver", "Notification Listener disconnected.")
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
    }

    private fun updateMediaControllers(controllers: List<MediaController>?) {
        val activeControllers = controllers?.toSet() ?: emptySet()
        val controllersToRemove = activeMediaControllers.keys.filterNot { it in activeControllers }
        controllersToRemove.forEach { controller ->
            Log.d("MediaSessionObserver", "Unregistering callback for ${controller.packageName}")
            val callback = activeMediaControllers.remove(controller)
            callback?.let { controller.unregisterCallback(it) }
        }

        controllers?.forEach { controller ->
            if (!activeMediaControllers.containsKey(controller)) {
                Log.d("MediaSessionObserver", "Registering callback for ${controller.packageName}")
                val callback = object : MediaController.Callback() {
                    private var lastMetadata: MediaMetadata? = null

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        super.onMetadataChanged(metadata)

                        val newTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        val newArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        val newAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

                        val lastTitle = lastMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        val lastArtist = lastMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        val lastAlbum = lastMetadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

                        // We check for lastMetadata not to be null, to avoid triggering the timer when switching apps.
                        if (lastMetadata != null && (newTitle != lastTitle || newArtist != lastArtist || newAlbum != lastAlbum)) {
                            Log.d("MediaSessionObserver", "Metadata changed for ${controller.packageName}. Starting timer.")
                            // Assuming startTimer() resets the timer if it's already running.
                            timerService?.startTimer()
                        }
                        lastMetadata = metadata
                    }
                }
                controller.registerCallback(callback)
                activeMediaControllers[controller] = callback
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activeMediaControllers.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        if (isTimerServiceBound) {
            unbindService(timerServiceConnection)
            isTimerServiceBound = false
        }
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
        } else {
            null
        }
    }
}