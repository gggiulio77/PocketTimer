package com.example.simpletimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi // Media3 is still unstable
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var timerService: TimerService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                timerService?.let {
                    if (it.isRunning.value) {
                        it.resetToDefault()
                    } else {
                        it.startTimer()
                    }
                }
            }
        }
    }

    // Create your player and media session in the onCreate lifecycle event
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {

                @Deprecated("Deprecated in Java")
                override fun onPlaybackResumption(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {

                    timerService?.let {
                        if (it.isRunning.value) {
                            it.resetToDefault()
                        } else {
                            it.startTimer()
                        }
                    }

                    // Return an empty media list — we are not actually resuming media
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(
                            emptyList(),
                            0,
                            0L
                        )
                    )
                }
            })
            .build()

        // Bind to TimerService
        val intent = Intent(this, TimerService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    // The user dismissed the notification. Stop the service
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!
        if (!player.playWhenReady) {
            player.release()
            stopSelf()
        }
    }

    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        unbindService(connection)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession
}
