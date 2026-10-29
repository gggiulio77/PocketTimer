package com.gonzalogiulio.pockettimer

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausePlayGestureDetectorTest {

    private val detector = PausePlayGestureDetector(pausePlayThresholdMs = 1000L)

    @Test
    fun quickPauseThenPlayTriggersAfterPlaybackWasObserved() {
        detector.onMetadataChanged("track-1")
        detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 500L)
        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L))
        assertTrue(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1500L))
    }

    @Test
    fun intermediateBufferingAndMetadataDoNotCancelGesture() {
        detector.onMetadataChanged("track-1")
        detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 500L)
        detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L)
        detector.onMetadataChanged("track-1")
        detector.onPlaybackStateChanged(PlaybackState.STATE_BUFFERING, 1200L)
        detector.onMetadataChanged(null)

        assertTrue(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1800L))
    }

    @Test
    fun playWithoutPauseDoesNotTrigger() {
        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1000L))
    }

    @Test
    fun firstPlayAfterInitialPausedStateDoesNotTrigger() {
        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L))
        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1500L))

        detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 2000L)
        assertTrue(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 2500L))
    }

    @Test
    fun pauseInAnotherSessionDoesNotArmNewSession() {
        val soundCloud = PausePlayGestureDetector(pausePlayThresholdMs = 1000L)
        val spotify = PausePlayGestureDetector(pausePlayThresholdMs = 1000L)
        soundCloud.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 500L)
        soundCloud.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L)

        spotify.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1100L)
        assertFalse(spotify.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1200L))
    }

    @Test
    fun slowPauseThenPlayDoesNotTrigger() {
        detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 500L)
        detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L)

        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 2000L))
    }

    @Test
    fun confirmedTrackChangeCancelsGesture() {
        detector.onMetadataChanged("track-1")
        detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 500L)
        detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L)
        detector.onMetadataChanged("track-2")

        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1500L))
    }

    @Test
    fun skippingStateCancelsGesture() {
        detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 500L)
        detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L)
        detector.onPlaybackStateChanged(PlaybackState.STATE_SKIPPING_TO_NEXT, 1200L)

        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1500L))
    }

    @Test
    fun duplicatePlayingStateDoesNotTriggerTwice() {
        detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 500L)
        detector.onPlaybackStateChanged(PlaybackState.STATE_PAUSED, 1000L)

        assertTrue(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1500L))
        assertFalse(detector.onPlaybackStateChanged(PlaybackState.STATE_PLAYING, 1600L))
    }
}
