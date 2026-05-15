package com.dhanuk.lofiga.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Visualizer
import com.dhanuk.lofiga.model.PresetValues
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioEngine(private val context: Context) {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var waveformSeq = 0L
    private val _waveformData = MutableStateFlow(WaveformSnapshot(List(32) { 0f }, 0L))
    val waveformData: StateFlow<WaveformSnapshot> = _waveformData.asStateFlow()

    private val _fftData = MutableStateFlow(List(32) { 0f })
    val fftData: StateFlow<List<Float>> = _fftData.asStateFlow()

    data class WaveformSnapshot(val data: List<Float>, val seq: Long)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var mainPlayer: MediaPlayer? = null
    private val atmospherePlayers = mutableMapOf<String, MediaPlayer>()
    private val atmosphereVolumes = mutableMapOf<String, Float>()

    private var reverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var visualizer: android.media.audiofx.Visualizer? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null
    private var wasPlayingBeforeFocusLoss = false

    private val audioFocusRequest = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent focus loss - pause playback
                wasPlayingBeforeFocusLoss = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary focus loss (e.g., phone call) - pause and remember state
                wasPlayingBeforeFocusLoss = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck the volume temporarily
                mainPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained
                mainPlayer?.setVolume(1.0f, 1.0f)
                if (wasPlayingBeforeFocusLoss) {
                    play()
                }
            }
        }
    }

    companion object {
        val ATMOSPHERE_KEYS = listOf("rain", "vinyl", "wind", "tape")
        val ATMOSPHERE_FILES = mapOf(
            "rain" to "atmosphere/rain_loop.wav",
            "vinyl" to "atmosphere/vinyl_crackle.wav",
            "wind" to "atmosphere/wind_blow.wav",
            "tape" to "atmosphere/tape_hiss.wav"
        )
        val ATMOSPHERE_LABELS = mapOf(
            "rain" to "Rain",
            "vinyl" to "Vinyl",
            "wind" to "Wind",
            "tape" to "Tape"
        )
        // Higher priority for audio effects to ensure they aren't dropped
        private const val EFFECT_PRIORITY = 1
    }

    suspend fun init() {
        withContext(Dispatchers.IO) {
            ATMOSPHERE_KEYS.forEach { key ->
                createAtmospherePlayer(key)
            }
        }
    }

    // --- Main Track ---

    private fun requestAudioFocus(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.requestAudioFocus(
            audioFocusRequest,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocus(audioFocusRequest)
    }

    fun loadTrack(uri: Uri): Boolean {
        _isPlaying.value = false
        releaseMainPlayer()
        releaseEffects()
        releaseVisualizer()
        _error.value = null
        _position.value = 0
        _duration.value = 0
        resetWaveformData()

        if (!requestAudioFocus()) {
            _error.value = "Cannot get audio focus"
            return false
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                prepare()
                _duration.value = duration.toLong()
                setOnCompletionListener {
                    if (!_isLooping.value) {
                        _isPlaying.value = false
                        positionJob?.cancel()
                        pauseAtmospheres()
                    } else {
                        start()
                    }
                }
            }
            mainPlayer = player
            initEffects(player.audioSessionId)
            initVisualizer(player.audioSessionId)
            return true
        } catch (e: Exception) {
            _error.value = "Failed to load track: ${e.message}"
            abandonAudioFocus()
            e.printStackTrace()
            return false
        }
    }

    fun loadTrackFromFile(filePath: String): Boolean {
        _isPlaying.value = false
        releaseMainPlayer()
        releaseEffects()
        releaseVisualizer()
        _error.value = null
        _position.value = 0
        _duration.value = 0
        resetWaveformData()

        if (!requestAudioFocus()) {
            _error.value = "Cannot get audio focus"
            return false
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                prepare()
                _duration.value = duration.toLong()
                setOnCompletionListener {
                    if (!_isLooping.value) {
                        _isPlaying.value = false
                        positionJob?.cancel()
                        pauseAtmospheres()
                    } else {
                        start()
                    }
                }
            }
            mainPlayer = player
            initEffects(player.audioSessionId)
            initVisualizer(player.audioSessionId)
            return true
        } catch (e: Exception) {
            _error.value = "Failed to load file: ${e.message}"
            _duration.value = 0
            abandonAudioFocus()
            e.printStackTrace()
            return false
        }
    }

    fun isReady(): Boolean = mainPlayer != null

    fun clearError() {
        _error.value = null
    }

    fun play() {
        val player = mainPlayer
        if (player == null) {
            _error.value = "No track loaded"
            return
        }
        try {
            if (!player.isPlaying) {
                val sessionId = player.audioSessionId
                if (sessionId > 0 && visualizer == null) {
                    initVisualizer(sessionId)
                }
                player.start()
                _isPlaying.value = true
                startPositionPolling()
                syncAtmospheres()
            }
        } catch (e: Exception) {
            _error.value = "Playback error: ${e.message}"
            e.printStackTrace()
        }
    }


    fun pause() {
        val player = mainPlayer
        if (player == null) return
        wasPlayingBeforeFocusLoss = false
        try {
            if (player.isPlaying) {
                player.pause()
            }
            _isPlaying.value = false
            positionJob?.cancel()
            pauseAtmospheres()
        } catch (e: Exception) {
            _error.value = "Pause error: ${e.message}"
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        val player = mainPlayer
        if (player == null) {
            _error.value = "No track loaded"
            return
        }
        try {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                positionJob?.cancel()
                pauseAtmospheres()
            } else {
                if (visualizer == null || !player.isPlaying) {
                    val sessionId = player.audioSessionId
                    if (sessionId > 0) {
                        initVisualizer(sessionId)
                    }
                }
                player.start()
                _isPlaying.value = true
                startPositionPolling()
                syncAtmospheres()
            }
        } catch (e: Exception) {
            _error.value = "Play/Pause error: ${e.message}"
            e.printStackTrace()
        }
    }

    fun seekTo(millis: Long) {
        try {
            mainPlayer?.seekTo(millis.toInt())
            _position.value = millis
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setLooping(loop: Boolean) {
        _isLooping.value = loop
        mainPlayer?.isLooping = loop
    }

    fun toggleLoop() {
        setLooping(!_isLooping.value)
    }

    fun stop() {
        _isPlaying.value = false
        wasPlayingBeforeFocusLoss = false
        pauseAtmospheres()
        mainPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}
        }
        _position.value = 0
        positionJob?.cancel()
        abandonAudioFocus()
    }

    fun release() {
        positionJob?.cancel()
        scope.cancel()
        releaseMainPlayer()
        releaseAtmospherePlayers()
        releaseEffects()
        releaseVisualizer()
    }

    // --- Speed & Pitch (INDEPENDENT) ---

    fun setSpeedAndPitch(tempo: Float, semitones: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mainPlayer?.let { player ->
                try {
                    val pitchFactor = if (semitones != 0f) {
                        Math.pow(2.0, (semitones / 12.0).toDouble()).toFloat()
                    } else 1f

                    val params = PlaybackParams()
                        .setSpeed(tempo.coerceIn(0.1f, 3.0f))
                        .setPitch(pitchFactor.coerceIn(0.1f, 3.0f))
                        .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
                    player.playbackParams = params
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // --- Audio Effects ---

    private fun initEffects(audioSessionId: Int) {
        try {
            reverb = PresetReverb(EFFECT_PRIORITY, audioSessionId).apply {
                enabled = true
                preset = PresetReverb.PRESET_SMALLROOM
            }

            bassBoost = BassBoost(EFFECT_PRIORITY, audioSessionId).apply {
                enabled = true
                setStrength(0.toShort())
            }

            equalizer = Equalizer(EFFECT_PRIORITY, audioSessionId).apply {
                enabled = true
                val bands = numberOfBands
                for (i in 0 until bands) {
                    setBandLevel(i.toShort(), 0.toShort())
                }
            }

            android.util.Log.i("AudioEngine", "Effects initialized on session $audioSessionId")
        } catch (e: Exception) {
            android.util.Log.w("AudioEngine", "Failed to init effects: ${e.message}")
        }
    }

    fun setReverb(wet: Float) {
        reverb?.let { r ->
            try {
                r.enabled = wet > 0.01f
                if (wet > 0.01f) {
                    r.preset = when {
                        wet < 0.25f -> PresetReverb.PRESET_SMALLROOM
                        wet < 0.5f -> PresetReverb.PRESET_MEDIUMROOM
                        wet < 0.75f -> PresetReverb.PRESET_LARGEHALL
                        else -> PresetReverb.PRESET_PLATE
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun setBassBoost(strength: Float) {
        bassBoost?.let {
            try {
                val s = (strength * 1000).toInt().coerceIn(0, 1000).toShort()
                it.setStrength(s)
                it.enabled = strength > 0.01f
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun setTrebleCut(cutoffFactor: Float) {
        equalizer?.let { eq ->
            try {
                if (cutoffFactor > 0.01f) {
                    val bands = eq.numberOfBands
                    for (i in 0 until bands) {
                        val freq = eq.getCenterFreq(i.toShort())
                        if (freq > 2_000_000) {
                            val gain = (-(cutoffFactor * 1500f)).toInt().coerceIn(-1500, 0).toShort()
                            eq.setBandLevel(i.toShort(), gain)
                        } else {
                            eq.setBandLevel(i.toShort(), 0.toShort())
                        }
                    }
                    eq.enabled = true
                } else {
                    val bands = eq.numberOfBands
                    for (i in 0 until bands) {
                        eq.setBandLevel(i.toShort(), 0.toShort())
                    }
                    eq.enabled = false
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun setDelay(wet: Float) {
        // No-op: delay is combined with reverb via setReverbAndDelay()
        // to prevent overriding the reverb setting.
    }

    /**
     * Combines both reverb and delay into a single reverb effect since
     * Android's AudioEffect framework only provides PresetReverb.
     */
    fun setReverbAndDelay(reverbWet: Float, delayWet: Float) {
        reverb?.let { r ->
            try {
                val combined = (reverbWet + delayWet * 1.0f).coerceIn(0f, 1f)
                r.enabled = combined > 0.01f
                if (combined > 0.01f) {
                    r.preset = when {
                        combined < 0.06f -> PresetReverb.PRESET_NONE
                        combined < 0.15f -> PresetReverb.PRESET_SMALLROOM
                        combined < 0.30f -> PresetReverb.PRESET_MEDIUMROOM
                        combined < 0.55f -> PresetReverb.PRESET_LARGEHALL
                        else -> PresetReverb.PRESET_PLATE
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- Visualizer (Real waveform data) ---

    private fun resetWaveformData() {
        _waveformData.value = WaveformSnapshot(List(32) { 0f }, ++waveformSeq)
        _fftData.value = List(32) { 0f }
    }

    private fun initVisualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            android.util.Log.w("AudioEngine", "Invalid audio session ID: $audioSessionId, cannot init visualizer")
            scheduleVisualizerRetry(audioSessionId)
            return
        }
        try {
            releaseVisualizer()
            val maxCapture = android.media.audiofx.Visualizer.getCaptureSizeRange()
            val captureSize = maxCapture[1].coerceAtMost(1024)
            val v = android.media.audiofx.Visualizer(audioSessionId)
            v.captureSize = captureSize
            val captureRate = android.media.audiofx.Visualizer.getMaxCaptureRate()
            v.setDataCaptureListener(
                object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: android.media.audiofx.Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                    }

                    override fun onFftDataCapture(
                        visualizer: android.media.audiofx.Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        val data = fft ?: return
                        if (data.size < 4) return
                        val magnitudeCount = data.size / 2
                        val magnitudes = FloatArray(magnitudeCount) { i ->
                            val real = data[i * 2].toFloat() / 128f
                            val imag = data[i * 2 + 1].toFloat() / 128f
                            kotlin.math.sqrt(real * real + imag * imag).coerceIn(0f, 1f)
                        }
                        val bands = 32
                        val floats = (0 until bands).map { i ->
                            val binStart = (magnitudeCount * i) / bands
                            val binEnd = (magnitudeCount * (i + 1)) / bands
                            var maxVal = 0f
                            for (j in binStart until binEnd.coerceAtMost(magnitudeCount)) {
                                if (magnitudes[j] > maxVal) maxVal = magnitudes[j]
                            }
                            maxVal
                        }
                        _fftData.value = floats
                        _waveformData.value = WaveformSnapshot(floats, ++waveformSeq)
                    }
                },
                captureRate,
                true,
                true
            )
            v.enabled = true
            visualizer = v
            android.util.Log.i("AudioEngine", "Visualizer initialized on session $audioSessionId")
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            android.util.Log.w("AudioEngine", "Visualizer unavailable: $errorMsg")
            scheduleVisualizerRetry(audioSessionId)
        }
    }

    private fun scheduleVisualizerRetry(audioSessionId: Int) {
        scope.launch {
            delay(500)
            if (mainPlayer != null && mainPlayer!!.audioSessionId > 0) {
                android.util.Log.i("AudioEngine", "Retrying visualizer init with session ${mainPlayer!!.audioSessionId}")
                initVisualizer(mainPlayer!!.audioSessionId)
            }
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.apply {
                enabled = false
                release()
            }
            visualizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Atmosphere System ---

    private fun createAtmospherePlayer(key: String) {
        releaseAtmospherePlayer(key)
        try {
            val assetPath = ATMOSPHERE_FILES[key] ?: return
            val fd = context.assets.openFd(assetPath)
            val player = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(0f, 0f)
                prepare()
            }
            fd.close()
            atmospherePlayers[key] = player
            atmosphereVolumes[key] = 0f
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to create atmosphere player '$key'", e)
        }
    }

    private fun releaseAtmospherePlayer(key: String) {
        atmospherePlayers.remove(key)?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
    }

    fun recreateAtmospherePlayers() {
        ATMOSPHERE_KEYS.forEach { key ->
            createAtmospherePlayer(key)
        }
    }

    fun setAtmosphereVolume(key: String, volume: Float) {
        atmosphereVolumes[key] = volume
        var player = atmospherePlayers[key]
        if (player == null) {
            createAtmospherePlayer(key)
            player = atmospherePlayers[key]
        }
        if (player == null) return
        val vol = volume.coerceIn(0f, 1f)
        player.setVolume(vol, vol)
        // Start atmosphere player whenever volume > 0, regardless of main track playing state
        // The atmosphere is an independent layer
        if (vol > 0.01f && !player.isPlaying) {
            try {
                player.seekTo(0)
                player.start()
            } catch (_: Exception) {}
        } else if (vol <= 0.01f && player.isPlaying) {
            player.pause()
        }
    }

    fun setAllAtmosphereVolumes(values: PresetValues) {
        setAtmosphereVolume("rain", values.rainVolume)
        setAtmosphereVolume("vinyl", values.vinylVolume)
        setAtmosphereVolume("wind", values.windVolume)
        setAtmosphereVolume("tape", values.tapeVolume)
    }

    private fun syncAtmospheres() {
        atmospherePlayers.forEach { (key, player) ->
            val vol = atmosphereVolumes[key] ?: 0f
            if (vol > 0.01f && !player.isPlaying) {
                try {
                    player.seekTo(0)
                    player.start()
                } catch (_: Exception) {}
            } else if (vol <= 0.01f && player.isPlaying) {
                player.pause()
            }
        }
    }

    private fun pauseAtmospheres() {
        atmospherePlayers.values.forEach {
            try {
                if (it.isPlaying) it.pause()
            } catch (_: Exception) {}
        }
    }

    // --- Position Polling ---

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                mainPlayer?.let { player ->
                    try {
                        val pos = player.currentPosition.toLong()
                        if (pos >= 0) {
                            _position.value = pos
                        }
                    } catch (_: Exception) {}
                }
                delay(250)
            }
        }
    }

    // --- Cleanup ---

    private fun releaseMainPlayer() {
        _isPlaying.value = false
        positionJob?.cancel()
        mainPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (_: Exception) {}
        }
        mainPlayer = null
        abandonAudioFocus()
    }

    /**
     * Ensure main audio session is established before initializing effects.
     */
    fun ensureAudioSessionReady(): Boolean {
        return mainPlayer != null && mainPlayer!!.audioSessionId > 0
    }

    private fun releaseAtmospherePlayers() {
        atmospherePlayers.values.forEach {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        atmospherePlayers.clear()
    }

    private fun releaseEffects() {
        reverb?.release()
        bassBoost?.release()
        equalizer?.release()
        reverb = null
        bassBoost = null
        equalizer = null
    }
}