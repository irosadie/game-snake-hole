package com.binarydev.startergame.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Abstraction for game audio playback triggered by ViewModels, keeping game logic
 * cleanly decoupled from Android's [MediaPlayer] for fast JVM unit testing.
 */
interface GameSoundController {
    var isMusicEnabled: Boolean
    var isSoundEffectsEnabled: Boolean

    fun startBackgroundMusic()
    fun pauseBackgroundMusic()
    fun resumeBackgroundMusic()
    fun stopBackgroundMusic()

    fun playEatFood()
    fun playGameOver()
    fun playWin()

    fun release()
}

/**
 * Safe no-op implementation used as the default in pure JVM tests and Compose previews.
 */
object NoOpSoundController : GameSoundController {
    override var isMusicEnabled: Boolean = true
    override var isSoundEffectsEnabled: Boolean = true

    override fun startBackgroundMusic() = Unit
    override fun pauseBackgroundMusic() = Unit
    override fun resumeBackgroundMusic() = Unit
    override fun stopBackgroundMusic() = Unit
    override fun playEatFood() = Unit
    override fun playGameOver() = Unit
    override fun playWin() = Unit
    override fun release() = Unit
}

/**
 * Manages looping background music and one-shot game sound effects (eat food, game over, win)
 * using Android's [MediaPlayer].
 *
 * Supports optional `@RawRes` audio files for every track and automatically falls back to
 * lightweight cached 16 kHz PCM WAV tracks synthesized on first use so audio plays reliably
 * out of the box without bloating APK size or RAM.
 */
