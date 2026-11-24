package com.binarydev.startergame.ui.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundManagerTest {

    @Test
    fun wavTrackSynthesizerProducesValidRiffWaveBytesForAllTracks() {
        for (track in SoundManager.SoundTrack.entries) {
            val bytes = WavTrackSynthesizer.buildWavBytes(track)
            assertTrue("Track ${track.name} must contain WAV header and PCM data", bytes.size > 44)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        }
    }

    @Test
    fun soundManagerHandlesPreferencesAndLifecycleSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = SoundManager(context)

        assertTrue(manager.isMusicEnabled)
        assertTrue(manager.isSoundEffectsEnabled)

        manager.startBackgroundMusic()
        manager.playEatFood()
        manager.pauseBackgroundMusic()
        manager.onAppBackgrounded()
        manager.onAppForegrounded()
        manager.resumeBackgroundMusic()
        manager.playGameOver()
        manager.playWin()

        manager.isMusicEnabled = false
        manager.isSoundEffectsEnabled = false
        assertFalse(manager.isMusicEnabled)
        assertFalse(manager.isSoundEffectsEnabled)

        manager.trimMemory()
        manager.stopBackgroundMusic()
        manager.release()
    }
}
