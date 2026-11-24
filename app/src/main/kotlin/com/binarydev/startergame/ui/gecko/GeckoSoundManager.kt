package com.binarydev.startergame.ui.gecko

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Generator audio PCM prosedural + umpan balik haptik ultra-ringan:
 * - Pre-cache buffer PCM kompak (16 kHz, total < 24 KB RAM) sehingga tidak alokasi array setiap langkah.
 * - Playback diserialisasi tanpa `Thread.sleep` pemblokir dan dibatasi throttle untuk drag cepat.
 * - Mendukung `trimMemory()` & `release()` agar aman di perangkat RAM kecil.
 */
class GeckoSoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackMutex = Mutex()
    private val sampleRate = 16_000

    private val cachedClips = ConcurrentHashMap<SoundClip, ShortArray>()

    @Volatile
    private var activePlaybackJob: Job? = null

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var lastStepSoundElapsedMs: Long = 0L

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    var vibrationEnabled: Boolean = true

    fun playStepPop() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastStepSoundElapsedMs < STEP_THROTTLE_MS) return
        lastStepSoundElapsedMs = now

        if (soundEnabled) {
            playClip(SoundClip.STEP_POP)
        }
        vibrateMs(8L, 42)
    }

    fun playBlockedThud() {
        if (soundEnabled) {
            playClip(SoundClip.BLOCKED_THUD)
        }
        vibrateMs(24L, 100)
    }

    fun playEscapeArpeggio() {
        if (soundEnabled) {
            playClip(SoundClip.ESCAPE_ARPEGGIO)
        }
        vibrateMs(30L, 130)
    }

    fun playVictoryFanfare() {
        if (soundEnabled) {
            playClip(SoundClip.VICTORY_FANFARE)
        }
        vibrateMs(48L, 155)
    }

    fun playUiClick() {
        if (soundEnabled) {
            playClip(SoundClip.UI_CLICK)
        }
        vibrateMs(6L, 32)
    }

    /**
     * Releases cached PCM buffers under Android low-memory pressure (`onTrimMemory`).
     */
    fun trimMemory() {
        cachedClips.clear()
        releaseActiveTrackSafely()
    }

    /**
     * Releases native AudioTrack resources and cancels background coroutines on Activity destroy.
     */
    fun release() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
        releaseActiveTrackSafely()
        cachedClips.clear()
        scope.cancel()
    }

    private enum class SoundClip(val tones: List<ToneSpec>) {
        STEP_POP(
            listOf(ToneSpec(freqHz = 520.0, durationMs = 34, gain = 0.24f, endFreqHz = 660.0))
        ),
        BLOCKED_THUD(
            listOf(ToneSpec(freqHz = 185.0, durationMs = 70, gain = 0.26f, endFreqHz = 120.0))
        ),
        ESCAPE_ARPEGGIO(
            listOf(
                ToneSpec(523.25, 50, 0.24f),
                ToneSpec(587.33, 50, 0.25f),
                ToneSpec(659.25, 55, 0.26f),
                ToneSpec(783.99, 65, 0.28f),
                ToneSpec(1046.50, 110, 0.30f)
            )
        ),
        VICTORY_FANFARE(
            listOf(
                ToneSpec(523.25, 85, 0.26f),
                ToneSpec(659.25, 85, 0.28f),
                ToneSpec(783.99, 95, 0.30f),
                ToneSpec(1046.50, 200, 0.32f)
            )
        ),
        UI_CLICK(
            listOf(ToneSpec(freqHz = 440.0, durationMs = 24, gain = 0.18f, endFreqHz = 510.0))
        )
    }

    private data class ToneSpec(
        val freqHz: Double,
        val durationMs: Int,
        val gain: Float,
        val endFreqHz: Double = freqHz
    )

    private fun getOrSynthesizeClip(clip: SoundClip): ShortArray {
        return cachedClips.getOrPut(clip) {
            val tones = clip.tones
            val totalSamples = tones.sumOf { (sampleRate * it.durationMs) / 1000 }
            if (totalSamples <= 0) return@getOrPut ShortArray(0)
            val buffer = ShortArray(totalSamples)
            var offset = 0
            for (tone in tones) {
                val count = (sampleRate * tone.durationMs) / 1000
                var phase = 0.0
                for (i in 0 until count) {
                    val t = i.toDouble() / count.coerceAtLeast(1)
                    val freq = tone.freqHz + (tone.endFreqHz - tone.freqHz) * t
                    phase += 2.0 * PI * freq / sampleRate
                    val attack = (i / (sampleRate * 0.004)).coerceIn(0.0, 1.0)
                    val decay = exp(-4.5 * t)
                    val sample = (sin(phase) * tone.gain * attack * decay * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer[offset + i] = sample.toShort()
                }
                offset += count
            }
            buffer
        }
    }

    private fun playClip(clip: SoundClip) {
        activePlaybackJob?.cancel()
        activePlaybackJob = scope.launch {
            playbackMutex.withLock {
                runCatching {
                    val buffer = getOrSynthesizeClip(clip)
                    if (buffer.isEmpty()) return@runCatching

                    releaseActiveTrackSafely()

                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(buffer.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()

                    activeTrack = track
                    track.write(buffer, 0, buffer.size)
                    track.play()

                    val durationMs = clip.tones.sumOf { it.durationMs } + 45L
                    delay(durationMs)
                    releaseActiveTrackSafely()
                }
            }
        }
    }

    private fun releaseActiveTrackSafely() {
        val track = activeTrack
        activeTrack = null
        if (track != null) {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            }
            runCatching { track.release() }
        }
    }

    private fun vibrateMs(durationMs: Long, amplitude: Int) {
        if (!vibrationEnabled) return
        runCatching {
            val vib = vibrator ?: return@runCatching
            if (vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            }
        }
    }

    companion object {
        private const val STEP_THROTTLE_MS = 55L
    }
}