class SoundManager(
    context: Context,
    @RawRes private val bgmResId: Int? = null,
    @RawRes private val eatFoodResId: Int? = null,
    @RawRes private val gameOverResId: Int? = null,
    @RawRes private val winResId: Int? = null,
    private val bgmVolume: Float = DEFAULT_BGM_VOLUME,
    private val sfxVolume: Float = DEFAULT_SFX_VOLUME,
    private val mediaPlayerFactory: () -> MediaPlayer = { MediaPlayer() }
) : GameSoundController {

    private val appContext = context.applicationContext
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bgmMutex = Mutex()

    private val activeSfxPlayers: MutableSet<MediaPlayer> =
        Collections.newSetFromMap(ConcurrentHashMap<MediaPlayer, Boolean>())
    private val cachedAudioFiles = ConcurrentHashMap<SoundTrack, File>()

    private var bgmPlayer: MediaPlayer? = null

    @Volatile
    private var bgmRequested: Boolean = false

    @Volatile
    private var bgmPausedForBackground: Boolean = false

    @Volatile
    private var released: Boolean = false

    private val musicAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val sfxAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Volatile
    private var _isMusicEnabled: Boolean = true
    override var isMusicEnabled: Boolean
        get() = _isMusicEnabled
        set(value) {
            if (_isMusicEnabled == value) return
            _isMusicEnabled = value
            if (!value) {
                audioScope.launch {
                    bgmMutex.withLock {
                        pauseBgmInternal()
                    }
                }
            } else if (bgmRequested && !bgmPausedForBackground && !released) {
                audioScope.launch {
                    bgmMutex.withLock {
                        startOrResumeBgmInternal(restartFromBeginning = false)
                    }
                }
            }
        }

    @Volatile
    private var _isSoundEffectsEnabled: Boolean = true
    override var isSoundEffectsEnabled: Boolean
        get() = _isSoundEffectsEnabled
        set(value) {
            _isSoundEffectsEnabled = value
            if (!value) {
                releaseAllSfxPlayers()
            }
        }

    override fun startBackgroundMusic() {
        if (released) return
        bgmRequested = true
        bgmPausedForBackground = false
        if (!_isMusicEnabled) return

        audioScope.launch {
            bgmMutex.withLock {
                startOrResumeBgmInternal(restartFromBeginning = true)
            }
        }
    }

    override fun pauseBackgroundMusic() {
        if (released) return
        audioScope.launch {
            bgmMutex.withLock {
                pauseBgmInternal()
            }
        }
    }

    override fun resumeBackgroundMusic() {
        if (released) return
        bgmRequested = true
        bgmPausedForBackground = false
        if (!_isMusicEnabled) return

        audioScope.launch {
            bgmMutex.withLock {
                startOrResumeBgmInternal(restartFromBeginning = false)
            }
        }
    }

    override fun stopBackgroundMusic() {
        bgmRequested = false
        bgmPausedForBackground = false
        audioScope.launch {
            bgmMutex.withLock {
                releaseBgmPlayerInternal()
            }
        }
    }

    override fun playEatFood() {
        playSoundEffect(SoundTrack.EAT_FOOD, eatFoodResId)
    }

    override fun playGameOver() {
        playSoundEffect(SoundTrack.GAME_OVER, gameOverResId)
    }

    override fun playWin() {
        playSoundEffect(SoundTrack.WIN, winResId)
    }

    /**
     * Pauses background music when the Activity moves to the background.
     */
    fun onAppBackgrounded() {
        if (released) return
        bgmPausedForBackground = true
        audioScope.launch {
            bgmMutex.withLock {
                pauseBgmInternal()
            }
        }
    }

    /**
     * Resumes background music when the Activity returns to the foreground if music was active.
     */
    fun onAppForegrounded() {
        if (released) return
        val wasPausedForBg = bgmPausedForBackground
        bgmPausedForBackground = false
        if (wasPausedForBg && bgmRequested && _isMusicEnabled) {
            audioScope.launch {
                bgmMutex.withLock {
                    startOrResumeBgmInternal(restartFromBeginning = false)
                }
            }
        }
    }

    /**
     * Frees active one-shot SFX players and idle/paused BGM MediaPlayer under memory pressure.
     */
    fun trimMemory() {
        releaseAllSfxPlayers()
        audioScope.launch {
            bgmMutex.withLock {
                val player = bgmPlayer
                val playing = runCatching { player?.isPlaying == true }.getOrDefault(false)
                if (!playing) {
                    releaseBgmPlayerInternal()
                }
            }
        }
    }

    override fun release() {
        if (released) return
        released = true
        bgmRequested = false
        bgmPausedForBackground = false
        releaseAllSfxPlayers()
        val playerToRelease = bgmPlayer
        bgmPlayer = null
        releaseMediaPlayerSafely(playerToRelease)
        audioScope.cancel()
    }

    private fun startOrResumeBgmInternal(restartFromBeginning: Boolean) {
        if (released || !_isMusicEnabled || !bgmRequested || bgmPausedForBackground) return

        val existing = bgmPlayer
        if (existing != null) {
            val started = runCatching {
                if (restartFromBeginning) {
                    existing.seekTo(0)
                }
                if (!existing.isPlaying) {
                    existing.start()
                }
                true
            }.getOrDefault(false)
            if (started) return
            releaseBgmPlayerInternal()
        }

        val created = createPreparedPlayer(
            track = SoundTrack.BGM,
            rawResId = bgmResId,
            attributes = musicAudioAttributes,
            looping = true,
            volume = bgmVolume
        ) ?: return

        if (released || !_isMusicEnabled || !bgmRequested || bgmPausedForBackground) {
            releaseMediaPlayerSafely(created)
            return
        }

        created.setOnErrorListener { mp, _, _ ->
            if (bgmPlayer === mp) {
                bgmPlayer = null
            }
            releaseMediaPlayerSafely(mp)
            true
        }

        bgmPlayer = created
        runCatching { created.start() }.onFailure {
            releaseBgmPlayerInternal()
        }
    }

    private fun pauseBgmInternal() {
        val player = bgmPlayer ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
            }
        }
    }

    private fun releaseBgmPlayerInternal() {
        val player = bgmPlayer
        bgmPlayer = null
        releaseMediaPlayerSafely(player)
    }

    private fun playSoundEffect(track: SoundTrack, @RawRes rawResId: Int?) {
        if (released || !_isSoundEffectsEnabled) return

        audioScope.launch {
            if (released || !_isSoundEffectsEnabled) return@launch

            // Cap simultaneous MediaPlayer SFX instances to keep native memory low on budget phones
            if (activeSfxPlayers.size >= MAX_CONCURRENT_SFX) {
                activeSfxPlayers.firstOrNull()?.let { oldest ->
                    if (activeSfxPlayers.remove(oldest)) {
                        releaseMediaPlayerSafely(oldest)
                    }
                }
            }

            val player = createPreparedPlayer(
                track = track,
                rawResId = rawResId,
                attributes = sfxAudioAttributes,
                looping = false,
                volume = sfxVolume
            ) ?: return@launch

            if (released || !_isSoundEffectsEnabled) {
                releaseMediaPlayerSafely(player)
                return@launch
            }

            activeSfxPlayers.add(player)
            player.setOnCompletionListener { completed ->
                activeSfxPlayers.remove(completed)
                releaseMediaPlayerSafely(completed)
            }
            player.setOnErrorListener { errored, _, _ ->
                activeSfxPlayers.remove(errored)
                releaseMediaPlayerSafely(errored)
                true
            }

            runCatching { player.start() }.onFailure {
                activeSfxPlayers.remove(player)
                releaseMediaPlayerSafely(player)
            }
        }
    }

    private fun createPreparedPlayer(
        track: SoundTrack,
        @RawRes rawResId: Int?,
        attributes: AudioAttributes,
        looping: Boolean,
        volume: Float
    ): MediaPlayer? {
        val player = runCatching { mediaPlayerFactory() }.getOrNull() ?: return null
        return runCatching {
            player.setAudioAttributes(attributes)
            if (rawResId != null) {
                appContext.resources.openRawResourceFd(rawResId).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            } else {
                val wavFile = getOrCreateTrackWavFile(track)
                player.setDataSource(wavFile.absolutePath)
            }
            player.isLooping = looping
            val clampedVol = volume.coerceIn(0f, 1f)
            player.setVolume(clampedVol, clampedVol)
            player.prepare()
            player
        }.onFailure {
            releaseMediaPlayerSafely(player)
        }.getOrNull()
    }

    private fun getOrCreateTrackWavFile(track: SoundTrack): File {
        cachedAudioFiles[track]?.takeIf { it.exists() && it.length() > WAV_HEADER_SIZE }?.let {
            return it
        }
        val audioDir = File(appContext.cacheDir, AUDIO_CACHE_DIR).apply { mkdirs() }
        val file = File(audioDir, track.fileName)
        if (!file.exists() || file.length() <= WAV_HEADER_SIZE) {
            val wavBytes = WavTrackSynthesizer.buildWavBytes(track)
            FileOutputStream(file).use { out ->
                out.write(wavBytes)
                out.flush()
            }
        }
        cachedAudioFiles[track] = file
        return file
    }

    private fun releaseAllSfxPlayers() {
        val snapshot = activeSfxPlayers.toList()
        activeSfxPlayers.clear()
        for (player in snapshot) {
            releaseMediaPlayerSafely(player)
        }
    }

    private fun releaseMediaPlayerSafely(player: MediaPlayer?) {
        if (player == null) return
        runCatching {
            if (player.isPlaying) {
                player.stop()
            }
        }
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    internal enum class SoundTrack(val fileName: String) {
        BGM("snake_bgm_v1.wav"),
        EAT_FOOD("snake_sfx_eat_v1.wav"),
        GAME_OVER("snake_sfx_gameover_v1.wav"),
        WIN("snake_sfx_win_v1.wav")
    }

    companion object {
        private const val AUDIO_CACHE_DIR = "startergame_audio"
        private const val WAV_HEADER_SIZE = 44L
        private const val MAX_CONCURRENT_SFX = 4
        private const val DEFAULT_BGM_VOLUME = 0.35f
        private const val DEFAULT_SFX_VOLUME = 0.85f
    }
}

/**
 * Generates compact 16 kHz, 16-bit mono RIFF/WAVE audio tracks for [MediaPlayer] playback.
 */
internal object WavTrackSynthesizer {

    private const val SAMPLE_RATE = 16_000

    private data class NoteStep(
        val freqHz: Double,
        val durationMs: Int,
        val gain: Float,
        val endFreqHz: Double = freqHz,
        val bassFreqHz: Double = 0.0
    )

    fun buildWavBytes(track: SoundManager.SoundTrack): ByteArray {
        val pcmSamples = when (track) {
            SoundManager.SoundTrack.BGM -> synthesizeBgmLoop()
            SoundManager.SoundTrack.EAT_FOOD -> synthesizeSequence(
                listOf(
                    NoteStep(freqHz = 880.0, endFreqHz = 987.77, durationMs = 55, gain = 0.36f),
                    NoteStep(freqHz = 1318.51, endFreqHz = 1396.91, durationMs = 95, gain = 0.42f)
                ),
                decayRate = 5.2
            )
            SoundManager.SoundTrack.GAME_OVER -> synthesizeSequence(
                listOf(
                    NoteStep(freqHz = 392.00, endFreqHz = 369.99, durationMs = 115, gain = 0.36f),
                    NoteStep(freqHz = 329.63, endFreqHz = 311.13, durationMs = 120, gain = 0.35f),
                    NoteStep(freqHz = 261.63, endFreqHz = 246.94, durationMs = 130, gain = 0.34f),
                    NoteStep(freqHz = 196.00, endFreqHz = 164.81, durationMs = 220, gain = 0.38f)
                ),
                decayRate = 3.6
            )
            SoundManager.SoundTrack.WIN -> synthesizeSequence(
                listOf(
                    NoteStep(freqHz = 523.25, durationMs = 90, gain = 0.34f),
                    NoteStep(freqHz = 659.25, durationMs = 90, gain = 0.36f),
                    NoteStep(freqHz = 783.99, durationMs = 100, gain = 0.38f),
                    NoteStep(freqHz = 1046.50, durationMs = 220, gain = 0.42f)
                ),
                decayRate = 4.0
            )
        }
        return encodePcm16MonoWav(pcmSamples, SAMPLE_RATE)
    }

    /**
     * Synthesizes a seamless 16-beat (~4.8s) warm pentatonic marimba/arcade background loop.
     */
    private fun synthesizeBgmLoop(): ShortArray {
        val melody = doubleArrayOf(
            523.25, 659.25, 783.99, 659.25,
            587.33, 659.25, 880.00, 659.25,
            440.00, 523.25, 659.25, 523.25,
            392.00, 493.88, 587.33, 493.88
        )
        val bass = doubleArrayOf(
            130.81, 0.0, 196.00, 0.0,
            146.83, 0.0, 220.00, 0.0,
            110.00, 0.0, 164.81, 0.0,
            98.00, 0.0, 146.83, 0.0
        )
        val stepDurationMs = 300
        val notes = List(melody.size) { idx ->
            NoteStep(
                freqHz = melody[idx],
                durationMs = stepDurationMs,
                gain = 0.22f,
                bassFreqHz = bass[idx]
            )
        }
        return synthesizeSequence(notes, decayRate = 3.2)
    }

    private fun synthesizeSequence(notes: List<NoteStep>, decayRate: Double): ShortArray {
        val totalSamples = notes.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
        if (totalSamples <= 0) return ShortArray(0)

        val buffer = ShortArray(totalSamples)
        var offset = 0
        for (note in notes) {
            val count = (SAMPLE_RATE * note.durationMs) / 1000
            var phase = 0.0
            var harmonicPhase = 0.0
            var bassPhase = 0.0
            val attackSamples = (SAMPLE_RATE * 0.008).coerceAtLeast(1.0)
            val releaseSamples = (SAMPLE_RATE * 0.015).coerceAtLeast(1.0)

            for (i in 0 until count) {
                val t = i.toDouble() / count.coerceAtLeast(1)
                val freq = note.freqHz + (note.endFreqHz - note.freqHz) * t
                phase += 2.0 * PI * freq / SAMPLE_RATE
                harmonicPhase += 2.0 * PI * (freq * 2.0) / SAMPLE_RATE

                val attack = (i / attackSamples).coerceIn(0.0, 1.0)
                val release = ((count - 1 - i) / releaseSamples).coerceIn(0.0, 1.0)
                val env = attack * release * exp(-decayRate * t)

                var mixed = (sin(phase) * 0.80 + sin(harmonicPhase) * 0.20) * note.gain * env

                if (note.bassFreqHz > 0.0) {
                    bassPhase += 2.0 * PI * note.bassFreqHz / SAMPLE_RATE
                    val bassEnv = attack * release * exp(-2.4 * t)
                    mixed += sin(bassPhase) * 0.18 * bassEnv
                }

                val sampleInt = (mixed * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[offset + i] = sampleInt.toShort()
            }
            offset += count
        }
        return buffer
    }

    internal fun encodePcm16MonoWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 44 + dataSize
        val byteBuffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        byteBuffer.put('R'.code.toByte())
        byteBuffer.put('I'.code.toByte())
        byteBuffer.put('F'.code.toByte())
        byteBuffer.put('F'.code.toByte())
        byteBuffer.putInt(36 + dataSize)
        byteBuffer.put('W'.code.toByte())
        byteBuffer.put('A'.code.toByte())
        byteBuffer.put('V'.code.toByte())
        byteBuffer.put('E'.code.toByte())

        // fmt sub-chunk
        byteBuffer.put('f'.code.toByte())
        byteBuffer.put('m'.code.toByte())
        byteBuffer.put('t'.code.toByte())
        byteBuffer.put(' '.code.toByte())
        byteBuffer.putInt(16) // PCM subchunk size
        byteBuffer.putShort(1) // AudioFormat = PCM
        byteBuffer.putShort(1) // NumChannels = Mono
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * 2) // ByteRate
        byteBuffer.putShort(2) // BlockAlign
        byteBuffer.putShort(16) // BitsPerSample

        // data sub-chunk
        byteBuffer.put('d'.code.toByte())
        byteBuffer.put('a'.code.toByte())
        byteBuffer.put('t'.code.toByte())
        byteBuffer.put('a'.code.toByte())
        byteBuffer.putInt(dataSize)
        for (sample in samples) {
            byteBuffer.putShort(sample)
        }
        return byteBuffer.array()
    }
}
